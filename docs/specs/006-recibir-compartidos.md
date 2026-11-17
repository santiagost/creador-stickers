# 006 · Recibir fotos, videos y GIF compartidos

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-17. |
| **Componentes** | App Android (`app`): manifest (destino de "Compartir"), `MainActivity`, ViewModel, pantalla nueva para elegir pack. Packs, provider, WhatsApp, tramo y encuadre sin cambios. |
| **Rama** | `feature/006-Recibir_compartidos` desde `main` (`e8a59f7`). |
| **Sigue** | Parte 2: links de X (estilo Snaptube), en otra spec. |

## 1. Qué problema resuelve

Hoy para hacer un sticker hay que entrar a la app, al pack y elegir el archivo. Santi quiere hacerlo desde otra app: "Compartir → Stickers" con una foto, un video o un GIF.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero compartir una foto, un video o un GIF desde otra app (galería, WhatsApp, Telegram…) a la app de stickers, elegir el pack y seguir con el encuadre o el tramo.

## 3. Alcance

**Entra:** la app aparece en "Compartir" para imágenes y videos; elegir el pack (del tipo que corresponde) o crear uno; seguir con el flujo que ya existe.

**No entra:** links (texto) — no se recibe texto, así la app no aparece al compartir un link (parte 2); varios stickers de una vez (backlog).

## 4. Decisiones

| # | Decisión | Quién |
|---|---|---|
| D1 | Se reciben fotos (→ pack normal), videos y GIF (→ pack animado). | Santi (2026-11-17) |
| D2 | Al recibir, se muestra la lista de packs del tipo que corresponde para elegir a cuál va. | Santi (2026-11-17) |
| D3 | En esa lista está "+ Nuevo pack", con el tipo ya elegido (sirve si no hay packs de ese tipo o están llenos). | Santi (2026-11-17) |
| D4 | Si se comparten varios archivos, se usa el primero y se avisa. | Santi (2026-11-17) |
| D5 | No se recibe texto (links): queda para la parte 2. | Santi (2026-11-17) |
| D6 | Compartir abre la app en una pantalla nueva sobre la app que comparte (comportamiento estándar de Android): al terminar o cancelar, "atrás" vuelve a esa app. | Santi (2026-11-17) |

**Verificado (2026-11-17, logcat en el S20 FE):** la app de X, al compartir un tweet con video (con o sin texto), manda solo el link como texto (`text/plain`), nunca el archivo. Por eso los links de X van en la parte 2.

## 5. Reglas de negocio

- **RN-1** El tipo sale del archivo (lo que informa el sistema para ese archivo; si no informa nada, lo que dice quien comparte): `image/gif` → GIF; `image/*` → foto; `video/*` → video. Cualquier otra cosa: "Ese archivo no sirve para stickers."
- **RN-2** La lista muestra solo packs del tipo que corresponde; los llenos (30) se ven pero no se pueden elegir.
- **RN-3** Elegido el pack, sigue igual que desde "+ Nuevo sticker": foto → encuadre; video/GIF → tramo → encuadre. Al guardar, vuelve a la pantalla de ese pack.
- **RN-4** Si con "+ Nuevo pack" se llega al máximo de 10 packs, se avisa como hoy y se sigue en la lista.
- **RN-5** El archivo compartido se procesa una sola vez (girar el teléfono no lo vuelve a abrir).

## 6. Flujo

```
Otra app ── Compartir ──► Stickers
   ──► "¿A qué pack?" [packs del tipo] [+ Nuevo pack]
   ──► foto: encuadre │ video/GIF: tramo ──► encuadre
   ──► guardar ──► pantalla del pack (atrás: Inicio; desde Inicio, atrás: la app que compartió)
```

## 7. Criterios de aceptación

- **AC-1** Dado una foto en la galería, cuando la comparto, entonces "Stickers" aparece en la lista de apps.
- **AC-2** Dado un video o un GIF, cuando lo comparto a Stickers, entonces veo solo mis packs animados; con una foto, solo los normales.
- **AC-3** Cuando elijo un pack, entonces sigo con el tramo (video/GIF) o el encuadre (foto), y al guardar el sticker queda en ese pack.
- **AC-4** Dado que no tengo packs de ese tipo (o están llenos), cuando toco "+ Nuevo pack", entonces lo creo con el tipo ya elegido y el sticker va a ese pack nuevo.
- **AC-5** Un pack lleno aparece pero no se puede elegir.
- **AC-6** Si comparto varios archivos, se usa el primero y se avisa.
- **AC-7** Si comparto un link (texto), Stickers no aparece en la lista.
- **AC-8** Si comparto algo que no es foto, video ni GIF, o un archivo que no se puede abrir, se avisa y no se rompe la app.
- **AC-9** Girar el teléfono en la lista de packs no pierde ni repite lo compartido.

## 8. Seguridad y privacidad

Sin permisos nuevos: quien comparte da acceso de lectura solo a ese archivo, mientras dure la pantalla. La app sigue sin internet. Se recibe contenido de cualquier app: se trata como cualquier archivo del selector (se valida el tipo y que se pueda decodificar; los errores se avisan, RN-1 y AC-8).

## 9. Modelo de datos

Sin cambios.

## 10. Interfaces

```kotlin
/** Qué se puede hacer con un archivo según su tipo (RN-1). Sin Android, con tests en la PC. */
enum class SharedKind { PHOTO, VIDEO, GIF }
fun sharedKind(mime: String?): SharedKind?

// ViewModel
fun onShared(uri: Uri, mime: String?, more: Boolean)   // abre la lista de packs (una sola vez, RN-5)
fun chooseSharedPack(packId: String)                    // sigue con el tramo o el encuadre
fun createPackForShared(name: String)                   // D3
```

## 11. Pantallas

**¿A qué pack?:** barra con "Cancelar" y el título "¿A qué pack?"; la lista de packs del tipo (como en Inicio, los llenos atenuados con "lleno"); botón "+ Nuevo pack" que abre el diálogo de siempre con el tipo fijo.

## 12. Plan de trabajo

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P1 | `sharedKind` + filtro de packs elegibles. | Unitarios. | RN-1, RN-2 |
| P2 | Manifest, `MainActivity` (leer el intent una vez), ViewModel y pantalla "¿A qué pack?". Un commit cuando Santi confirme la UI. | Manual guiada por los ACs (galería, WhatsApp, Telegram si lo tenés), más los tests de siempre. | AC-1 a AC-9 |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Algunas apps mandan un tipo genérico o equivocado. | RN-1: primero el tipo que informa el sistema para el archivo. |
| El acceso al archivo dura lo que dura la pantalla. | Se lee mientras la pantalla está abierta, como con el selector (no verificado con todas las apps). |

## 14. Preguntas abiertas

Ninguna.

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-17 | "creo que no se manda el video deberiamos probar pero sino una forma como snaptube que si descargar videos o gifs si se los comparte desde el link de X. Vamos primero con la parte 1" | Esta spec; links de X a la parte 2. Se probó qué manda X (sección 4). |
| 2026-11-17 | "Dale te apruebo todas" (las 6 recomendaciones) | D1 a D5. |
| 2026-11-17 | "Sí, aprobada" (con D6 como estaba) | Estado: aprobada; D6 confirmada. |
