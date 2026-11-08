package com.santi.stickers.provider

import android.database.Cursor
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.santi.stickers.data.AddResult
import com.santi.stickers.data.PackRepository
import com.santi.stickers.data.Packs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

/**
 * Spec 001: AC-20, AC-21, AC-22 y la sección 8 (permisos del provider).
 * Usa un repositorio en cacheDir para no tocar los stickers reales de la app instalada.
 */
@RunWith(AndroidJUnit4::class)
class StickerContentProviderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val authority = "com.santi.stickers.stickercontentprovider"
    private lateinit var root: File
    private lateinit var repo: PackRepository

    @Before
    fun setUp() {
        root = File(context.cacheDir, "provider-test-packs").apply { deleteRecursively() }
        repo = PackRepository(root) { sticker -> "tray-of-${sticker.name}".toByteArray() }
        Packs.useForTests(repo)
    }

    @After
    fun tearDown() {
        Packs.useForTests(null)
        root.deleteRecursively()
    }

    private fun uri(vararg segments: String): Uri =
        Uri.Builder().scheme("content").authority(authority).apply { segments.forEach { appendPath(it) } }.build()

    private fun addStickers(count: Int) = repeat(count) { assertTrue(repo.addSticker("webp-$it".toByteArray()) is AddResult.Added) }

    private fun <T> Cursor.rows(read: Cursor.() -> T): List<T> = use { c ->
        buildList { while (c.moveToNext()) add(c.read()) }
    }

    private fun Cursor.str(column: String) = getString(getColumnIndexOrThrow(column))

    private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))

    private fun readAsset(vararg segments: String): ByteArray =
        resolver.openAssetFileDescriptor(uri(*segments), "r")!!.use { it.createInputStream().readBytes() }

    /** Si el provider devuelve null, ContentResolver devuelve null (o lanza FileNotFoundException, según el camino). */
    private fun assertAssetMissing(vararg segments: String) {
        val afd = try {
            resolver.openAssetFileDescriptor(uri(*segments), "r")
        } catch (_: FileNotFoundException) {
            null
        }
        if (afd != null) {
            afd.close()
            fail("no se esperaba un archivo para ${segments.joinToString("/")}")
        }
    }

    @Test
    fun ac20_metadataDevuelveLasColumnasDelContratoSoloParaPacksConAlMenos3() {
        addStickers(31) // pack_1 lleno, pack_2 con 1 sticker

        val rows = resolver.query(uri("metadata"), null, null, null, null)!!.rows {
            assertEquals(
                listOf(
                    "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
                    "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email",
                    "sticker_pack_publisher_website", "sticker_pack_privacy_policy_website",
                    "sticker_pack_license_agreement_website", "image_data_version",
                    "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
                ),
                columnNames.toList(),
            )
            listOf(
                str("sticker_pack_identifier"), str("sticker_pack_name"), str("sticker_pack_publisher"),
                str("sticker_pack_icon"), str("image_data_version"),
                int("whatsapp_will_not_cache_stickers").toString(), int("animated_sticker_pack").toString(),
            )
        }

        assertEquals(listOf(listOf("pack_1", "Mis stickers 1", "Santi", "tray.png", "30", "0", "0")), rows)
    }

    @Test
    fun ac20_metadataDeUnPackPuntual() {
        addStickers(3)
        assertEquals(listOf("pack_1"), resolver.query(uri("metadata", "pack_1"), null, null, null, null)!!.rows { str("sticker_pack_identifier") })
        assertEquals(emptyList<String>(), resolver.query(uri("metadata", "pack_9"), null, null, null, null)!!.rows { str("sticker_pack_identifier") })
    }

    @Test
    fun ac20_stickersDeUnPackConEmojiYAccesibilidad() {
        addStickers(3)
        val rows = resolver.query(uri("stickers", "pack_1"), null, null, null, null)!!.rows {
            Triple(str("sticker_file_name"), str("sticker_emoji"), str("sticker_accessibility_text"))
        }
        assertEquals(
            listOf(Triple("s1.webp", "😀", ""), Triple("s2.webp", "😀", ""), Triple("s3.webp", "😀", "")),
            rows,
        )
    }

    @Test
    fun ac20_entregaLosArchivosDelPackConSuTipo() {
        addStickers(3)
        assertArrayEquals("webp-1".toByteArray(), readAsset("stickers_asset", "pack_1", "s2.webp"))
        assertArrayEquals("tray-of-s1.webp".toByteArray(), readAsset("stickers_asset", "pack_1", "tray.png"))
        assertEquals("image/webp", resolver.getType(uri("stickers_asset", "pack_1", "s2.webp")))
        assertEquals("image/png", resolver.getType(uri("stickers_asset", "pack_1", "tray.png")))
    }

    @Test
    fun ac21_noEntregaArchivosQueNoEstanEnUnPack() {
        addStickers(31)
        File(repo.packDir("pack_1"), "otro.webp").writeBytes(byteArrayOf(1))
        assertAssetMissing("stickers_asset", "pack_1", "otro.webp") // existe en disco pero no está en el índice
        assertAssetMissing("stickers_asset", "pack_1", "s31.webp") // es de pack_2
        assertAssetMissing("stickers_asset", "pack_1", "../index.json")
        assertAssetMissing("stickers_asset", "..", "index.json")
        assertAssetMissing("stickers_asset", "pack_9", "s1.webp")
        assertAssetMissing("stickers_asset", "pack_1")
    }

    @Test
    fun ac22_losCambiosSeVenSinReiniciar() {
        addStickers(3)
        fun version() = resolver.query(uri("metadata", "pack_1"), null, null, null, null)!!.rows { str("image_data_version") }.single()
        fun count() = resolver.query(uri("stickers", "pack_1"), null, null, null, null)!!.rows { 1 }.size
        assertEquals("3", version())

        repo.addSticker("nuevo".toByteArray())

        assertEquals("4", version())
        assertEquals(4, count())
        assertArrayEquals("nuevo".toByteArray(), readAsset("stickers_asset", "pack_1", "s4.webp"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun uriDesconocidaFalla() {
        resolver.query(uri("otra_cosa"), null, null, null, null)
    }

    @Test
    fun seccion8_providerExportadoSoloLecturaConPermisoDeWhatsApp() {
        @Suppress("DEPRECATION") // la variante con ComponentInfoFlags es de API 33 y minSdk es 30
        val info = context.packageManager.resolveContentProvider(authority, 0)!!
        assertTrue(info.exported)
        assertEquals("com.whatsapp.sticker.READ", info.readPermission)
        assertEquals(null, info.writePermission)
        assertFalse(info.grantUriPermissions)
    }
}
