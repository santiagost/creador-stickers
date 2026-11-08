package com.santi.stickers.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 001: RN-7 y RN-9 (estado de cada pack al volver a la app); AC-15. */
class PackStatusTest {

    private fun pack(id: String, stickers: Int, version: Int, confirmed: Int = 0) = StickerPack(
        identifier = id,
        name = id,
        imageDataVersion = version,
        lastConfirmedVersion = confirmed,
        everAdded = confirmed > 0,
        stickers = (1..stickers).map { StickerEntry("$id-$it.webp") },
    )

    @Test
    fun `combina cada pack con lo que responde WhatsApp`() {
        val index = PackIndex(
            packs = listOf(
                pack("pack_1", stickers = 30, version = 30, confirmed = 30),
                pack("pack_2", stickers = 5, version = 6, confirmed = 4),
                pack("pack_3", stickers = 3, version = 3),
            ),
        )
        val whatsAppHas = setOf("pack_1", "pack_2")

        val statuses = packStatuses(index) { it in whatsAppHas }

        assertEquals(
            listOf(SyncState.UP_TO_DATE, SyncState.NEEDS_UPDATE, SyncState.NOT_ADDED),
            statuses.map { it.state },
        )
        assertEquals(index.packs, statuses.map { it.pack })
    }

    @Test
    fun `AC-15 un pack quitado desde WhatsApp figura sin agregar`() {
        val index = PackIndex(packs = listOf(pack("pack_1", stickers = 3, version = 3, confirmed = 3)))
        assertEquals(SyncState.NOT_ADDED, packStatuses(index) { false }.single().state)
    }

    @Test
    fun `no se le pregunta a WhatsApp por packs con menos de 3 stickers`() {
        val asked = mutableListOf<String>()
        val index = PackIndex(packs = listOf(pack("pack_1", stickers = 2, version = 2)))

        val status = packStatuses(index) { asked += it; true }.single()

        assertEquals(emptyList<String>(), asked)
        assertEquals(SyncState.NOT_ADDED, status.state)
    }
}
