package com.santi.stickers.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import com.santi.stickers.provider.StickerContentProvider

/**
 * Integración con WhatsApp común (D7: Business queda fuera). Contrato tomado del repo oficial
 * WhatsApp/stickers y verificado en el spike (Anexo A de la spec 001).
 */
object WhatsApp {
    const val PACKAGE = "com.whatsapp"
    const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    const val EXTRA_PACK_ID = "sticker_pack_id"
    const val EXTRA_AUTHORITY = "sticker_pack_authority"
    const val EXTRA_PACK_NAME = "sticker_pack_name"

    private const val TAG = "WhatsApp"
    private const val WHITELIST_AUTHORITY = "$PACKAGE.provider.sticker_whitelist_check"

    /** Necesita `<queries>` con com.whatsapp en el manifest (Android 11+). */
    @Suppress("DEPRECATION") // la variante con PackageInfoFlags es de API 33 y minSdk es 30
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * RN-9: le pregunta a WhatsApp si tiene el pack. Es una llamada entre procesos: no usarla en el hilo principal.
     * Si WhatsApp no responde (no instalado, versión vieja), se considera que no lo tiene.
     */
    fun isWhitelisted(context: Context, packId: String): Boolean {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(WHITELIST_AUTHORITY)
            .appendPath("is_whitelisted")
            .appendQueryParameter("authority", StickerContentProvider.authority(context.packageName))
            .appendQueryParameter("identifier", packId)
            .build()
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow("result")) == 1
            } ?: false
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo consultar la whitelist de $packId", e)
            false
        }
    }
}

/** Qué se le manda a WhatsApp. [version] es la que se confirma si responde que sí (RN-8). */
data class AddPackRequest(val packId: String, val packName: String, val version: Int)

/** Agregar o actualizar un pack: es el mismo intent (D8). */
class AddStickerPackContract : ActivityResultContract<AddPackRequest, AddPackOutcome>() {

    override fun createIntent(context: Context, input: AddPackRequest): Intent =
        Intent(WhatsApp.ACTION_ENABLE_STICKER_PACK)
            .setPackage(WhatsApp.PACKAGE)
            .putExtra(WhatsApp.EXTRA_PACK_ID, input.packId)
            .putExtra(WhatsApp.EXTRA_AUTHORITY, StickerContentProvider.authority(context.packageName))
            .putExtra(WhatsApp.EXTRA_PACK_NAME, input.packName)

    override fun parseResult(resultCode: Int, intent: Intent?): AddPackOutcome =
        AddPackOutcome.from(
            resultCode = resultCode,
            addSuccessful = intent?.takeIf { it.hasExtra("add_successful") }?.getBooleanExtra("add_successful", false),
            validationError = intent?.getStringExtra("validation_error"),
        )
}
