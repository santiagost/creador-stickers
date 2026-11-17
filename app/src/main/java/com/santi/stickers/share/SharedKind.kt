package com.santi.stickers.share

import com.santi.stickers.data.StickerPack

/** Qué se puede hacer con un archivo compartido (spec 006, RN-1). */
enum class SharedKind {
    PHOTO, VIDEO, GIF;

    val animated get() = this != PHOTO
}

/** RN-1: `image/gif` → GIF; `image/...` → foto; `video/...` → video; lo demás, null. */
fun sharedKind(mime: String?): SharedKind? {
    val type = mime?.substringBefore(';')?.trim()?.lowercase() ?: return null
    return when {
        type == "image/gif" -> SharedKind.GIF
        type.startsWith("image/") -> SharedKind.PHOTO
        type.startsWith("video/") -> SharedKind.VIDEO
        else -> null
    }
}

/** RN-2: los packs del tipo que corresponde, en su orden (los llenos se muestran pero no se eligen). */
fun packsFor(kind: SharedKind, packs: List<StickerPack>): List<StickerPack> = packs.filter { it.animated == kind.animated }
