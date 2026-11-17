package com.santi.stickers.x

import java.io.File
import java.io.RandomAccessFile

/**
 * Arreglo para los "GIF" de X (spec 007, verificado 2026-11-17 en el S20 FE): su caja `ctts` trae flags = 1, y el
 * lector de MP4 de Android exige versión y flags en 0 (`SampleTable::setCompositionTimeToSampleParams` de AOSP)
 * o rechaza el archivo entero (no ve ninguna pista). Poner los flags en 0 no cambia los cuadros: los flags de
 * `ctts` no tienen significado en el estándar. Se cambian 3 bytes en el lugar, sin recodificar.
 * Sin Android, con tests en la PC.
 */
object Mp4Fix {
    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")

    /** Devuelve true si cambió algo. Nunca tira excepción por un archivo raro: si no entiende algo, no toca nada. */
    fun clearCttsFlags(file: File): Boolean = runCatching {
        RandomAccessFile(file, "rw").use { raf -> walk(raf, 0, raf.length(), depth = 0) }
    }.getOrDefault(false)

    private fun walk(raf: RandomAccessFile, start: Long, end: Long, depth: Int): Boolean {
        var changed = false
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            var size = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { raf.readFully(it) }.decodeToString()
            var header = 8L
            when (size) {
                0L -> size = end - pos // hasta el final
                1L -> {
                    size = raf.readLong()
                    header = 16
                }
            }
            if (size < header || pos + size > end) break
            when {
                type == "ctts" && depth > 0 && size >= header + 4 -> {
                    raf.seek(pos + header)
                    val version = raf.read()
                    val flags = ByteArray(3).also { raf.readFully(it) }
                    if (version == 0 && flags.any { it.toInt() != 0 }) {
                        raf.seek(pos + header + 1)
                        raf.write(ByteArray(3))
                        changed = true
                    }
                }
                type in CONTAINERS && depth < 6 -> changed = walk(raf, pos + header, pos + size, depth + 1) || changed
            }
            pos += size
        }
        return changed
    }
}
