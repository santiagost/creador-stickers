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

/**
 * Spec 004 (packs independientes) más las reglas de las specs 001 y 003 que siguen vigentes:
 * versión, ícono, contadores, borrado con mínimo de 3, confirmación de WhatsApp.
 */
class PackRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private val trayCalls = mutableListOf<String>()
    private lateinit var repo: PackRepository

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

    private fun create(name: String = "Pack", animated: Boolean = false): StickerPack {
        val r = repo.createPack(name, animated)
        assertTrue("se esperaba Created y vino $r", r is CreatePackResult.Created)
        return (r as CreatePackResult.Created).pack
    }

    private fun add(packId: String, n: Int = 0): AddResult.Added {
        val r = repo.addSticker(packId, webp(n))
        assertTrue("se esperaba Added y vino $r", r is AddResult.Added)
        return r as AddResult.Added
    }

    private fun addMany(packId: String, count: Int) = repeat(count) { add(packId, it) }

    private fun pack(id: String) = repo.load().packs.single { it.identifier == id }

    // --- Crear packs (spec 004) ---

    @Test
    fun `AC-2 crear un pack lo deja vacio, con su nombre y su tipo`() {
        val p = create("Gatos", animated = true)
        assertEquals("pack_1", p.identifier)
        assertEquals("Gatos", p.name)
        assertTrue(p.animated)
        assertTrue(p.stickers.isEmpty())
        assertEquals(listOf(p), repo.load().packs)
    }

    @Test
    fun `D2 el nombre se guarda sin espacios de mas y no puede quedar vacio ni pasar de 128`() {
        assertEquals("Hola", create("  Hola  ").name)
        assertEquals(CreatePackResult.InvalidName, repo.createPack("   ", animated = false))
        assertEquals(CreatePackResult.InvalidName, repo.createPack("x".repeat(129), animated = false))
        assertTrue(repo.createPack("x".repeat(128), animated = false) is CreatePackResult.Created)
    }

    @Test
    fun `D2 nombre sugerido segun el tipo y el proximo numero de pack`() {
        assertEquals("Mis stickers 1", repo.suggestedName(animated = false))
        create()
        assertEquals("Mis animados 2", repo.suggestedName(animated = true))
    }

    @Test
    fun `AC-3 con 10 packs no se puede crear otro, de ningun tipo`() {
        repeat(10) { create("P$it", animated = it % 2 == 0) }
        assertFalse(repo.canCreatePack())
        assertEquals(CreatePackResult.LimitReached, repo.createPack("Otro", animated = false))
        assertEquals(10, repo.load().packs.size)
    }

    @Test
    fun `D5 los packs quedan en orden de creacion`() {
        create("A"); create("B", animated = true); create("C")
        assertEquals(listOf("A", "B", "C"), repo.load().packs.map { it.name })
    }

    // --- Agregar stickers a un pack ---

    @Test
    fun `RN-2 el sticker va al pack elegido aunque haya otros`() {
        val a = create("A")
        val b = create("B")
        add(b.identifier, 1)
        assertTrue(pack(a.identifier).stickers.isEmpty())
        assertEquals(listOf("s1.webp"), pack(b.identifier).stickers.map { it.file })
        assertArrayEquals(webp(1), File(root, "${b.identifier}/s1.webp").readBytes())
    }

    @Test
    fun `el primer sticker de un pack genera el icono y la version arranca en 1`() {
        val p = create()
        val r = add(p.identifier, 1)
        assertEquals(1, r.pack.imageDataVersion)
        assertEquals("tray-of-s1.webp", File(root, "${p.identifier}/${StickerPack.TRAY_FILE}").readText())
        add(p.identifier, 2)
        assertEquals(2, pack(p.identifier).imageDataVersion)
        assertEquals(listOf("s1.webp"), trayCalls)
    }

    @Test
    fun `RN-12 cada sticker lleva el emoji fijo`() {
        val p = create()
        assertEquals(listOf("😀"), add(p.identifier).pack.stickers.single().emojis)
    }

    @Test
    fun `AC-6 con 30 stickers el pack esta lleno y no acepta mas`() {
        val p = create()
        addMany(p.identifier, 30)
        assertTrue(pack(p.identifier).isFull)
        val before = repo.load()
        assertEquals(AddResult.PackFull, repo.addSticker(p.identifier, webp(31)))
        assertEquals(before, repo.load())
    }

    @Test
    fun `agregar a un pack que no existe no cambia nada`() {
        create()
        val before = repo.load()
        assertEquals(AddResult.NotFound, repo.addSticker("pack_9", webp(1)))
        assertEquals(before, repo.load())
    }

    @Test
    fun `RN-14 los numeros de sticker son globales y no se reutilizan`() {
        val a = create("A")
        val b = create("B")
        add(a.identifier); add(b.identifier); add(a.identifier)
        repo.deleteSticker(a.identifier, "s3.webp")
        assertEquals("s4.webp", add(b.identifier).file)
    }

    // --- Persistencia ---

    @Test
    fun `el estado sobrevive a una instancia nueva del repositorio`() {
        val p = create()
        addMany(p.identifier, 4)
        repo.confirmAdded(p.identifier, sentVersion = 4)
        assertEquals(repo.load(), PackRepository(root, ::fakeTray).load())
        assertEquals(listOf("index.json", p.identifier), root.list()!!.sorted())
    }

    @Test
    fun `AC-9 un index json de antes se lee igual y sus packs siguen recibiendo stickers`() {
        File(root, "index.json").writeText(
            """{"schemaVersion":1,"nextPackNumber":2,"nextStickerNumber":4,"packs":[{"identifier":"pack_1","name":"Mis stickers 1","imageDataVersion":3,"lastConfirmedVersion":3,"everAdded":true,"stickers":[{"file":"s1.webp","emojis":["😀"]},{"file":"s2.webp","emojis":["😀"]},{"file":"s3.webp","emojis":["😀"]}]}]}""",
        )
        val p = repo.load().packs.single()
        assertFalse(p.animated)
        assertEquals("s4.webp", add("pack_1").file)
        assertEquals("pack_2", create().identifier)
    }

    // --- Borrar ---

    @Test
    fun `AC-16 borrar en un pack agregado con mas de 3 sube la version y borra el archivo`() {
        val p = create()
        addMany(p.identifier, 4)
        repo.confirmAdded(p.identifier, sentVersion = 4)
        assertTrue(repo.deleteSticker(p.identifier, "s4.webp") is DeleteResult.Deleted)
        assertEquals(5, pack(p.identifier).imageDataVersion)
        assertFalse(File(root, "${p.identifier}/s4.webp").exists())
        assertEquals(SyncState.NEEDS_UPDATE, pack(p.identifier).syncState(whitelisted = true))
    }

    @Test
    fun `AC-17 en un pack agregado con exactamente 3 no se puede borrar`() {
        val p = create()
        addMany(p.identifier, 3)
        repo.confirmAdded(p.identifier, sentVersion = 3)
        assertEquals(DeleteResult.BelowMinimum, repo.deleteSticker(p.identifier, "s2.webp"))
        assertTrue(File(root, "${p.identifier}/s2.webp").exists())
    }

    @Test
    fun `AC-7 spec 004 borrar el ultimo sticker de un pack nunca agregado lo deja vacio, no lo elimina`() {
        val p = create("Vacío")
        add(p.identifier)
        val r = repo.deleteSticker(p.identifier, "s1.webp") as DeleteResult.Deleted
        assertTrue(r.pack.stickers.isEmpty())
        assertEquals("Vacío", pack(p.identifier).name)
        assertFalse(File(root, "${p.identifier}/${StickerPack.TRAY_FILE}").exists())
        // Al volver a agregar, el ícono se genera de nuevo.
        trayCalls.clear()
        add(p.identifier)
        assertEquals(listOf("s2.webp"), trayCalls)
    }

    @Test
    fun `AC-19 borrar el primer sticker regenera el icono desde el nuevo primero`() {
        val p = create()
        addMany(p.identifier, 4)
        trayCalls.clear()
        repo.deleteSticker(p.identifier, "s1.webp")
        assertEquals(listOf("s2.webp"), trayCalls)
        assertEquals(5, pack(p.identifier).imageDataVersion)
    }

    @Test
    fun `borrar un sticker que no existe no cambia nada`() {
        val p = create()
        addMany(p.identifier, 4)
        val before = repo.load()
        assertEquals(DeleteResult.NotFound, repo.deleteSticker(p.identifier, "s99.webp"))
        assertEquals(DeleteResult.NotFound, repo.deleteSticker("pack_9", "s1.webp"))
        assertEquals(before, repo.load())
    }

    // --- Borrar y renombrar packs (spec 008) ---

    @Test
    fun `AC-1 spec 008 borrar un pack lo saca de la lista, borra su carpeta y no toca los demas`() {
        val a = create("A")
        val b = create("B", animated = true)
        addMany(a.identifier, 3)
        add(b.identifier)
        val r = repo.deletePack(a.identifier)
        assertEquals("A", (r as DeletePackResult.Deleted).pack.name)
        assertEquals(listOf("B"), repo.load().packs.map { it.name })
        assertFalse(File(root, a.identifier).exists())
        assertTrue(File(root, "${b.identifier}/s4.webp").exists())
        assertTrue(repo.canCreatePack())
    }

    @Test
    fun `AC-6 spec 008 un pack nuevo despues de borrar no reutiliza el numero`() {
        val a = create("A")
        repo.deletePack(a.identifier)
        assertEquals("pack_2", create("B").identifier)
    }

    @Test
    fun `spec 008 borrar un pack que no existe no cambia nada`() {
        create()
        val before = repo.load()
        assertEquals(DeletePackResult.NotFound, repo.deletePack("pack_9"))
        assertEquals(before, repo.load())
    }

    @Test
    fun `D5 spec 008 al borrar tambien se van las carpetas de packs que ya no estan en la lista`() {
        val a = create("A")
        val b = create("B")
        add(b.identifier)
        val orphan = File(root, "pack_77").apply { mkdirs() }
        File(orphan, "s1.webp").writeText("resto")
        val notAPack = File(root, "otra-cosa").apply { mkdirs() }
        repo.deletePack(a.identifier)
        assertFalse(orphan.exists())
        assertTrue("solo se borran carpetas pack_N", notAPack.exists())
        assertTrue(File(root, "${b.identifier}/s1.webp").exists())
    }

    @Test
    fun `D3 spec 008 renombrar cambia el nombre y sube la version`() {
        val p = create("Viejo")
        addMany(p.identifier, 3)
        repo.confirmAdded(p.identifier, sentVersion = 3)
        val r = repo.renamePack(p.identifier, "  Nuevo  ") as RenameResult.Renamed
        assertEquals("Nuevo", r.pack.name)
        assertEquals("Nuevo", pack(p.identifier).name)
        assertEquals(4, pack(p.identifier).imageDataVersion)
        assertEquals(SyncState.NEEDS_UPDATE, pack(p.identifier).syncState(whitelisted = true))
    }

    @Test
    fun `AC-5 spec 008 nombre invalido o igual no cambia nada`() {
        val p = create("Igual")
        val before = repo.load()
        assertEquals(RenameResult.InvalidName, repo.renamePack(p.identifier, "   "))
        assertEquals(RenameResult.InvalidName, repo.renamePack(p.identifier, "x".repeat(129)))
        assertEquals(RenameResult.Unchanged, repo.renamePack(p.identifier, " Igual "))
        assertEquals(RenameResult.NotFound, repo.renamePack("pack_9", "Otro"))
        assertEquals(before, repo.load())
    }

    // --- Confirmación de WhatsApp ---

    @Test
    fun `RN-8 confirmar guarda la version enviada y marca el pack como agregado alguna vez`() {
        val p = create()
        addMany(p.identifier, 3)
        repo.confirmAdded(p.identifier, sentVersion = 3)
        assertEquals(3, pack(p.identifier).lastConfirmedVersion)
        assertTrue(pack(p.identifier).everAdded)
    }
}
