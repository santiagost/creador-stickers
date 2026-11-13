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

    /** Spec 004, RN-1: hasta 10 packs en total (límite de WhatsApp). */
    fun canCreatePack(): Boolean = synchronized(lock) { load().packs.size < PackRules.MAX_PACKS }

    /** Spec 004, D2: "Mis stickers N" o "Mis animados N", con N el número que va a tener el pack. */
    fun suggestedName(animated: Boolean): String = synchronized(lock) {
        val prefix = if (animated) PackRules.ANIMATED_PACK_NAME_PREFIX else PackRules.PACK_NAME_PREFIX
        "$prefix${load().nextPackNumber}"
    }

    /** Spec 004: crea un pack vacío al final de la lista (D5). */
    fun createPack(name: String, animated: Boolean): CreatePackResult = synchronized(lock) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > PackRules.MAX_NAME_LENGTH) return CreatePackResult.InvalidName
        val index = load()
        if (index.packs.size >= PackRules.MAX_PACKS) return CreatePackResult.LimitReached
        val pack = StickerPack(
            identifier = "pack_${index.nextPackNumber}",
            name = clean,
            imageDataVersion = 0, // sube a 1 con el primer sticker
            animated = animated,
        )
        save(index.copy(nextPackNumber = index.nextPackNumber + 1, packs = index.packs + pack))
        CreatePackResult.Created(pack)
    }

    /**
     * Spec 004, RN-2: suma un sticker ya codificado (WebP 512x512) al pack [packId]. El que llama se asegura
     * de que el tipo coincida (foto para packs normales, video para animados). Sube la versión (RN-5 de la 001).
     */
    fun addSticker(packId: String, webp: ByteArray): AddResult = synchronized(lock) {
        val index = load()
        val target = index.packs.firstOrNull { it.identifier == packId } ?: return AddResult.NotFound
        if (target.isFull) return AddResult.PackFull // RN-3

        val fileName = "s${index.nextStickerNumber}.webp"
        val dir = packDir(target.identifier).apply { mkdirs() }
        val stickerFile = File(dir, fileName)
        stickerFile.writeBytes(webp)
        // El ícono sale del primer sticker (RN-11 de la 001); también si el pack había quedado vacío.
        if (target.stickers.isEmpty()) File(dir, StickerPack.TRAY_FILE).writeBytes(trayRenderer(stickerFile))

        val updated = target.copy(
            imageDataVersion = target.imageDataVersion + 1,
            stickers = target.stickers + StickerEntry(fileName),
        )
        save(
            index.copy(
                nextStickerNumber = index.nextStickerNumber + 1,
                packs = index.packs.map { if (it.identifier == packId) updated else it },
            ),
        )
        AddResult.Added(updated, fileName)
    }

    /** RN-10, RN-11, AC-16 a AC-19 (spec 001); AC-7 de la 004: un pack que queda vacío no se elimina. */
    fun deleteSticker(packId: String, file: String): DeleteResult = synchronized(lock) {
        val index = load()
        val pack = index.packs.firstOrNull { it.identifier == packId } ?: return DeleteResult.NotFound
        if (pack.stickers.none { it.file == file }) return DeleteResult.NotFound

        val remaining = pack.stickers.filterNot { it.file == file }
        if (pack.everAdded && remaining.size < PackRules.MIN_STICKERS) return DeleteResult.BelowMinimum

        val dir = packDir(packId)
        val updated = pack.copy(imageDataVersion = pack.imageDataVersion + 1, stickers = remaining)
        save(index.copy(packs = index.packs.map { if (it.identifier == packId) updated else it }))
        File(dir, file).delete()
        when {
            remaining.isEmpty() -> File(dir, StickerPack.TRAY_FILE).delete() // se regenera con el próximo sticker
            pack.stickers.first().file == file ->
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
