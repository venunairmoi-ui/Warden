package com.venunair.warden.capture

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * In-app photo capture via CameraX (Preview + ImageCapture use cases),
 * rather than launching the system Camera app through an
 * ACTION_IMAGE_CAPTURE intent -- keeps the user inside Warden with no
 * app-switch, and gives direct control over the output file instead of
 * depending on whatever the device's default camera app does with a
 * granted Uri.
 *
 * Captures straight to a File (AttachmentStorage.newCaptureFile), then
 * hands the caller a content:// Uri for that file via FileProvider -- so
 * this screen's result has the exact same shape (a Uri) as the gallery and
 * PDF pickers, letting AddEditItemScreen run all three sources through one
 * shared handler.
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder().build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                imageCapture = capture
            } catch (e: Exception) {
                // Broad catch deliberately: CameraX's bindToLifecycle can throw
                // several distinct exception types (no camera, already bound
                // wrong, illegal state) and every one of them means the same
                // thing to the user -- show the message, let them cancel out.
                cameraError = "Couldn't start the camera: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Surface(
            onClick = onCancel,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
            shape = CircleShape,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cancel",
                tint = Color.White,
                modifier = Modifier.padding(8.dp)
            )
        }

        cameraError?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            if (isCapturing) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
            } else {
                IconButton(
                    enabled = imageCapture != null,
                    onClick = {
                        val capture = imageCapture ?: return@IconButton
                        isCapturing = true
                        val outputFile = AttachmentStorage.newCaptureFile(context)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    onCaptured(AttachmentStorage.uriForFile(context, outputFile))
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    cameraError = "Capture failed: ${exception.message}"
                                }
                            }
                        )
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    ) {}
                }
            }
        }
    }
}
