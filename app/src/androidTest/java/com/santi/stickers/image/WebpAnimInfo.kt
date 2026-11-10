package com.santi.stickers.image

/** Lee los chunks de un WebP animado (VP8X, ANIM, ANMF) para validar el archivo en los tests. */
data class WebpAnimInfo(val canvasWidth: Int, val canvasHeight: Int, val loopCount: Int?, val frameDurations: List<Int>) {
    val totalMs get() = frameDurations.sum()

    companion object {
        fun parse(bytes: ByteArray): WebpAnimInfo {
            require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") {
                "no es un WebP"
            }
            fun u16(p: Int) = (bytes[p].toInt() and 0xff) or ((bytes[p + 1].toInt() and 0xff) shl 8)
            fun u24(p: Int) = u16(p) or ((bytes[p + 2].toInt() and 0xff) shl 16)
            fun u32(p: Int) = u24(p) or ((bytes[p + 3].toInt() and 0xff) shl 24)
            var pos = 12
            var w = 0
            var h = 0
            var loop: Int? = null
            val durations = mutableListOf<Int>()
            while (pos + 8 <= bytes.size) {
                val tag = String(bytes, pos, 4, Charsets.US_ASCII)
                val size = u32(pos + 4)
                val data = pos + 8
                when (tag) {
                    "VP8X" -> {
                        w = u24(data + 4) + 1
                        h = u24(data + 7) + 1
                    }
                    "ANIM" -> loop = u16(data + 4)
                    "ANMF" -> durations += u24(data + 12)
                }
                pos = data + size + (size and 1)
            }
            return WebpAnimInfo(w, h, loop, durations)
        }
    }
}
