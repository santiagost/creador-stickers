package com.santi.stickers.image

/** Línea de tiempo de un GIF (spec 005, RN-4). Sin Android, con tests en la PC. */
class GifTimeline(rawDelaysMs: List<Int>) {
    /** Duración de cada cuadro; 0 o menos se toma como [DEFAULT_DELAY_MS] (convención de los navegadores). */
    val delaysMs: List<Int> = rawDelaysMs.map { if (it <= 0) DEFAULT_DELAY_MS else it }
    val durationMs: Long = delaysMs.sumOf { it.toLong() }
    private val starts: LongArray = LongArray(delaysMs.size).also { s ->
        for (i in 1 until s.size) s[i] = s[i - 1] + delaysMs[i - 1]
    }

    val frameCount get() = delaysMs.size

    /** Índice del cuadro que se ve en [timeMs] (fuera de la línea de tiempo, el primero o el último). */
    fun indexAt(timeMs: Long): Int {
        val i = starts.binarySearch(timeMs)
        return (if (i >= 0) i else -i - 2).coerceIn(0, delaysMs.size - 1)
    }

    companion object {
        const val DEFAULT_DELAY_MS = 100
    }
}
