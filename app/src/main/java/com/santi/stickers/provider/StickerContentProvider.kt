package com.santi.stickers.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.santi.stickers.data.PackRules
import com.santi.stickers.data.Packs
import com.santi.stickers.data.StickerPack
import java.io.File

/**
 * Le muestra los packs a WhatsApp (spec 001, sección 10). Mismo contrato que StickerContentProvider
 * del repo oficial WhatsApp/stickers: rutas y nombres de columnas no se pueden cambiar.
 * Lee el índice en cada consulta, así WhatsApp ve los cambios sin reiniciar la app (AC-22).
 */
class StickerContentProvider : ContentProvider() {

    private val repository get() = Packs.repository(requireNotNull(context))

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val seg = uri.pathSegments
        // Solo se publican packs que WhatsApp acepta (RN-6).
        val packs = repository.load().packs.filter { it.canSendToWhatsApp }
        val cursor = when {
            seg.size == 1 && seg[0] == METADATA -> metadataCursor(packs)
            seg.size == 2 && seg[0] == METADATA -> metadataCursor(packs.filter { it.identifier == seg[1] })
            seg.size == 2 && seg[0] == STICKERS -> stickersCursor(packs.firstOrNull { it.identifier == seg[1] })
            else -> throw IllegalArgumentException("URI desconocida: $uri")
        }
        cursor.setNotificationUri(requireNotNull(context).contentResolver, uri)
        return cursor
    }

    private fun metadataCursor(packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(METADATA_COLUMNS)
        packs.forEach { p ->
            cursor.addRow(
                arrayOf<Any?>(
                    p.identifier,
                    p.name,
                    PackRules.PUBLISHER,
                    StickerPack.TRAY_FILE,
                    "", "", "", "", "", "", // links y contacto: opcionales, no se usan (uso personal)
                    p.imageDataVersion.toString(),
                    0, // whatsapp_will_not_cache_stickers: deprecado, WhatsApp lo ignora desde 2.25.9.78
                    if (p.animated) 1 else 0, // animated_sticker_pack (spec 003, RN-8)
                ),
            )
        }
        return cursor
    }

    private fun stickersCursor(pack: StickerPack?): Cursor {
        val cursor = MatrixCursor(arrayOf(STICKER_FILE_NAME, STICKER_EMOJI, STICKER_ACCESSIBILITY_TEXT))
        pack?.stickers?.forEach { cursor.addRow(arrayOf<Any?>(it.file, it.emojis.joinToString(","), "")) }
        return cursor
    }

    /** RN-13: solo archivos que figuran en un pack publicado; el nombre se compara contra el índice, nunca se arma una ruta libre. */
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val file = resolveAsset(uri) ?: return null
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            AssetFileDescriptor.UNKNOWN_LENGTH,
        )
    }

    private fun resolveAsset(uri: Uri): File? {
        val seg = uri.pathSegments
        if (seg.size != 3 || seg[0] != STICKERS_ASSET) return null
        val (_, packId, fileName) = seg
        val pack = repository.load().packs.firstOrNull { it.identifier == packId && it.canSendToWhatsApp } ?: return null
        val listed = fileName == StickerPack.TRAY_FILE || pack.stickers.any { it.file == fileName }
        if (!listed) return null
        return File(repository.packDir(pack.identifier), fileName).takeIf { it.isFile }
    }

    override fun getType(uri: Uri): String? {
        val seg = uri.pathSegments
        val authority = uri.authority
        return when {
            seg.size == 1 && seg[0] == METADATA -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
            seg.size == 2 && seg[0] == METADATA -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
            seg.size == 2 && seg[0] == STICKERS -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
            seg.size == 3 && seg[0] == STICKERS_ASSET && seg[2] == StickerPack.TRAY_FILE -> "image/png"
            seg.size == 3 && seg[0] == STICKERS_ASSET -> "image/webp"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        /** Debe coincidir con android:authorities del manifest. */
        fun authority(packageName: String) = "$packageName.stickercontentprovider"

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"

        private const val STICKER_FILE_NAME = "sticker_file_name"
        private const val STICKER_EMOJI = "sticker_emoji"
        private const val STICKER_ACCESSIBILITY_TEXT = "sticker_accessibility_text"

        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",
            "animated_sticker_pack",
        )
    }
}
