package com.santi.stickers.x

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Qué video bajar de un tweet (spec 007, RN-3, RN-4, D4). */
sealed interface XVideoPick {
    /** [more]: el tweet tiene más videos o GIF que no se usan (D4). */
    data class Video(val url: String, val isGif: Boolean, val more: Boolean) : XVideoPick
    data object NoVideo : XVideoPick
    data object Unreadable : XVideoPick
}

/** Links de X y respuesta de los tweets incrustados (spec 007). Sin Android, con tests en la PC. */
object XLinks {
    const val MAX_SHORT_SIDE = 720 // D5

    /** RN-1: x.com / twitter.com (con www. o mobile.), /<usuario o i>/status/<número>. */
    private val STATUS = Regex(
        """https?://(?:www\.|mobile\.)?(?:x|twitter)\.com/(?:[A-Za-z0-9_]{1,15}|i)/status/(\d{1,25})(?![\d.A-Za-z])""",
        RegexOption.IGNORE_CASE,
    )

    /** Videos que se aceptan bajar (sección 8). */
    private val ALLOWED_VIDEO = Regex("""^https://video\.twimg\.com/[^\s]+$""")
    private val RESOLUTION = Regex("""/(\d+)x(\d+)/""")

    private val json = Json { ignoreUnknownKeys = true }

    fun statusId(text: String): String? = STATUS.find(text)?.groupValues?.get(1)

    fun syndicationUrl(id: String) = "https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=es&token=${syndicationToken(id)}"

    /**
     * Igual que el código de los tweets incrustados: `((id / 1e15) * Math.PI).toString(36).replace(/(0+|\.)/g, '')`.
     * Hoy X no lo controla (verificado 2026-11-17), pero se manda igual por si empieza a hacerlo.
     */
    fun syndicationToken(id: String): String = toStringRadix36(id.toDouble() / 1e15 * Math.PI).replace("0", "").replace(".", "")

    /**
     * `Number.prototype.toString(36)` de JavaScript para números positivos chicos, como lo hace V8
     * (DoubleToRadixCString): la parte decimal tiene los dígitos justos para identificar el número.
     */
    internal fun toStringRadix36(value: Double): String {
        require(value >= 0 && value < 1e15) { "fuera de rango: $value" }
        val radix = 36
        val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
        var integer = floor(value)
        var fraction = value - integer
        var delta = max(0.5 * (Math.nextUp(value) - value), Double.MIN_VALUE)
        val frac = StringBuilder()
        if (fraction >= delta) {
            do {
                fraction *= radix
                delta *= radix
                val digit = fraction.toInt()
                frac.append(chars[digit])
                fraction -= digit
                if (fraction > 0.5 || (fraction == 0.5 && (digit and 1) == 1)) {
                    if (fraction + delta > 1) {
                        // Redondeo hacia arriba, con acarreo.
                        while (true) {
                            if (frac.isEmpty()) {
                                integer += 1
                                break
                            }
                            val d = chars.indexOf(frac.last())
                            frac.setLength(frac.length - 1)
                            if (d + 1 < radix) {
                                frac.append(chars[d + 1])
                                break
                            }
                        }
                        break
                    }
                }
            } while (fraction >= delta)
        }
        val int = StringBuilder()
        do {
            val remainder = integer % radix
            int.append(chars[remainder.toInt()])
            integer = (integer - remainder) / radix
        } while (integer > 0)
        return int.reverse().toString() + if (frac.isEmpty()) "" else ".$frac"
    }

    /** RN-3, RN-4, D4: el primer video o GIF del tweet, en el mejor MP4 de hasta 720p del lado corto. */
    fun pickVideo(body: String): XVideoPick {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return XVideoPick.Unreadable
        val media = root["mediaDetails"] as? JsonArray
        if (media == null) {
            val isTweet = root["__typename"]?.jsonPrimitive?.content == "Tweet"
            return if (isTweet) XVideoPick.NoVideo else XVideoPick.Unreadable
        }
        val videos = media.mapNotNull { it as? JsonObject }.filter { type(it) == "video" || type(it) == "animated_gif" }
        val candidates = videos.mapNotNull { m -> bestVariant(m)?.let { url -> m to url } }
        val (first, url) = candidates.firstOrNull() ?: return XVideoPick.NoVideo
        return XVideoPick.Video(url, isGif = type(first) == "animated_gif", more = videos.size > 1)
    }

    private fun type(m: JsonObject) = runCatching { m["type"]?.jsonPrimitive?.content }.getOrNull()

    private data class Variant(val url: String, val bitrate: Long, val shortSide: Int?)

    private fun bestVariant(media: JsonObject): String? {
        val variants = ((media["video_info"] as? JsonObject)?.get("variants") as? JsonArray).orEmpty().mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val contentType = runCatching { o["content_type"]?.jsonPrimitive?.content }.getOrNull()
            val url = runCatching { o["url"]?.jsonPrimitive?.content }.getOrNull() ?: return@mapNotNull null
            if (contentType != "video/mp4" || !ALLOWED_VIDEO.matches(url)) return@mapNotNull null
            val bitrate = runCatching { o["bitrate"]?.jsonPrimitive?.longOrNull }.getOrNull() ?: 0
            val res = RESOLUTION.find(url)?.destructured?.let { (w, h) -> min(w.toInt(), h.toInt()) }
            Variant(url, bitrate, res)
        }
        if (variants.isEmpty()) return null
        // Sin resolución en la dirección (los GIF): se toma como que entra.
        val fits = variants.filter { (it.shortSide ?: 0) <= MAX_SHORT_SIDE }
        return if (fits.isNotEmpty()) fits.maxBy { it.bitrate }.url else variants.minBy { it.shortSide ?: 0 }.url
    }
}
