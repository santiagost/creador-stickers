# 008 · Borrar y renombrar packs

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-17. |
| **Componentes** | App Android (`app`): repositorio de packs, ViewModel, pantalla del pack (menú). Provider y WhatsApp sin cambios. |
| **Rama** | `feature/008-Gestionar_packs` desde `main` (`2f93358`). |
| **Sigue** | Spec aparte: miniaturas animadas + línea para recorrer el tramo (idea de Santi, 2026-11-17). |

## 1. Qué problema resuelve

WhatsApp permite como mucho 10 packs por app y hoy no hay forma de borrar uno ni de cambiarle el nombre.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero borrar un pack que ya no uso para liberar el lugar.
- **HU-2** Como usuario, quiero cambiarle el nombre a un pack, también en WhatsApp.

## 3. Alcance

**Entra:** menú ⋮ en la pantalla del pack con "Renombrar" y "Borrar pack".

**No entra:** reordenar packs (Santi, 2026-11-17: el orden de creación alcanza); deshacer un borrado.

## 4. Decisiones

| # | Decisión | Quién |
|---|---|---|
| D1 | Borrar pide confirmación con el nombre y la cantidad de stickers; no se puede deshacer. Borra los archivos y libera el lugar. | Santi (2026-11-17) |
| D2 | Si el pack se agregó alguna vez a WhatsApp, la confirmación avisa que va a seguir en WhatsApp y que se quita desde ahí (prueba de abajo). | Santi (2026-11-17) |
| D3 | Renombrar: mismas reglas que al crear (1 a 128 caracteres, sin espacios de más). Si el nombre cambia, sube la versión del pack, así aparece "Actualizar en WhatsApp" si ya estaba agregado. | Santi (2026-11-17) |
| D4 | Los números internos de pack no se reutilizan (ya era así: `nextPackNumber` solo crece), para que WhatsApp no confunda un pack nuevo con uno borrado que todavía tiene. | Santi (2026-11-17) |
| D5 | Al borrar un pack también se borran carpetas de packs que ya no están en la lista (restos, como el de la prueba). | Santi (2026-11-17) |

**Verificado el 2026-11-17 en el S20 FE (WhatsApp 2.26.42.74)**, con un pack de prueba de 3 stickers agregado a WhatsApp y cambios hechos desde la PC:
- **Renombrar** + subir la versión + "Actualizar en WhatsApp": WhatsApp muestra y guarda el nombre nuevo.
- **Sacar el pack de la app:** WhatsApp lo sigue mostrando y se pueden mandar sus stickers (tiene su propia copia). Se puede quitar desde WhatsApp.

## 5. Reglas de negocio

- **RN-1** Borrar saca el pack de la lista y borra su carpeta (stickers e ícono). Los demás packs no cambian.
- **RN-2** Después de borrar, se vuelve a Inicio y se avisa "Pack borrado".
- **RN-3** Renombrar con el mismo nombre (después de quitar espacios) no cambia nada ni sube la versión.
- **RN-4** El nombre nuevo se ve en Inicio, en la pantalla del pack y, después de actualizar, en WhatsApp.

## 6. Flujo

```
Pantalla del pack ── ⋮ ──► Renombrar ──► diálogo con el nombre actual ──► Guardar
                     └──► Borrar pack ──► "¿Borrar 'Gatos' y sus 12 stickers? No se puede deshacer."
                                           (+ "Va a seguir en WhatsApp: quitalo desde ahí." si se agregó alguna vez)
                                           ──► Borrar ──► Inicio
```

## 7. Criterios de aceptación

- **AC-1** Dado un pack, cuando toco ⋮ → Borrar pack y confirmo, entonces desaparece de Inicio, se borran sus archivos y puedo crear otro pack en su lugar.
- **AC-2** Dado un pack que se agregó a WhatsApp, la confirmación avisa que sigue en WhatsApp.
- **AC-3** Si cancelo la confirmación, no cambia nada.
- **AC-4** Cuando renombro un pack, el nombre nuevo se ve en Inicio y en su pantalla; si estaba en WhatsApp, aparece "Actualizar en WhatsApp" y, al actualizar, WhatsApp muestra el nombre nuevo.
- **AC-5** Un nombre vacío o de más de 128 caracteres no se acepta; el mismo nombre no cambia nada.
- **AC-6** Un pack creado después de borrar otro tiene un número interno nuevo (no reutiliza el del borrado).

## 8. Seguridad y privacidad

Sin permisos nuevos. Se borran solo archivos dentro de la carpeta de packs de la app.

## 9. Modelo de datos

Sin campos nuevos. Cambia el repositorio: `deletePack(packId)` y `renamePack(packId, name)`.

## 10. Interfaces

```kotlin
sealed interface DeletePackResult { data class Deleted(val pack: StickerPack); data object NotFound }
fun deletePack(packId: String): DeletePackResult
sealed interface RenameResult { data class Renamed(val pack: StickerPack); data object Unchanged; data object InvalidName; data object NotFound }
fun renamePack(packId: String, name: String): RenameResult
```

## 11. Pantallas

- **Pack:** ⋮ en la barra de arriba, con "Renombrar" y "Borrar pack".
- **Renombrar (diálogo):** campo con el nombre actual; "Cancelar" / "Guardar".
- **Borrar (diálogo):** el texto de D1/D2; "Cancelar" / "Borrar".

## 12. Plan de trabajo

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P1 | Repositorio: `deletePack`, `renamePack`, limpieza de restos (D5). | Unitarios. | AC-1, AC-5, AC-6 (lógica) |
| P2 | ViewModel y pantalla (menú y diálogos). Un commit cuando Santi confirme. | Manual con los ACs. | AC-1 a AC-4 |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Borrar por error. | Confirmación con nombre y cantidad (D1). |
| El pack borrado sigue en WhatsApp y confunde. | Aviso en la confirmación (D2). |

## 14. Preguntas abiertas

Ninguna.

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-17 | "Dale, vamos con packs. […] le agregaria la linea entre medio del bloque elegible para ir moviendo hacia adelante o atras para ver que parte del video contiene" | Esta spec; la línea va a la spec de miniaturas. |
| 2026-11-17 | "Sí, todas" (las 6 recomendaciones) | D1 a D4; reordenar fuera. |
| 2026-11-17 | Prueba: "Nombre nuevo" / "Sigue ahi el pack y puedo enviar los stickers, lo puedo borrar desde WA" | Sección 4 (verificado) y D2. |
| 2026-11-17 | "Sí, aprobada" (con D5 como estaba) | Estado: aprobada; D5 confirmada. |
| 2026-11-17 | Prueba de P2: "Lo estuve probando y la verdad sin problemas hasta ahora" | P2 commiteada. |
