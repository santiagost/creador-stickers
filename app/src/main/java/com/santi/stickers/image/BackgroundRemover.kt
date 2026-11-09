package com.santi.stickers.image

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.resume

/** Spec 002, D6: quitar el fondo, detrás de una interfaz para poder cambiar de motor. */
interface BackgroundRemover : Closeable {
    /** Pesado (~300 ms): llamar desde una corrutina. */
    suspend fun remove(photo: Bitmap): RemovalResult
}

sealed interface RemovalResult {
    /** [subject] tiene el tamaño de la foto, con fondo transparente; [bounds] es la caja del sujeto. */
    data class Removed(val subject: Bitmap, val bounds: CropMath.Box) : RemovalResult

    /** RN-3: no hay nada que recortar. */
    data object NoSubject : RemovalResult

    /** RN-4: falló o no está disponible (sin Play services, modelo sin bajar). */
    data class Unavailable(val reason: String) : RemovalResult
}

/**
 * ML Kit Subject Segmentation (spec 002, D1). Usa el "foreground bitmap" tal cual (D8).
 * El modelo lo baja Play services (D7); si todavía no está, ML Kit falla y se devuelve [RemovalResult.Unavailable].
 */
class MlKitBackgroundRemover : BackgroundRemover {

    private val segmenter by lazy {
        SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
    }

    override suspend fun remove(photo: Bitmap): RemovalResult {
        val outcome = suspendCancellableCoroutine { cont ->
            segmenter.process(InputImage.fromBitmap(photo, 0))
                .addOnSuccessListener { cont.resume(Result.success(it.foregroundBitmap)) }
                .addOnFailureListener { cont.resume(Result.failure(it)) }
        }
        val subject = outcome.getOrElse {
            Log.w(TAG, "ML Kit no pudo quitar el fondo", it)
            return RemovalResult.Unavailable(it.message ?: it.javaClass.simpleName)
        } ?: return RemovalResult.NoSubject
        val bounds = withContext(Dispatchers.Default) { StickerImages.alphaBounds(subject) }
            ?: return RemovalResult.NoSubject
        return RemovalResult.Removed(subject, bounds)
    }

    override fun close() {
        segmenter.close()
    }

    private companion object {
        const val TAG = "BackgroundRemover"
    }
}
