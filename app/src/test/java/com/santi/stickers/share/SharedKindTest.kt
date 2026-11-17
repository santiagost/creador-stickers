package com.santi.stickers.share

import com.santi.stickers.data.StickerPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 006, RN-1 y RN-2. */
class SharedKindTest {

    @Test
    fun `RN-1 GIF, foto y video segun el tipo`() {
        assertEquals(SharedKind.GIF, sharedKind("image/gif"))
        assertEquals(SharedKind.PHOTO, sharedKind("image/jpeg"))
        assertEquals(SharedKind.PHOTO, sharedKind("image/webp"))
        assertEquals(SharedKind.PHOTO, sharedKind("image/*"))
        assertEquals(SharedKind.VIDEO, sharedKind("video/mp4"))
        assertEquals(SharedKind.VIDEO, sharedKind("video/*"))
    }

    @Test
    fun `RN-1 sin importar mayusculas, espacios ni parametros`() {
        assertEquals(SharedKind.GIF, sharedKind(" IMAGE/GIF "))
        assertEquals(SharedKind.VIDEO, sharedKind("video/mp4; codecs=avc1"))
    }

    @Test
    fun `RN-1 lo demas no sirve`() {
        assertNull(sharedKind(null))
        assertNull(sharedKind(""))
        assertNull(sharedKind("text/plain"))
        assertNull(sharedKind("application/pdf"))
        assertNull(sharedKind("*/*"))
    }

    @Test
    fun `RN-2 solo los packs del tipo que corresponde, llenos incluidos`() {
        fun pack(id: String, animated: Boolean) = StickerPack(identifier = id, name = id, animated = animated)
        val packs = listOf(pack("a", false), pack("b", true), pack("c", false))
        assertEquals(listOf("a", "c"), packsFor(SharedKind.PHOTO, packs).map { it.identifier })
        assertEquals(listOf("b"), packsFor(SharedKind.VIDEO, packs).map { it.identifier })
        assertEquals(listOf("b"), packsFor(SharedKind.GIF, packs).map { it.identifier })
    }
}
