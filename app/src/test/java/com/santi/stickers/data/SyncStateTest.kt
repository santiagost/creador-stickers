package com.santi.stickers.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 001: RN-6, RN-7, RN-8; AC-10 a AC-15 (parte lógica). */
class SyncStateTest {

    private fun pack(stickers: Int, version: Int, confirmed: Int = 0, everAdded: Boolean = confirmed > 0) =
        StickerPack(
            identifier = "pack_1",
            name = "Mis stickers 1",
            imageDataVersion = version,
            lastConfirmedVersion = confirmed,
            everAdded = everAdded,
            stickers = (1..stickers).map { StickerEntry("s$it.webp") },
        )

    @Test
    fun `AC-10 con menos de 3 stickers no se puede enviar y se sabe cuantos faltan`() {
        assertFalse(pack(stickers = 1, version = 1).canSendToWhatsApp)
        assertEquals(2, pack(stickers = 1, version = 1).stickersMissing)
        assertEquals(1, pack(stickers = 2, version = 2).stickersMissing)
    }

    @Test
    fun `RN-6 con 3 o mas stickers se puede enviar`() {
        assertTrue(pack(stickers = 3, version = 3).canSendToWhatsApp)
        assertEquals(0, pack(stickers = 3, version = 3).stickersMissing)
        assertEquals(0, pack(stickers = 30, version = 30).stickersMissing)
    }

    @Test
    fun `AC-11 agregado y confirmado en la version actual esta al dia`() {
        assertEquals(SyncState.UP_TO_DATE, pack(stickers = 3, version = 3, confirmed = 3).syncState(whitelisted = true))
    }

    @Test
    fun `AC-12 agregado con una version confirmada menor esta para actualizar`() {
        assertEquals(SyncState.NEEDS_UPDATE, pack(stickers = 4, version = 4, confirmed = 3).syncState(whitelisted = true))
    }

    @Test
    fun `AC-14 si WhatsApp nunca confirmo, el pack sigue sin agregar`() {
        assertEquals(SyncState.NOT_ADDED, pack(stickers = 3, version = 3).syncState(whitelisted = false))
    }

    @Test
    fun `AC-15 si WhatsApp ya no lo tiene, figura sin agregar aunque se haya confirmado antes`() {
        assertEquals(SyncState.NOT_ADDED, pack(stickers = 3, version = 3, confirmed = 3).syncState(whitelisted = false))
    }

    @Test
    fun `WhatsApp lo tiene pero no hay confirmacion guardada entonces conviene actualizar`() {
        assertEquals(SyncState.NEEDS_UPDATE, pack(stickers = 3, version = 3).syncState(whitelisted = true))
    }
}
