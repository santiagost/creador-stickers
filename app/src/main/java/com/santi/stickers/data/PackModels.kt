package com.santi.stickers.data

import kotlinx.serialization.Serializable

/** Límites y valores fijos de la spec 001. */
object PackRules {
    const val MIN_STICKERS = 3 // RN-6, RN-10
    const val MAX_STICKERS = 30 // RN-3
    const val MAX_PACKS = 10 // RN-4 (límite de WhatsApp por app)
    const val DEFAULT_EMOJI = "😀" // RN-12, D6
    const val PACK_NAME_PREFIX = "Mis stickers " // D10
    const val PUBLISHER = "Santi" // D10
}

/** Contenido de index.json (sección 9 de la spec). */
@Serializable
data class PackIndex(
    val schemaVersion: Int = 1,
    val nextPackNumber: Int = 1,
    val nextStickerNumber: Int = 1,
    val packs: List<StickerPack> = emptyList(),
)

@Serializable
data class StickerPack(
    val identifier: String,
    val name: String,
    val imageDataVersion: Int = 1,
    /** Última versión que WhatsApp confirmó con add_successful (RN-8). 0 = nunca. */
    val lastConfirmedVersion: Int = 0,
    /** Si WhatsApp lo confirmó alguna vez (RN-10). */
    val everAdded: Boolean = false,
    val stickers: List<StickerEntry> = emptyList(),
) {
    val isFull: Boolean get() = stickers.size >= PackRules.MAX_STICKERS
    val canSendToWhatsApp: Boolean get() = stickers.size >= PackRules.MIN_STICKERS
    val stickersMissing: Int get() = (PackRules.MIN_STICKERS - stickers.size).coerceAtLeast(0)

    companion object {
        const val TRAY_FILE = "tray.png"
    }
}

@Serializable
data class StickerEntry(
    val file: String,
    val emojis: List<String> = listOf(PackRules.DEFAULT_EMOJI),
)

/** Estado de un pack respecto de WhatsApp (RN-7). */
enum class SyncState { NOT_ADDED, UP_TO_DATE, NEEDS_UPDATE }

/**
 * RN-7. [whitelisted] es lo que responde WhatsApp hoy (puede que el usuario haya quitado el pack).
 * Si WhatsApp lo tiene pero no hay confirmación guardada para la versión actual, conviene actualizar.
 */
fun StickerPack.syncState(whitelisted: Boolean): SyncState = when {
    !whitelisted -> SyncState.NOT_ADDED
    imageDataVersion == lastConfirmedVersion -> SyncState.UP_TO_DATE
    else -> SyncState.NEEDS_UPDATE
}

sealed interface AddResult {
    data class Added(val pack: StickerPack, val file: String) : AddResult
    /** RN-4: 10 packs y el último lleno. */
    data object LimitReached : AddResult
}

sealed interface DeleteResult {
    /** [pack] es el pack actualizado, o null si quedó vacío y se eliminó (AC-18). */
    data class Deleted(val pack: StickerPack?) : DeleteResult
    /** RN-10: el pack ya se agregó a WhatsApp y quedaría con menos de 3. */
    data object BelowMinimum : DeleteResult
    data object NotFound : DeleteResult
}
