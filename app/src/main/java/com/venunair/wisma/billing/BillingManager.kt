package com.venunair.wisma.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.venunair.wisma.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Scaffolding for the freemium "Premium unlock" purchase -- see
 * com.venunair.wisma.license.LicenseState for what it unlocks (OCR past
 * the 30-day trial, and Backup from day one) and the project memory
 * "warden-android-playstore-licensing" for why it's a one-time
 * non-consumable purchase, not a subscription.
 *
 * NOT YET LIVE: [PREMIUM_UNLOCK_PRODUCT_ID] doesn't exist in Play Console
 * yet -- the developer account is still under identity verification, and
 * an in-app product can't be created before that clears. Until then,
 * [queryPremiumProductDetails] always returns null (Play returns an empty
 * product list for an unknown id, not an error), and every call site here
 * already treats that as "nothing to sell yet", not a failure. Once the
 * real product is created with this exact id in Play Console -> Monetize
 * -> Products -> In-app products, this should work end-to-end with no
 * code changes.
 *
 * Constructed once in WardenApplication (manual DI, same as
 * SettingsRepository/ItemRepository there) and lives for the process --
 * [startConnection] is called from WardenApplication.onCreate;
 * [BillingClient.endConnection] is deliberately never called, matching
 * every other process-lifetime singleton in this app.
 */
class BillingManager(
    context: Context,
    private val settingsRepository: SettingsRepository
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        // This app sells exactly one non-consumable product, never a
        // subscription -- enableOneTimeProducts() is the (Billing 6+)
        // requirement for letting a one-time purchase go PENDING (e.g. a
        // delayed payment method) instead of being silently dropped.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // Billing 7+: the library retries a dropped connection itself --
        // no hand-rolled onBillingServiceDisconnected() retry loop needed.
        .enableAutoServiceReconnection()
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    // Reconciles this device's local premiumUnlocked flag
                    // against Play's own record -- the correct way to
                    // "restore purchases" after a reinstall, or on a
                    // second device signed into the same Google account,
                    // with no button or backend of this app's own
                    // involved.
                    scope.launch { restorePurchases() }
                } else {
                    Log.w(TAG, "onBillingSetupFinished: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "onBillingServiceDisconnected -- library will auto-retry (enableAutoServiceReconnection)")
            }
        })
    }

    /** Null if the product hasn't been created in Play Console yet, or the
     *  query itself failed (logged either way) -- callers should treat
     *  null as "nothing purchasable right now", not an error to surface
     *  loudly. */
    suspend fun queryPremiumProductDetails(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_UNLOCK_PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetailsAsync(params) { result, queryResult ->
                if (result.responseCode != BillingResponseCode.OK) {
                    Log.w(TAG, "queryProductDetailsAsync: ${result.responseCode} ${result.debugMessage}")
                }
                continuation.resume(queryResult.productDetailsList.firstOrNull())
            }
        }
    }

    /** Launches Play's own purchase UI. [activity] must be the current
     *  foreground Activity -- Play's checkout sheet attaches to it
     *  directly, the same reason DriveBackupManager's consent flow needs
     *  one for its IntentSenderRequest launcher. */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow: ${result.responseCode} ${result.debugMessage}")
        }
    }

    /** Play calls this for every purchase update -- a real-time purchase,
     *  one resumed from a PENDING state, or one made on another device
     *  syncing back. Every path funnels through [handlePurchase]. */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingResponseCode.OK && purchases != null) {
            scope.launch { purchases.forEach { handlePurchase(it) } }
        } else if (result.responseCode != BillingResponseCode.USER_CANCELED) {
            Log.w(TAG, "onPurchasesUpdated: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private suspend fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        val purchases = suspendCancellableCoroutine<List<Purchase>> { continuation ->
            billingClient.queryPurchasesAsync(params) { result, purchasesList ->
                if (result.responseCode != BillingResponseCode.OK) {
                    Log.w(TAG, "queryPurchasesAsync: ${result.responseCode} ${result.debugMessage}")
                }
                continuation.resume(purchasesList)
            }
        }
        // Deliberately one-directional: a found, PURCHASED premium
        // purchase unlocks Premium, but NOT finding one never re-locks it
        // locally. This app has no backend of its own to tell "the
        // purchase was refunded/revoked" apart from "this query had a
        // transient hiccup" -- flipping premiumUnlocked back to false on
        // the wrong one would be a much worse failure (silently re-locking
        // a paying user) than the reverse (this device just doesn't
        // proactively catch a refund it should have). A real refund still
        // takes effect correctly the moment Play itself enforces it.
        purchases.filter { it.products.contains(PREMIUM_UNLOCK_PRODUCT_ID) }
            .forEach { handlePurchase(it) }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_UNLOCK_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            // Play auto-refunds a purchase that's never acknowledged
            // within 3 days -- this must happen every time a fresh,
            // unacknowledged PURCHASED state comes through, not just once.
            val ackResult = suspendCancellableCoroutine<BillingResult> { continuation ->
                billingClient.acknowledgePurchase(ackParams) { continuation.resume(it) }
            }
            if (ackResult.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "acknowledgePurchase: ${ackResult.responseCode} ${ackResult.debugMessage}")
            }
        }
        settingsRepository.setPremiumUnlocked(true)
    }

    companion object {
        private const val TAG = "BillingManager"

        /** Must match the in-app product id created in Play Console ->
         *  Monetize -> Products -> In-app products exactly -- see this
         *  class's doc comment. Not yet created there. */
        const val PREMIUM_UNLOCK_PRODUCT_ID = "premium_unlock"
    }
}
