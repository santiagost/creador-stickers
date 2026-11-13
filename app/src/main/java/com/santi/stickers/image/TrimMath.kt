package com.santi.stickers.image

/** Tramo elegido de un video o GIF: arranca en [startMs] y dura [lengthMs]. */
data class ClipRange(val startMs: Long, val lengthMs: Long) {
    val endMs get() = startMs + lengthMs
}

/** Caja del tramo (spec 005, D1, RN-1 a RN-3). Sin Android, con tests en la PC. */
object TrimMath {
    const val MIN_MS = 2000L
    const val MAX_MS = 10_000L // límite de WhatsApp
    const val INITIAL_MS = 5000L
    const val BASE_FPS = 15
    const val MAX_FRAMES = 75 // D4

    /** D1, RN-2: 5 s desde el principio, o todo si dura menos. */
    fun initial(durationMs: Long): ClipRange = ClipRange(0, minOf(INITIAL_MS, durationMs))

    /** RN-1: mueve la caja entera sin salirse de la línea de tiempo. */
    fun move(range: ClipRange, deltaMs: Long, durationMs: Long): ClipRange =
        range.copy(startMs = (range.startMs + deltaMs).coerceIn(0, (durationMs - range.lengthMs).coerceAtLeast(0)))

    /** RN-1: el final queda fijo; el inicio se mueve dentro de 2 a 10 s de largo y sin pasar del principio. */
    fun resizeStart(range: ClipRange, deltaMs: Long, durationMs: Long): ClipRange {
        if (durationMs < MIN_MS) return range
        val end = range.endMs
        val lower = (end - MAX_MS).coerceAtLeast(0)
        val upper = (end - MIN_MS).coerceAtLeast(lower)
        val start = (range.startMs + deltaMs).coerceIn(lower, upper)
        return ClipRange(start, end - start)
    }

    /** RN-1: el inicio queda fijo; el final se mueve dentro de 2 a 10 s de largo y sin pasar del final. */
    fun resizeEnd(range: ClipRange, deltaMs: Long, durationMs: Long): ClipRange {
        if (durationMs < MIN_MS) return range
        val lower = range.startMs + MIN_MS
        val upper = minOf(durationMs, range.startMs + MAX_MS).coerceAtLeast(lower)
        val end = (range.endMs + deltaMs).coerceIn(lower, upper)
        return ClipRange(range.startMs, end - range.startMs)
    }

    /** RN-3, D4: 15 fps hasta 5 s; más largo, los fps que entran en 75 cuadros. */
    fun fpsFor(lengthMs: Long): Int =
        if (lengthMs <= MAX_FRAMES * 1000L / BASE_FPS) BASE_FPS else (MAX_FRAMES * 1000L / lengthMs).toInt().coerceAtLeast(1)

    /** Cuadros que va a tener el sticker de un tramo de [lengthMs]. */
    fun frameCount(lengthMs: Long): Int = (lengthMs * fpsFor(lengthMs) / 1000).toInt()
}
