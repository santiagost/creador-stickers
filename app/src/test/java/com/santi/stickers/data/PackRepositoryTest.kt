package com.santi.stickers.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Spec 001: RN-3 a RN-6, RN-10 a RN-12, RN-14; AC-6 a AC-9, AC-16 a AC-18 y parte lógica de AC-19. */
class PackRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private val trayCalls = mutableListOf<String>()
    private lateinit var repo: PackRepository

    /** Tray falso: deja constancia de qué sticker se usó para generarlo. */
    private fun fakeTray(sticker: File): ByteArray {
        trayCalls += sticker.name
        return "tray-of-${sticker.name}".toByteArray()
    }

    @Before
    fun setUp() {
        root = tmp.newFolder("packs")
        repo = PackRepository(root, ::fakeTray)
    }

    private fun webp(n: Int) = "webp-$n".toByteArray()

    private fun added(result: AddResult): AddResult.Added {
        assertTrue("se esperaba Added y vino $result", result is AddResult.Added)
        return result as AddResult.Added
    }

    private fun addMany(count: Int) = repeat(count) { added(repo.addSticker(webp(it))) }

    private fun pack(id: String) = repo.load().packs.single { it.identifier == id }

    // --- Crear y llenar packs ---

    @Test
    fun `AC-6 el primer sticker crea Mis stickers 1 con version 1, archivo e icono`() {
        val result = added(repo.addSticker(webp(1)))

        val p = result.pack
        assertEquals("pack_1", p.identifier)
        assertEquals("Mis stickers 1", p.name)
        assertEquals(1, p.imageDataVersion)
        assertEquals(listOf("s1.webp"), p.stickers.map { it.file })
        assertArrayEquals(webp(1), File(root, "pack_1/s1.webp").readBytes())
        assertEquals("tray-of-s1.webp", File(root, "pack_1/${StickerPack.TRAY_FILE}").readText())
        assertFalse(p.everAdded)
        assertEquals(0, p.lastConfirmedVersion)
    }

    @Test
    fun `RN-12 cada sticker lleva el emoji fijo`() {
        val p = added(repo.addSticker(webp(1))).pack
        assertEquals(listOf("😀"), p.stickers.single().emojis)
    }

    @Test
    fun `AC-9 sumar un sticker a un pack existente sube la version en 1 y no regenera el icono`() {
        addMany(1)
        val p = added(repo.addSticker(webp(2))).pack
        assertEquals(2, p.imageDataVersion)
        assertEquals(listOf("s1.webp", "s2.webp"), p.stickers.map { it.file })
        assertEquals(listOf("s1.webp"), trayCalls)
    }

    @Test
    fun `AC-7 con el ultimo pack lleno se crea Mis stickers 2 y el pack lleno no cambia`() {
        addMany(30)
        val before = pack("pack_1")

        val result = added(repo.addSticker(webp(31)))

        assertEquals("pack_2", result.pack.identifier)
        assertEquals("Mis stickers 2", result.pack.name)
        assertEquals(listOf("s31.webp"), result.pack.stickers.map { it.file })
        assertEquals(before, pack("pack_1"))
    }

    @Test
    fun `AC-8 con 10 packs llenos no se puede agregar y no se escribe nada`() {
        addMany(300)
        assertEquals(10, repo.load().packs.size)
        assertFalse(repo.canAddSticker())

        val indexBefore = File(root, "index.json").readText()
        assertEquals(AddResult.LimitReached, repo.addSticker(webp(301)))
        assertEquals(indexBefore, File(root, "index.json").readText())
        assertFalse(File(root, "pack_11").exists())
    }

    @Test
    fun `RN-4 con 10 packs y el ultimo con lugar todavia se puede agregar`() {
        addMany(299)
        assertTrue(repo.canAddSticker())
    }

    // --- Persistencia ---

    @Test
    fun `el estado sobrevive a una instancia nueva del repositorio y no quedan temporales`() {
        addMany(4)
        repo.confirmAdded("pack_1", sentVersion = 4)

        val reloaded = PackRepository(root, ::fakeTray).load()

        assertEquals(repo.load(), reloaded)
        assertEquals(listOf("index.json", "pack_1"), root.list()!!.sorted())
    }

    @Test
    fun `sin index json el repositorio arranca vacio`() {
        assertTrue(repo.load().packs.isEmpty())
        assertTrue(repo.canAddSticker())
    }

    // --- Borrar ---

    @Test
    fun `AC-16 borrar en un pack agregado con mas de 3 sube la version y borra el archivo`() {
        addMany(4)
        repo.confirmAdded("pack_1", sentVersion = 4)

        val result = repo.deleteSticker("pack_1", "s4.webp")

        assertTrue(result is DeleteResult.Deleted)
        val p = pack("pack_1")
        assertEquals(5, p.imageDataVersion)
        assertEquals(listOf("s1.webp", "s2.webp", "s3.webp"), p.stickers.map { it.file })
        assertFalse(File(root, "pack_1/s4.webp").exists())
        assertEquals(SyncState.NEEDS_UPDATE, p.syncState(whitelisted = true))
    }

    @Test
    fun `AC-17 en un pack agregado con exactamente 3 no se puede borrar`() {
        addMany(3)
        repo.confirmAdded("pack_1", sentVersion = 3)
        val before = repo.load()

        assertEquals(DeleteResult.BelowMinimum, repo.deleteSticker("pack_1", "s2.webp"))
        assertEquals(before, repo.load())
        assertTrue(File(root, "pack_1/s2.webp").exists())
    }

    @Test
    fun `RN-10 un pack nunca agregado puede quedar con menos de 3`() {
        addMany(3)
        assertTrue(repo.deleteSticker("pack_1", "s3.webp") is DeleteResult.Deleted)
        assertEquals(2, pack("pack_1").stickers.size)
    }

    @Test
    fun `AC-18 un pack nunca agregado que queda vacio desaparece y el siguiente usa un identificador nuevo`() {
        addMany(1)

        val result = repo.deleteSticker("pack_1", "s1.webp")

        assertEquals(DeleteResult.Deleted(pack = null), result)
        assertTrue(repo.load().packs.isEmpty())
        assertFalse(File(root, "pack_1").exists())

        val next = added(repo.addSticker(webp(2))).pack
        assertEquals("pack_2", next.identifier)
        assertEquals("Mis stickers 2", next.name)
        assertEquals(listOf("s2.webp"), next.stickers.map { it.file })
    }

    @Test
    fun `RN-14 los numeros de sticker no se reutilizan despues de borrar`() {
        addMany(5)
        repo.deleteSticker("pack_1", "s5.webp")
        val p = added(repo.addSticker(webp(6))).pack
        assertEquals("s6.webp", p.stickers.last().file)
    }

    @Test
    fun `AC-19 borrar el primer sticker regenera el icono desde el nuevo primero con una sola suba de version`() {
        addMany(4)
        trayCalls.clear()

        repo.deleteSticker("pack_1", "s1.webp")

        assertEquals(listOf("s2.webp"), trayCalls)
        assertEquals("tray-of-s2.webp", File(root, "pack_1/${StickerPack.TRAY_FILE}").readText())
        assertEquals(5, pack("pack_1").imageDataVersion)
    }

    @Test
    fun `borrar un sticker que no existe no cambia nada`() {
        addMany(4)
        val before = repo.load()
        assertEquals(DeleteResult.NotFound, repo.deleteSticker("pack_1", "s99.webp"))
        assertEquals(DeleteResult.NotFound, repo.deleteSticker("pack_9", "s1.webp"))
        assertEquals(before, repo.load())
    }

    @Test
    fun `los stickers nuevos siguen yendo al ultimo pack aunque se haya liberado lugar en uno anterior`() {
        addMany(31)
        repo.deleteSticker("pack_1", "s1.webp")
        val p = added(repo.addSticker(webp(32))).pack
        assertEquals("pack_2", p.identifier)
    }

    // --- Confirmación de WhatsApp ---

    @Test
    fun `RN-8 confirmar guarda la version enviada y marca el pack como agregado alguna vez`() {
        addMany(3)
        repo.confirmAdded("pack_1", sentVersion = 3)
        val p = pack("pack_1")
        assertEquals(3, p.lastConfirmedVersion)
        assertTrue(p.everAdded)
    }

    @Test
    fun `confirmar un pack que ya no existe no falla ni cambia nada`() {
        addMany(3)
        val before = repo.load()
        repo.confirmAdded("pack_9", sentVersion = 1)
        assertEquals(before, repo.load())
    }
}
