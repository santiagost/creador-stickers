package com.santi.stickers.whatsapp

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 001, sección 10: intent, respuesta y consulta de whitelist contra el WhatsApp real del teléfono. */
@RunWith(AndroidJUnit4::class)
class WhatsAppTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contract = AddStickerPackContract()

    @Test
    fun intentConAccionPaqueteYExtrasDelContrato() {
        val intent = contract.createIntent(context, AddPackRequest(packId = "pack_1", packName = "Mis stickers 1", version = 7))

        assertEquals("com.whatsapp.intent.action.ENABLE_STICKER_PACK", intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals("pack_1", intent.getStringExtra("sticker_pack_id"))
        assertEquals("com.santi.stickers.stickercontentprovider", intent.getStringExtra("sticker_pack_authority"))
        assertEquals("Mis stickers 1", intent.getStringExtra("sticker_pack_name"))
    }

    @Test
    fun respuestaConLosExtrasQueDevuelveWhatsApp() {
        val added = Intent().putExtra("add_successful", true)
        assertEquals(AddPackOutcome.Added, contract.parseResult(Activity.RESULT_OK, added))

        val cancelled = Intent().putExtra("user_cancelled", true)
        assertEquals(AddPackOutcome.Cancelled, contract.parseResult(Activity.RESULT_CANCELED, cancelled))
        assertEquals(AddPackOutcome.Cancelled, contract.parseResult(Activity.RESULT_CANCELED, null))

        val invalid = Intent().putExtra("validation_error", "bad tray")
        assertEquals(AddPackOutcome.ValidationError("bad tray"), contract.parseResult(Activity.RESULT_CANCELED, invalid))
    }

    @Test
    fun whitelistDeUnPackQueWhatsAppNoTieneEsFalso() {
        assumeTrue("WhatsApp no está instalado en este teléfono", WhatsApp.isInstalled(context))
        // Si <queries> faltara en el manifest, isInstalled daría false y este test se saltearía:
        // por eso el test siguiente exige que esté instalado en el teléfono de pruebas.
        assertFalse(WhatsApp.isWhitelisted(context, "pack_que_no_existe_${System.nanoTime()}"))
    }

    @Test
    fun whatsAppVisibleDesdeLaApp() {
        // El teléfono de pruebas (S20 FE) tiene WhatsApp: si esto falla, falta <queries> en el manifest.
        assertEquals(true, WhatsApp.isInstalled(context))
    }
}
