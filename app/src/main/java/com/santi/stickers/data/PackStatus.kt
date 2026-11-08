package com.santi.stickers.data

/** Un pack con su estado respecto de WhatsApp, para mostrar en Inicio. */
data class PackStatus(val pack: StickerPack, val state: SyncState)

/**
 * RN-7 y RN-9: arma el estado de cada pack con lo que responde WhatsApp.
 * A los packs con menos de 3 stickers no se les pregunta: WhatsApp no puede tenerlos.
 */
fun packStatuses(index: PackIndex, isWhitelisted: (packId: String) -> Boolean): List<PackStatus> =
    index.packs.map { pack ->
        val whitelisted = pack.canSendToWhatsApp && isWhitelisted(pack.identifier)
        PackStatus(pack, pack.syncState(whitelisted))
    }
