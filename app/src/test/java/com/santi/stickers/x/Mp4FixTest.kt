package com.santi.stickers.x

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/** Spec 007: los "GIF" de X traen la caja `ctts` con flags = 1 y Android rechaza el archivo entero. */
class Mp4FixTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun box(type: String, vararg children: ByteArray): ByteArray {
        val body = children.fold(ByteArray(0)) { acc, b -> acc + b }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(8 + body.size)
            writeBytes(type)
            write(body)
        }
        return out.toByteArray()
    }

    private fun raw(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

    /** ctts con versión/flags dados y una entrada (1 muestra, desplazamiento 12). */
    private fun ctts(versionFlags: IntArray) = box("ctts", raw(*versionFlags), raw(0, 0, 0, 1), raw(0, 0, 0, 1, 0, 0, 0, 12))

    private fun mp4(cttsBox: ByteArray, mdatFirst: Boolean = false): ByteArray {
        val moov = box("moov", box("mvhd", ByteArray(100)), box("trak", box("mdia", box("minf", box("stbl", box("stts", ByteArray(16)), cttsBox)))))
        // El mdat tiene adentro los mismos bytes que una caja ctts con flags 1: no se tienen que tocar.
        val mdat = box("mdat", ctts(intArrayOf(0, 0, 0, 1)) + ByteArray(50) { 7 })
        val ftyp = box("ftyp", "iso5".toByteArray(), ByteArray(4))
        return if (mdatFirst) ftyp + mdat + moov else ftyp + moov + mdat
    }

    private fun file(bytes: ByteArray) = tmp.newFile().apply { writeBytes(bytes) }

    @Test
    fun `ctts con flags 1 queda en 0 y no cambia nada mas`() {
        for (mdatFirst in listOf(false, true)) {
            val original = mp4(ctts(intArrayOf(0, 0, 0, 1)), mdatFirst)
            val f = file(original)
            assertTrue(Mp4Fix.clearCttsFlags(f))
            val fixed = f.readBytes()
            val expected = mp4(ctts(intArrayOf(0, 0, 0, 0)), mdatFirst)
            assertArrayEquals("mdat primero = $mdatFirst", expected, fixed)
            assertEquals(original.size, fixed.size)
        }
    }

    @Test
    fun `si ya esta bien no toca nada`() {
        val original = mp4(ctts(intArrayOf(0, 0, 0, 0)))
        val f = file(original)
        assertFalse(Mp4Fix.clearCttsFlags(f))
        assertArrayEquals(original, f.readBytes())
    }

    @Test
    fun `version 1 se deja como esta`() {
        val original = mp4(ctts(intArrayOf(1, 0, 0, 1)))
        val f = file(original)
        assertFalse(Mp4Fix.clearCttsFlags(f))
        assertArrayEquals(original, f.readBytes())
    }

    @Test
    fun `archivos raros no rompen`() {
        assertFalse(Mp4Fix.clearCttsFlags(file(ByteArray(0))))
        assertFalse(Mp4Fix.clearCttsFlags(file("<!DOCTYPE html>".toByteArray())))
        assertFalse(Mp4Fix.clearCttsFlags(file(raw(0, 0, 0, 4, 'm'.code, 'o'.code, 'o'.code, 'v'.code)))) // tamaño < 8
        val truncated = mp4(ctts(intArrayOf(0, 0, 0, 1))).copyOf(60)
        Mp4Fix.clearCttsFlags(file(truncated)) // no tira excepción
    }

    @Test
    fun `sin video no hay ctts`() {
        val f: File = file(box("ftyp", ByteArray(8)) + box("mdat", ByteArray(10)))
        assertFalse(Mp4Fix.clearCttsFlags(f))
    }
}
