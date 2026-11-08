package com.santi.stickers.whatsapp

/** Lo que respondió WhatsApp al intent ENABLE_STICKER_PACK (spec 001, sección 10; Anexo A). */
sealed interface AddPackOutcome {
    /** RESULT_OK + add_successful=true: el pack se agregó o actualizó. */
    data object Added : AddPackOutcome

    /** El usuario canceló (o WhatsApp no devolvió datos). */
    data object Cancelled : AddPackOutcome

    /** WhatsApp rechazó el pack; el texto sirve para depurar. */
    data class ValidationError(val message: String) : AddPackOutcome

    /** OK sin add_successful: no se da por confirmado (RN-8). */
    data object Unknown : AddPackOutcome

    /** RN-8: solo una confirmación explícita actualiza la versión confirmada. */
    val confirms: Boolean get() = this == Added

    companion object {
        private const val RESULT_OK = -1 // Activity.RESULT_OK

        fun from(resultCode: Int, addSuccessful: Boolean?, validationError: String?): AddPackOutcome = when {
            validationError != null -> ValidationError(validationError)
            resultCode == RESULT_OK && addSuccessful == true -> Added
            resultCode == RESULT_OK -> Unknown
            else -> Cancelled
        }
    }
}
