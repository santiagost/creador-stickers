package com.santi.stickers.x

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

sealed interface XDownload {
    /** [file] está en la caché; quien lo recibe lo borra al terminar (D6). */
    data class Ok(val file: File, val isGif: Boolean, val more: Boolean) : XDownload
    data object NoConnection : XDownload
    /** Borrado, privado, protegido o una respuesta que no se entiende (RN-6). */
    data object Unreadable : XDownload
    data object NoVideo : XDownload
    data object TooBig : XDownload
}

/** Baja el video de un tweet (spec 007, D1, D5, D6, RN-6). Solo habla con X: ver [XLinks]. */
object XDownloader {
    const val MAX_BYTES = 50L * 1024 * 1024 // D5
    private const val PREFIX = "x-"
    private const val TIMEOUT_MS = 15_000
    private const val TAG = "XDownloader"

    suspend fun download(
        context: Context,
        statusId: String,
        maxBytes: Long = MAX_BYTES,
        onProgress: (Float) -> Unit = {},
    ): XDownload = withContext(Dispatchers.IO) {
        val pick = try {
            val (code, body) = get(XLinks.syndicationUrl(statusId))
            Log.i(TAG, "tweet $statusId: HTTP $code")
            if (code != 200) return@withContext XDownload.Unreadable
            XLinks.pickVideo(body)
        } catch (e: IOException) {
            Log.w(TAG, "sin conexión", e)
            return@withContext XDownload.NoConnection
        }
        val video = when (pick) {
            is XVideoPick.Video -> pick
            XVideoPick.NoVideo -> return@withContext XDownload.NoVideo
            XVideoPick.Unreadable -> return@withContext XDownload.Unreadable
        }
        val file = File(context.cacheDir, "$PREFIX$statusId-${System.nanoTime()}.mp4")
        try {
            when (val r = save(video.url, file, maxBytes, onProgress)) {
                null -> {
                    // Los "GIF" de X no se pueden leer en Android sin esto (ver Mp4Fix).
                    if (Mp4Fix.clearCttsFlags(file)) Log.i(TAG, "se arregló la caja ctts")
                    XDownload.Ok(file, video.isGif, video.more)
                }
                else -> {
                    file.delete()
                    r
                }
            }
        } catch (e: Throwable) {
            file.delete() // también si se canceló
            if (e is IOException) {
                Log.w(TAG, "no se pudo bajar ${video.url}", e)
                XDownload.NoConnection
            } else {
                throw e
            }
        }
    }

    /** D6: borra los videos que hayan quedado de otra vez (por ejemplo, si se cerró la app bajando). */
    fun clearCache(context: Context, olderThanMs: Long = 0) {
        val limit = System.currentTimeMillis() - olderThanMs
        context.cacheDir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".mp4") && f.lastModified() <= limit }
            ?.forEach { it.delete() }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = false // solo las direcciones que aceptamos (sección 8)
        setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
    }

    private fun get(url: String): Pair<Int, String> {
        val c = open(url)
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            return code to body
        } finally {
            c.disconnect()
        }
    }

    /** Devuelve null si se bajó bien; si no, el error. */
    private suspend fun save(url: String, file: File, maxBytes: Long, onProgress: (Float) -> Unit): XDownload? {
        val c = open(url)
        try {
            if (c.responseCode != 200) return XDownload.Unreadable
            val total = c.contentLengthLong
            if (total > maxBytes) return XDownload.TooBig // D5: se mira antes de bajar
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive() // "Cancelar" (AC-4)
                        val n = input.read(buffer)
                        if (n < 0) break
                        done += n
                        if (done > maxBytes) return XDownload.TooBig // por si no avisó el tamaño
                        out.write(buffer, 0, n)
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }
}
