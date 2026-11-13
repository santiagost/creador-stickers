# 004 · Packs independientes

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-13. |
| **Componentes** | App Android (`app`): repositorio de packs, pantallas de Inicio y de pack. Provider y WhatsApp sin cambios. |
| **Rama** | `feature/004-Packs_independientes` desde `main` (`5f43c2b`). |
| **Sigue** | Spec 005 (tramo con caja + GIF), aparte. |

## 1. Qué problema resuelve

Hoy la app decide sola a qué pack va cada sticker (el último de su tipo, y crea otro al llenarse). Santi quiere armar packs a propósito, normales o animados, y agregar stickers a cada uno desde adentro del pack.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero crear un pack eligiendo si es normal o animado y ponerle nombre.
- **HU-2** Como usuario, quiero entrar a un pack y desde ahí crear stickers, borrarlos y agregarlo/actualizarlo en WhatsApp.

## 3. Alcance

**Entra:** Inicio como lista de packs; "+ Nuevo pack" (tipo + nombre); pantalla de pack con "+ Nuevo sticker"; se elimina el armado automático de packs.

**No entra:** borrar un pack entero, renombrar o reordenar packs (backlog, Santi 2026-11-10); GIF y tramo con caja (spec 005).

## 4. Decisiones

| # | Decisión | Quién |
|---|---|---|
| D1 | Se elimina el armado automático: cada sticker se crea desde un pack y va a ese pack. | Santi (2026-11-10) |
| D2 | Al crear un pack se elige tipo (normal / animado) y nombre; el nombre viene sugerido ("Mis stickers N" / "Mis animados N") y se puede cambiar. No vacío, hasta 128 caracteres (límite de WhatsApp). | Santi (2026-11-13) |
| D3 | Borrar, renombrar y reordenar packs quedan para después. | Santi (2026-11-10) |
| D4 | Un pack puede quedar vacío o con menos de 3: ya no se elimina solo al borrar su último sticker (cambia RN-10/AC-18 de la spec 001). Mientras tenga menos de 3, no se le muestra a WhatsApp (igual que hoy). | Santi (2026-11-13) |
| D5 | El orden de los packs en Inicio es el de creación (el más nuevo abajo). | Santi (2026-11-13) |
| D6 | En un pack animado, "Nuevo sticker" abre el selector de videos (el GIF llega con la spec 005). | Santi (2026-11-13) |

## 5. Reglas de negocio

- **RN-1** Se puede crear un pack si hay menos de 10 (límite de WhatsApp, compartido entre tipos).
- **RN-2** Un sticker nuevo va al pack desde el que se creó; tiene que ser del mismo tipo que el pack.
- **RN-3** Con 30 stickers, "Nuevo sticker" se deshabilita y se avisa que el pack está lleno.
- **RN-4** Siguen igual: versión del pack, mínimo de 3 para WhatsApp, agregar/actualizar, borrar con mínimo de 3 en packs ya agregados.
- **RN-5** Los packs existentes se conservan con su nombre, tipo, stickers y estado en WhatsApp.

## 6. Flujo

```
Inicio (lista de packs) ── "+ Nuevo pack" ──► diálogo: tipo + nombre ──► pantalla del pack nuevo
      │
      └─ tocar un pack ──► Pantalla del pack ── "+ Nuevo sticker" ──► foto (normal) / video (animado)
                               │                                     └─► encuadre/tramo ──► vuelve al pack
                               ├─ "Agregar/Actualizar en WhatsApp"
                               ├─ mantener apretado ──► borrar sticker
                               └─ volver ──► Inicio
```

## 7. Criterios de aceptación

- **AC-1** Sin packs, Inicio muestra "Creá tu primer pack".
- **AC-2** Con "+ Nuevo pack" elijo tipo y nombre (sugerido); al crear, entro a la pantalla del pack, vacía.
- **AC-3** Con 10 packs, "+ Nuevo pack" avisa que se llegó al máximo y no crea nada.
- **AC-4** Inicio muestra cada pack con nombre, tipo, cantidad (N/30) y estado en WhatsApp; tocarlo abre su pantalla.
- **AC-5** En un pack normal, "+ Nuevo sticker" abre fotos; en uno animado, videos. Al guardar, el sticker queda en ese pack y vuelvo a su pantalla.
- **AC-6** Con 30 stickers, "+ Nuevo sticker" no deja crear y avisa.
- **AC-7** Borrar el último sticker de un pack nunca agregado lo deja vacío (no lo elimina).
- **AC-8** Agregar/actualizar en WhatsApp y borrar con mínimo de 3 funcionan igual que antes, desde la pantalla del pack.
- **AC-9** Los packs que ya tenía siguen ahí, con sus stickers y su estado.

## 8. Seguridad y privacidad

Sin cambios: sin permisos nuevos; el nombre del pack se guarda en `index.json` y lo lee WhatsApp por el provider (como hoy).

## 9. Modelo de datos

Sin campos nuevos: `StickerPack` ya tiene `name` y `animated`. Cambia el repositorio: `createPack(name, animated)` y `addSticker(packId, webp)`; desaparece la elección automática del pack. Sin migración.

## 10. Interfaces

```kotlin
sealed interface CreatePackResult { data class Created(val pack: StickerPack); data object LimitReached; data object InvalidName }
fun createPack(name: String, animated: Boolean): CreatePackResult
fun addSticker(packId: String, webp: ByteArray): AddResult   // Added / PackFull / NotFound
fun suggestedName(animated: Boolean): String
```

## 11. Pantallas

- **Inicio:** lista de packs (miniatura del primer sticker, nombre, "animado" si corresponde, N/30, estado en WhatsApp). Botón "+ Nuevo pack". Estado vacío: "Creá tu primer pack".
- **Nuevo pack (diálogo):** "Normal" / "Animado" y campo de nombre con el sugerido; "Cancelar" / "Crear".
- **Pack:** barra con volver y el nombre; botón de WhatsApp; grilla de stickers (mantener apretado para borrar); botón "+ Nuevo sticker".

## 12. Plan de trabajo

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P1 | Repositorio: crear pack, agregar a un pack, packs que no se eliminan solos; ViewModel y pantallas (van juntos porque el ViewModel no compila con el repositorio nuevo sin la UI nueva). Un solo commit cuando Santi confirme la UI. | Unitarios (incluye los de las specs 001 y 003 que cambian). | AC-3, AC-6, AC-7, AC-9 (lógica) |
| P2 | UI: Inicio, diálogo de nuevo pack, pantalla de pack. | Manual guiada por los ACs. | AC-1 a AC-8 |
| P3 | Verificación completa y cierre. | Todos los tests + guion. | Todos |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Cambian reglas probadas en las specs 001 y 003 (RN-3, AC-6/AC-7/AC-18 de la 001; RN-6, AC-8 de la 003). | Se actualizan esos tests en P1 y se anota en esta spec qué reglas reemplaza. |

## 14. Preguntas abiertas

Ninguna.

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-10 | "Empecemos con lo que quedo pendiente. Primero que nada el crear packs independientes ya sea animados o normales, para crear un nuevo sticker se tiene que entrar al pack y ahi esta el boton nuevo sticker." | Esta spec. |
| 2026-11-10 | "Igual aca vayamos mas rapido." / P1: "Si" / P2: "Si, lo eliminamos" / P3: "Si" / P4: "Bueno, lo dejamos para despues" | Spec más corta; dos specs (004 y 005); D1, D2, D3. |
| 2026-11-10 | P5: "Maximo 10s y minimo 2s" / P6: "Dale, hacemos que permita selector entre gif y videos" | Para la spec 005. |
| 2026-11-13 | "Sí, aprobada" (con D2, D4, D5 y D6 como estaban) | Estado: aprobada; D2, D4, D5 y D6 confirmadas. |
| 2026-11-13 | Después de probarlo: "Todo bien, commiteá" | Implementación (P1 y P2) commiteada. Verificado: 58 tests unitarios, 35 instrumentados en el S20 FE, y los 2 packs existentes de Santi aparecen bien en la lista nueva (captura). |
