package com.venunair.wisma.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.venunair.wisma.capture.AttachmentStorage
import com.venunair.wisma.data.WardenDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.google.api.services.drive.model.File as DriveFile

/**
 * Phase 2 (commercial-readiness plan, "Build commercial trust"): the app's
 * first and only network feature. One rolling backup per Google account
 * (whichever the user picks in the system account chooser each time this
 * runs), stored in that account's own hidden Drive "app data" folder --
 * invisible in the user's normal Drive UI, readable only by this app.
 * Not a version history: matches the same "keep it simple" scope the
 * rest of this app's data-management features (Delete all my data,
 * Archive) already follow.
 *
 * Built on the Authorization API (`com.google.android.gms.auth.api.
 * identity.AuthorizationClient`/[Identity]), NOT the older
 * `GoogleSignInClient`/`GoogleSignInAccount` classes -- those were
 * REMOVED from `play-services-auth` entirely as of the version this app
 * pulls (confirmed by inspecting the actual AAR's class list; an earlier
 * draft of this file was written against the old API from memory and
 * failed to compile with "unresolved reference: GoogleSignIn"). The
 * Authorization API is scope-focused rather than identity-focused: no
 * separate "sign in" step, no server/web OAuth client ID needed at all
 * for this app's shape (that's only required for
 * `requestOfflineAccess()`, which this app doesn't use, having no
 * backend) -- just the Android OAuth client (package name + signing
 * SHA-1, registered directly in Google Cloud Console, referenced nowhere
 * in this code). Calling [requestAuthorization] resolves silently
 * (no dialog) if access was already granted in a previous session, or
 * surfaces an [AuthorizationResult.getPendingIntent] to launch for
 * consent if not -- see the call sites in SettingsScreen/PrivacyScreen
 * for how that pending intent gets wired through an
 * `ActivityResultContracts.StartIntentSenderForResult` launcher.
 *
 * Scope requested is [DriveScopes.DRIVE_APPDATA], not the broader
 * `drive.file`/`drive` scopes -- this app can only ever see files IT
 * created, in a folder the user can't browse or accidentally delete from
 * Drive's own UI.
 */
object DriveBackupManager {
    private const val TAG = "DriveBackupManager"
    private const val BACKUP_FILE_NAME = "warden_backup.zip"
    private const val DB_ENTRY_NAME = "warden.db"
    private const val ATTACHMENTS_ENTRY_PREFIX = "attachments/"
    private const val PENDING_RESTORE_DIR_NAME = "pending_restore"

    // ── Authorization ────────────────────────────────────────────────

    /**
     * Requests Drive app-data access. Exactly one of [onAuthorized] /
     * [onResolutionRequired] / [onFailure] fires. [onResolutionRequired]
     * hands back an [IntentSenderRequest] ready to pass straight to an
     * `ActivityResultContracts.StartIntentSenderForResult` launcher --
     * the caller's launcher callback should then pass its result `Intent`
     * to [handleAuthorizationResolution].
     */
    fun requestAuthorization(
        context: Context,
        onAuthorized: (AuthorizationResult) -> Unit,
        onResolutionRequired: (IntentSenderRequest) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
            .build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                val pendingIntent = result.pendingIntent
                if (result.hasResolution() && pendingIntent != null) {
                    onResolutionRequired(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                    onAuthorized(result)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "requestAuthorization failed", e)
                onFailure(e)
            }
    }

    /** Call from the `StartIntentSenderForResult` launcher's result
     *  callback, passing its `ActivityResult.data`. */
    fun handleAuthorizationResolution(context: Context, data: Intent?): Result<AuthorizationResult> = runCatching {
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
    }.onFailure { e -> Log.e(TAG, "handleAuthorizationResolution failed", e) }

    private fun driveService(accessToken: String): Drive {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName("Wisma")
            .build()
    }

    // ── Backup ────────────────────────────────────────────────────────

    suspend fun backupNow(
        context: Context,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Resolved via the existing WardenDatabase.getInstance(context)
            // singleton getter rather than threading a WardenDatabase
            // parameter through the whole nav graph down to this call site
            // -- safe to call from anywhere with a Context, same as every
            // other consumer of that singleton already does.
            val database = WardenDatabase.getInstance(context)
            // WAL mode (Room's default) can leave recent writes sitting in
            // -wal/-shm sidecar files, not yet folded into the main .db
            // file this backup actually reads -- checkpoint first so the
            // zip below is never missing the last few minutes of edits.
            database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }

            val zipFile = File(context.cacheDir, BACKUP_FILE_NAME)
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                addFileToZip(zos, context.getDatabasePath(DB_ENTRY_NAME), DB_ENTRY_NAME)
                AttachmentStorage.attachmentsDir(context).listFiles()?.forEach { file ->
                    if (file.isFile) addFileToZip(zos, file, ATTACHMENTS_ENTRY_PREFIX + file.name)
                }
            }

            val drive = driveService(accessToken)
            val existingId = findBackupFileId(drive)
            val content = FileContent("application/zip", zipFile)
            if (existingId != null) {
                drive.files().update(existingId, null, content).execute()
            } else {
                val metadata = DriveFile().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                drive.files().create(metadata, content).execute()
            }
            zipFile.delete()
            Unit
        }.onFailure { e -> Log.e(TAG, "backupNow failed", e) }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun findBackupFileId(drive: Drive): String? =
        drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_FILE_NAME' and trashed = false")
            .setFields("files(id)")
            .execute()
            .files
            .firstOrNull()
            ?.id

    // ── Restore ──────────────────────────────────────────────────────

    sealed interface RestoreOutcome {
        /** Downloaded and staged successfully -- caller must restart the
         *  app (see [restartApp]) before the restored data takes effect;
         *  see this file's class doc / [applyPendingRestoreIfAny] for why
         *  a live hot-swap of an open Room database isn't attempted. */
        data object StagedPendingRestart : RestoreOutcome
        data object NoBackupFound : RestoreOutcome
    }

    suspend fun restoreNow(context: Context, accessToken: String): Result<RestoreOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = driveService(accessToken)
                val fileId = findBackupFileId(drive) ?: return@runCatching RestoreOutcome.NoBackupFound

                val zipFile = File(context.cacheDir, "warden_restore.zip")
                zipFile.outputStream().use { out -> drive.files().get(fileId).executeMediaAndDownloadTo(out) }

                val stagingDir = File(context.filesDir, PENDING_RESTORE_DIR_NAME)
                stagingDir.deleteRecursively()
                stagingDir.mkdirs()
                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(stagingDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        if (!entry.isDirectory) {
                            outFile.outputStream().use { zis.copyTo(it) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                zipFile.delete()
                RestoreOutcome.StagedPendingRestart
            }.onFailure { e -> Log.e(TAG, "restoreNow failed", e) }
        }

    /**
     * Applies a staged restore, if one is pending -- MUST be called from
     * [com.venunair.wisma.WardenApplication.onCreate] before its
     * `database` lazy val (or anything that touches it) is ever accessed.
     * Swapping the live warden.db file out from under an already-open
     * Room connection is exactly the kind of thing that's easy to get
     * subtly wrong without a device to verify against, so [restoreNow]
     * above deliberately never touches the real database/attachments
     * paths directly -- it only stages files here, and this function does
     * the actual swap at the one moment it's safe: cold start, before
     * Room has opened anything.
     */
    fun applyPendingRestoreIfAny(context: Context) {
        val stagingDir = File(context.filesDir, PENDING_RESTORE_DIR_NAME)
        if (!stagingDir.exists()) return

        val stagedDb = File(stagingDir, DB_ENTRY_NAME)
        if (stagedDb.exists()) {
            val liveDb = context.getDatabasePath(DB_ENTRY_NAME)
            // Stale WAL/SHM sidecars from the database this restore is
            // REPLACING must not survive -- Room would otherwise try to
            // replay old WAL frames against the newly-restored main file
            // on next open, an old-data-over-new-data corruption risk.
            File(liveDb.path + "-wal").delete()
            File(liveDb.path + "-shm").delete()
            liveDb.parentFile?.mkdirs()
            stagedDb.copyTo(liveDb, overwrite = true)
        }

        val stagedAttachments = File(stagingDir, ATTACHMENTS_ENTRY_PREFIX.removeSuffix("/"))
        if (stagedAttachments.exists()) {
            val liveAttachments = AttachmentStorage.attachmentsDir(context)
            liveAttachments.deleteRecursively()
            stagedAttachments.copyRecursively(liveAttachments, overwrite = true)
        }

        stagingDir.deleteRecursively()
    }

    /** Restarts the app via a fresh task, so [applyPendingRestoreIfAny]
     *  runs against a Room database nothing has opened yet. Standard
     *  Android "restart my own app" pattern -- makeRestartActivityTask
     *  clears the back stack, and killing this process after starting the
     *  new task (not before) avoids a window where no activity is running
     *  at all. */
    fun restartApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
        context.startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }
}
