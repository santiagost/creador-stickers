package com.santi.stickers.data

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Guarda los packs en [root] (en la app, `filesDir/packs`): un index.json y una carpeta por pack
 * con los .webp y el tray.png. Lo usan la UI y el ContentProvider, que corre en hilos de Binder,
 * por eso todas las operaciones van bajo el mismo lock y leen siempre del disco.
 *
 * Orden de escritura (sección 9): al agregar, primero los archivos y después el índice;
 * al borrar, primero el índice y después los archivos. Así el índice nunca apunta a algo que no existe.
 *
 * @param trayRenderer genera el PNG 96x96 del ícono a partir del archivo de un sticker (RN-11).
 */
class PackRepository(
    private val root: File,
    private val trayRenderer: (sticker: File) -> ByteArray,
) {
    private val lock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val indexFile get() = File(root, INDEX_FILE)

    fun packDir(identifier: String) = File(root, identifier)

    fun load(): PackIndex = synchronized(lock) {
        if (!indexFile.exists()) PackIndex() else json.decodeFromString(PackIndex.serializer(), indexFile.readText())
    }

    /** RN-3 y RN-4. */
    fun canAddSticker(): Boolean = synchronized(lock) { canAdd(load()) }

    /** Suma un sticker ya codificado (WebP 512x512) al último pack, o a uno nuevo si el último está lleno (RN-3). */
    fun addSticker(webp: ByteArray): AddResult = synchronized(lock) {
        val index = load()
        if (!canAdd(index)) return AddResult.LimitReached

        val last = index.packs.lastOrNull()
        val createsPack = last == null || last.isFull
        val fileName = "s${index.nextStickerNumber}.webp"
        val target = if (createsPack) {
            val n = index.nextPackNumber
            StickerPack(identifier = "pack_$n", name = "${PackRules.PACK_NAME_PREFIX}$n", imageDataVersion = 0)
        } else {
            last
        }

        val dir = packDir(target.identifier).apply { mkdirs() }
        val stickerFile = File(dir, fileName)
        stickerFile.writeBytes(webp)
        if (createsPack) File(dir, StickerPack.TRAY_FILE).writeBytes(trayRenderer(stickerFile))

        // Un pack nuevo arranca en versión 1; uno existente sube en 1 (RN-5).
        val updated = target.copy(
            imageDataVersion = target.imageDataVersion + 1,
            stickers = target.stickers + StickerEntry(fileName),
        )
        val packs = if (createsPack) index.packs + updated else index.packs.dropLast(1) + updated
        save(
            index.copy(
                nextPackNumber = index.nextPackNumber + if (createsPack) 1 else 0,
                nextStickerNumber = index.nextStickerNumber + 1,
                packs = packs,
            ),
        )
        AddResult.Added(updated, fileName)
    }

    /** RN-10, RN-11, AC-16 a AC-19. */
    fun deleteSticker(packId: String, file: String): DeleteResult = synchronized(lock) {
        val index = load()
        val pack = index.packs.firstOrNull { it.identifier == packId } ?: return DeleteResult.NotFound
        if (pack.stickers.none { it.file == file }) return DeleteResult.NotFound

        val remaining = pack.stickers.filterNot { it.file == file }
        if (pack.everAdded && remaining.size < PackRules.MIN_STICKERS) return DeleteResult.BelowMinimum

        val dir = packDir(packId)
        if (remaining.isEmpty()) {
            // Solo llega acá un pack nunca agregado (si no, BelowMinimum): se elimina entero.
            save(index.copy(packs = index.packs.filterNot { it.identifier == packId }))
            dir.deleteRecursively()
            return DeleteResult.Deleted(pack = null)
        }

        val updated = pack.copy(imageDataVersion = pack.imageDataVersion + 1, stickers = remaining)
        save(index.copy(packs = index.packs.map { if (it.identifier == packId) updated else it }))
        File(dir, file).delete()
        if (pack.stickers.first().file == file) {
            File(dir, StickerPack.TRAY_FILE).writeBytes(trayRenderer(File(dir, remaining.first().file)))
        }
        DeleteResult.Deleted(updated)
    }

    /**
     * RN-8: WhatsApp respondió add_successful al intent enviado con [sentVersion].
     * Se guarda la versión enviada, no la actual: si se agregó un sticker mientras el diálogo estaba abierto,
     * el pack tiene que seguir "para actualizar".
     */
    fun confirmAdded(packId: String, sentVersion: Int): Unit = synchronized(lock) {
        val index = load()
        if (index.packs.none { it.identifier == packId }) return
        save(
            index.copy(
                packs = index.packs.map {
                    if (it.identifier == packId) it.copy(lastConfirmedVersion = sentVersion, everAdded = true) else it
                },
            ),
        )
    }

    private fun canAdd(index: PackIndex): Boolean {
        val last = index.packs.lastOrNull() ?: return true
        return !last.isFull || index.packs.size < PackRules.MAX_PACKS
    }

    /** Escritura atómica: temporal + rename (sección 9). */
    private fun save(index: PackIndex) {
        root.mkdirs()
        val tmp = File(root, "$INDEX_FILE.tmp")
        tmp.writeText(json.encodeToString(PackIndex.serializer(), index))
        Files.move(
            tmp.toPath(),
            indexFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private companion object {
        const val INDEX_FILE = "index.json"
    }
}
