package com.santi.stickers.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Spec 003: RN-6, RN-7, D8, D10; AC-8, AC-9 y AC-13. */
class AnimatedPacksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var repo: PackRepository

    @Before
    fun setUp() {
        root = tmp.newFolder("packs")
        repo = PackRepository(root) { "tray-of-${it.name}".toByteArray() }
    }

    private fun add(animated: Boolean): StickerPack {
        val result = repo.addSticker("webp".toByteArray(), animated)
        assertTrue("se esperaba Added y vino $result", result is AddResult.Added)
        return (result as AddResult.Added).pack
    }

    private fun addMany(count: Int, animated: Boolean) = repeat(count) { add(animated) }

    @Test
    fun `AC-8 el primer animado crea Mis animados con su propio identificador y marcado como animado`() {
        addMany(2, animated = false)

        val p = add(animated = true)

        assertEquals("pack_2", p.identifier)
        assertEquals("Mis animados 2", p.name)
        assertTrue(p.animated)
        assertEquals(1, p.imageDataVersion)
        assertTrue(File(root, "pack_2/${StickerPack.TRAY_FILE}").exists())
    }

    @Test
    fun `RN-6 estaticos y animados nunca se mezclan y cada uno va a su ultimo pack`() {
        add(animated = false) // pack_1 estático
        add(animated = true) // pack_2 animado
        val estatico = add(animated = false)
        val animado = add(animated = true)

        assertEquals("pack_1", estatico.identifier)
        assertEquals("pack_2", animado.identifier)
        val packs = repo.load().packs
        assertEquals(listOf(false, true), packs.map { it.animated })
        assertEquals(listOf(2, 2), packs.map { it.stickers.size })
    }

    @Test
    fun `RN-6 con el pack animado lleno se crea otro animado aunque el estatico tenga lugar`() {
        add(animated = false)
        addMany(30, animated = true)

        val p = add(animated = true)

        assertEquals("pack_3", p.identifier)
        assertEquals("Mis animados 3", p.name)
        assertEquals(1, repo.load().packs.first { it.identifier == "pack_1" }.stickers.size)
    }

    @Test
    fun `AC-9 el limite de 10 packs es compartido entre estaticos y animados`() {
        addMany(9 * 30, animated = false) // 9 packs estáticos llenos
        add(animated = true) // pack 10: animado con lugar

        assertTrue("todavía entra un animado", repo.canAddSticker(animated = true))
        assertFalse("no hay lugar para un pack estático nuevo", repo.canAddSticker(animated = false))
        assertEquals(AddResult.LimitReached, repo.addSticker("webp".toByteArray(), animated = false))
        assertEquals(10, repo.load().packs.size)
    }

    @Test
    fun `AC-9 con 10 packs y el animado lleno tampoco entra otro animado`() {
        addMany(9 * 30, animated = false)
        addMany(30, animated = true)
        assertFalse(repo.canAddSticker(animated = true))
        assertEquals(AddResult.LimitReached, repo.addSticker("webp".toByteArray(), animated = true))
    }

    @Test
    fun `AC-13 un index json de antes, sin el campo animated, se lee como estatico`() {
        File(root, "index.json").writeText(
            """{"schemaVersion":1,"nextPackNumber":2,"nextStickerNumber":4,"packs":[{"identifier":"pack_1","name":"Mis stickers 1","imageDataVersion":3,"lastConfirmedVersion":3,"everAdded":true,"stickers":[{"file":"s1.webp","emojis":["😀"]},{"file":"s2.webp","emojis":["😀"]},{"file":"s3.webp","emojis":["😀"]}]}]}""",
        )

        val p = repo.load().packs.single()
        assertFalse(p.animated)

        val nuevoEstatico = add(animated = false)
        assertEquals("pack_1", nuevoEstatico.identifier)
    }

    @Test
    fun `por defecto se sigue agregando como estatico`() {
        val result = repo.addSticker("webp".toByteArray()) as AddResult.Added
        assertFalse(result.pack.animated)
        assertTrue(repo.canAddSticker())
    }
}
