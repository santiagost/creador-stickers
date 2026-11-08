package com.santi.stickers.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 001: RN-8, AC-11, AC-13, AC-14. Cómo se interpreta la respuesta de WhatsApp al intent.
 * Valores reales observados en el spike (Anexo A): add_successful=true / user_cancelled=true.
 */
class AddPackOutcomeTest {

    private val ok = -1 // Activity.RESULT_OK
    private val canceled = 0 // Activity.RESULT_CANCELED

    @Test
    fun `AC-11 y AC-13 OK con add_successful es agregado`() {
        assertEquals(AddPackOutcome.Added, AddPackOutcome.from(ok, addSuccessful = true, validationError = null))
    }

    @Test
    fun `RN-8 OK sin add_successful no cuenta como confirmado`() {
        assertEquals(AddPackOutcome.Unknown, AddPackOutcome.from(ok, addSuccessful = null, validationError = null))
        assertEquals(AddPackOutcome.Unknown, AddPackOutcome.from(ok, addSuccessful = false, validationError = null))
    }

    @Test
    fun `AC-14 cancelar es cancelado`() {
        assertEquals(AddPackOutcome.Cancelled, AddPackOutcome.from(canceled, addSuccessful = null, validationError = null))
    }

    @Test
    fun `un error de validacion se informa con su texto`() {
        assertEquals(
            AddPackOutcome.ValidationError("sticker too big"),
            AddPackOutcome.from(canceled, addSuccessful = null, validationError = "sticker too big"),
        )
    }

    @Test
    fun `solo Added confirma`() {
        assertEquals(true, AddPackOutcome.Added.confirms)
        assertEquals(false, AddPackOutcome.Cancelled.confirms)
        assertEquals(false, AddPackOutcome.Unknown.confirms)
        assertEquals(false, AddPackOutcome.ValidationError("x").confirms)
    }
}
