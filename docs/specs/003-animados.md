# 003 · Stickers animados desde video

| | |
|---|---|
| **Estado** | Implementada y verificada el 2026-11-10 (sección 16). Aprobada por Santi el 2026-11-10. |
| **Componentes** | App Android (`app`): modelo de packs, provider, nuevo módulo de video y WebP animado, pantallas de Inicio, tramo y encuadre. |
| **Hoja de ruta** | Parte 3 de 3 (1: estáticos + WhatsApp; 2: quitar fondo; 3: animados). |
| **Rama** | `feature/003-Animados` desde `main` (`04a64d9`). |
| **Evidencia** | Spike en el S20 FE del 2026-11-09/10 (Anexo A). |

---

## 1. Qué problema resuelve

Hoy la app solo hace stickers quietos. Se quiere poder convertir un video corto del teléfono en un sticker animado y tenerlo en WhatsApp con el mismo flujo de la parte 1: elegir, encuadrar, guardar y agregar/actualizar el pack. Android no trae un encoder de WebP animado, así que hace falta una librería.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero elegir un video, marcar desde dónde arranca el tramo y encuadrarlo, para guardarlo como sticker animado.
- **HU-2** Como usuario, quiero que mis stickers animados se acumulen en sus propios packs y poder agregarlos y actualizarlos en WhatsApp como los estáticos.

## 3. Alcance

**Entra**
- "Nuevo sticker" ofrece "Foto" o "Video".
- Elegir un video con el selector del sistema (sin permisos).
- Pantalla de tramo: elegir el inicio del tramo viendo el cuadro donde arranca.
- Encuadre con la misma pantalla de siempre (imán incluido), sobre el primer cuadro del tramo.
- Generación del WebP animado 512×512 ≤ 500 KB con progreso, bajando cuadros por segundo y calidad si hace falta.
- Packs animados separados de los estáticos, con agregar/actualizar/borrar como en la parte 1.

**No entra (y adónde va)**
- GIF → backlog (Santi quiere GIF; Android no da los cuadros de un GIF, hace falta otra librería).
- Elegir el largo del tramo → próxima versión (D2).
- Ver los animados en movimiento en Inicio → más adelante (D12).
- Quitar fondo en animados → backlog (ML Kit procesa solo imágenes sueltas; ~300 ms por cuadro).
- Texto o dibujo animado, velocidad, reversa, bumerán → backlog.
- Sonido: WhatsApp no lo usa.
- Encoder compatible con páginas de 16 KB (requisito de Play Store) → antes de publicar (D4, riesgo).
- Rediseño de UI → sigue pendiente (spec 001).

## 4. Decisiones

| # | Decisión | Motivo | Quién |
|---|---|---|---|
| D1 | Solo videos como fuente en esta parte. | Android lee los cuadros de un video (`MediaMetadataRetriever`); los de un GIF no. Los "GIF" de WhatsApp y del teclado son MP4. | Santi (2026-11-09) |
| D2 | Tramo fijo de 3 s en esta versión; elegir el largo queda para la siguiente. | Con 3 s a 15 fps los stickers del spike pesaron 204–363 KB (bajo 500 KB al primer intento). | Santi (2026-11-09 y 2026-11-10) |
| D3 | Quitar fondo no está disponible para video. | Ver "No entra". | Santi (2026-11-09) |
| D4 | Encoder `com.aureusapps.android:webp-android:1.1.2` (libwebp por JNI, MIT). | Funcionó en el spike y WhatsApp aceptó y actualizó el pack. No agrega permisos. **Sus librerías nativas están alineadas a 4 KB, no a 16 KB**: para publicar en Play Store habrá que compilar libwebp con el NDK. | Santi (2026-11-09) |
| D5 | Los cuadros se leen por índice con `MediaMetadataRetriever.getFramesAtIndex` (si falla, por tiempo con `getScaledFrameAtTime`) y se convierten a `ARGB_8888` en memoria normal antes de pasarlos al encoder. | Por índice tardó 0,6–1,3 s por tramo contra ~10 s por tiempo. La primera versión del spike se cerraba al codificar con cuadros sin convertir (causa probable, no confirmada). | Santi (2026-11-10) |
| D6 | Cuadros a 15 fps; si el resultado pasa de 500 KB, se reintenta con (fps, calidad) = (15, 70) → (12, 60) → (10, 50) → (10, 40) → (8, 35) → (8, 25). Si ninguno entra, se avisa. | Escalera usada en el spike; en todos los casos entró al primer paso. | Santi (2026-11-10) |
| D7 | Encuadre: la misma pantalla y el mismo cálculo de la parte 1/2, sobre el primer cuadro del tramo; ese encuadre se aplica a todos los cuadros. El botón "Quitar fondo" no aparece para video. | Reutiliza todo lo hecho; el primer cuadro importa porque WhatsApp termina la animación en él. | Santi (2026-11-10) |
| D8 | Packs animados separados ("Mis animados 1", "Mis animados 2"…) y en la misma lista de Inicio, con la etiqueta "animado". El límite de 10 packs lo comparten estáticos y animados. | WhatsApp no deja mezclar tipos en un pack. Su README dice "de 1 a 10 packs" por app, sin distinguir tipos (no verificado si lo hace cumplir). | Santi (2026-11-10) |
| D9 | El ícono de un pack animado es el primer cuadro de su primer sticker, en PNG 96×96. | `ImageDecoder` lo saca bien del WebP animado (spike: 96×96, 13–14 KB). | Santi (2026-11-10) |
| D10 | El modelo suma `animated: Boolean = false` a cada pack. Los packs existentes quedan como estáticos, sin migración. | Cambio aditivo; `index.json` ya ignora/completa campos. | Santi (2026-11-10) |
| D11 | El spike (`spike-animados/`) no se versiona; se borra al terminar la parte 3 (con OK de Santi). | Igual que en las partes anteriores. | Santi (2026-11-10) |
| D12 | En Inicio, las miniaturas de los animados se ven quietas (primer cuadro); verlas en movimiento queda para más adelante. | Mantener esta parte chica y la lista liviana. | Santi (2026-11-10) |
| D13 | Los tests instrumentados de video usan el video de Santi en `app/src/androidTest/assets/video/prueba.mp4` (H.264, 1280×720, 60 fps, 4,2 s, 440 KB; queda en el repo de GitHub). | Hace falta un video real para probar la lectura de cuadros; además tiene más resolución y fps que los del spike. En P4 se sumó `prueba-vertical.mp4`, el mismo video con metadata de rotación, para probar videos verticales. | Santi (2026-11-10) |

## 5. Reglas de negocio

- **RN-1** Un video genera un sticker animado.
- **RN-2** El tramo empieza donde elige el usuario y dura 3 s (D2); si el video dura menos, se usa hasta el final. Hace falta al menos 2 cuadros (WhatsApp); un video de menos de 0,2 s no se puede usar.
- **RN-3** Cada cuadro se encuadra con el mismo encuadre elegido sobre el primero y se dibuja a 512×512 sobre fondo transparente (mismo render que la parte 1).
- **RN-4** El WebP animado se repite sin fin, con cuadros de igual duración (66 ms a 15 fps; nunca menos de 8 ms) y dura como máximo 10 s.
- **RN-5** Si después de la escalera de D6 sigue pesando más de 500 KB, no se guarda y se avisa "No entra en el tamaño de WhatsApp. Probá con otro tramo".
- **RN-6** Un sticker animado va al último pack **animado** si tiene menos de 30; si no, se crea un pack animado nuevo. Lo mismo para estáticos con packs estáticos. Nunca se mezclan.
- **RN-7** El límite de 10 packs es en total (D8). Si se llegó, no se puede crear un pack nuevo de ningún tipo.
- **RN-8** El provider informa `animated_sticker_pack = 1` en los packs animados y `0` en los estáticos.
- **RN-9** Todas las reglas de la parte 1 sobre mínimo de 3, versión, agregar/actualizar y borrar valen igual para packs animados.

## 6. Flujo

```
Inicio ── "+ Nuevo sticker" ──► ¿Foto o Video?
                                   ├─ Foto ──► (flujo de la parte 1/2)
                                   └─ Video ──► selector de videos
                                                  │
                                                  ▼
                                         Tramo: deslizar el inicio, ver el cuadro (RN-2)
                                                  │ Siguiente
                                                  ▼
                                         Encuadre sobre el primer cuadro (D7, sin "Quitar fondo")
                                                  │ Guardar
                                                  ▼
                                   "Generando sticker… N %" (leer cuadros → escalera D6)
                                        ├─ entra ──► último pack animado / pack animado nuevo (RN-6)
                                        └─ no entra ──► mensaje (RN-5), vuelve al encuadre
```

## 7. Criterios de aceptación

**Video y tramo**
- **AC-1** Cuando toco "+ Nuevo sticker", puedo elegir entre "Foto" y "Video"; con "Video" se abre el selector mostrando solo videos.
- **AC-2** Dado un video elegido, cuando deslizo el inicio del tramo, veo el cuadro donde arranca.
- **AC-3** Dado un video más corto que 3 s, el tramo es el video entero.
- **AC-4** Cancelar el selector, el tramo o el encuadre vuelve a Inicio sin cambios.

**Generación**
- **AC-5** Cuando guardo, veo "Generando sticker…" con progreso y no puedo tocar otra cosa hasta que termine o cancele.
- **AC-6** El sticker resultante es WebP animado 512×512, ≤ 500 KB, con 2 o más cuadros de igual duración ≥ 8 ms, ≤ 10 s, repetición infinita, y su primer cuadro es el primer cuadro del tramo encuadrado.
- **AC-7** Si no entra en 500 KB con ninguna combinación, veo el mensaje de RN-5 y no se guarda nada.

**Packs**
- **AC-8** El primer sticker animado crea "Mis animados 1" con la etiqueta "animado"; los estáticos siguen yendo a "Mis stickers N".
- **AC-9** Con 10 packs (de cualquier tipo) y el último del tipo lleno, no se puede crear otro y se avisa.
- **AC-10** El provider devuelve `animated_sticker_pack = 1` para packs animados y `0` para estáticos.
- **AC-11** El ícono de un pack animado es PNG 96×96 ≤ 50 KB sacado de su primer sticker.
- **AC-12** Agregar y actualizar un pack animado en WhatsApp funciona igual que uno estático, y el sticker se mueve en el chat (prueba manual).
- **AC-13** Los packs que ya existían siguen funcionando como estáticos.

## 8. Seguridad, permisos y privacidad

- Sin permisos nuevos: el selector del sistema da acceso solo al video elegido. Verificado en el spike que `webp-android` no agrega permisos al manifest; se revisa de nuevo en el manifest final de la app.
- El video se lee una vez; no se copia ni se guarda. Solo queda el WebP final (como D13 de la spec 001).
- Todo el procesamiento es en el teléfono.

## 9. Modelo de datos

`StickerPack` suma `animated: Boolean = false` (D10). Cambio aditivo: los `index.json` existentes se leen igual y sus packs quedan estáticos. `schemaVersion` sigue en 1 (no hace falta migrar nada).

## 10. Interfaces

```kotlin
/** D5: lee los cuadros de un tramo del video, ya en ARGB_8888. */
object VideoFrames {
    fun info(context: Context, uri: Uri): VideoInfo           // duración, tamaño, cuadros por segundo
    fun frameAt(context: Context, uri: Uri, timeMs: Long): Bitmap  // para la pantalla de tramo
    fun extract(context: Context, uri: Uri, startMs: Long, lengthMs: Long, fps: Int, onProgress: (Float) -> Unit): List<Bitmap>
}

/** D6: arma el WebP animado bajando fps y calidad hasta ≤ 500 KB. */
object AnimatedWebp {
    fun encode(frames: List<Bitmap>, crop: CropTransform, onProgress: (Float) -> Unit): ByteArray?  // null = no entra
}
```

`PackRepository.addSticker(webp, animated)` y `canAddSticker(animated)` reciben el tipo (RN-6, RN-7).

## 11. Pantallas

- **Inicio:** el botón "+ Nuevo sticker" abre un menú chico "Foto" / "Video". Los packs animados muestran "animado" junto al nombre; sus miniaturas son el primer cuadro, quietas (D12).
- **Tramo (nueva):** el cuadro donde arranca el tramo, una barra para mover el inicio, el texto "Tramo de 3 s desde 0:01,5" y los botones "Cancelar" / "Siguiente".
- **Encuadre:** la misma de siempre, sin "Quitar fondo" para video.
- **Generando:** sobre el encuadre, "Generando sticker… N %" con "Cancelar".

## 12. Plan de trabajo (un commit por parte, tests primero)

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P0 | Dependencia `webp-android`, revisar el manifest final. | Build, tests y lint en verde; sin permisos nuevos. | — |
| P1 | Packs animados en el modelo y el repositorio; flag en el provider; ícono desde WebP animado. | Unitarios (repositorio) e instrumentados (provider, ícono). | AC-8 a AC-11, AC-13 |
| P2 | `VideoFrames` y `AnimatedWebp`. | Instrumentados: codificar cuadros sintéticos y validar el archivo (cuadros, duración, repetición, tamaño); leer cuadros del video de prueba (D13). | AC-3, AC-6, AC-7 |
| P3 | UI: menú Foto/Video, pantalla de tramo, encuadre sin "Quitar fondo", progreso, etiqueta "animado". | Prueba manual guiada por los ACs. | AC-1 a AC-5, AC-12 |
| P4 | Auditoría, verificación completa, cierre de la spec, borrar el spike (con OK). | Todos los tests + guion manual. | Todos |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| `webp-android` no está alineada a 16 KB: no sirve para publicar en Play Store ni para teléfonos con páginas de 16 KB. | Aceptado para uso personal; antes de publicar, compilar libwebp con el NDK (queda en "No entra"). |
| `webp-android` tiene poca actividad (1.1.2, hace ~1 año, 22 estrellas). | Queda aislada detrás de `AnimatedWebp`; se puede cambiar por libwebp propio. |
| La causa del cierre de la primera versión del spike no está confirmada. | D5: siempre convertir a `ARGB_8888`; test instrumentado con varios formatos de entrada. |
| Videos de alta resolución o muy largos: memoria y tiempo. | Solo se leen los cuadros del tramo (≤ 45 a 15 fps), en bloques que no pasan de 64 MB, y se reducen al tamaño de trabajo (lado largo ≤ 1024). Medido en P2 con el video de prueba (720p, 60 fps): 4,1 s para leer 45 cuadros y 7,6 s de video a sticker (439 KB). Con videos 1080p o 4K va a tardar más: no verificado. |
| No verificado que WhatsApp haga cumplir el límite de 10 packs en total. | Se respeta igual (D8). |

## 14. Preguntas abiertas

Ninguna. Las cuatro del borrador quedaron resueltas (sección 15).

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-09 | Pregunta 1 (fuente): "Me gustaría GIF pero empecemos con videos" | D1; GIF en "No entra". |
| 2026-11-09 | Pregunta 2 (spike): "Bueno dale, ahora no estoy en frente de la compu pero bueno" | Spike hecho (Anexo A). |
| 2026-11-09 | Pregunta 3 (duración): "Dale xon 3s luego vamos ajustando con la spike" | D2; ajuste pendiente en P1 de preguntas abiertas. |
| 2026-11-09 | Pregunta 4 (quitar fondo en animados): "Si confirmo" | D3. |
| 2026-11-09 | Pregunta 5 (encoder): "dale" | D4. |
| 2026-11-09 | Pregunta 6 (límite de packs): "Hay límites diferentes o se puede tener 10 y 10 en WhatsApp?" | Según el README es 10 en total por app; D8 y P3 de preguntas abiertas. |
| 2026-11-10 | Sobre el spike: "Lo que no entiendo es que va a metiendo pequeñas partes del video y va generando stickers" | Era solo del spike (3 tramos para llegar al mínimo de 3 del pack); en la app es un video, un sticker (RN-1). |
| 2026-11-10 | "COnfirmo D5, D6, D7, D8, D9, D10, D11" | Esas decisiones pasan a "Santi (2026-11-10)". |
| 2026-11-10 | P1 (largo del tramo): "3s fijos para esta version, despues elegibles" | D2 actualizada; elegir el largo queda para la siguiente versión. |
| 2026-11-10 | P2 (miniaturas en Inicio): "Dale, que en esta version se vean quietos" | Nueva D12; sección 11. |
| 2026-11-10 | P3 (límite compartido): "SI" | D8 confirmada. |
| 2026-11-10 | P4 (video de prueba): "Ahi te pase un video, usalo para los test" | Nueva D13: `androidTest/assets/video/prueba.mp4`. |
| 2026-11-10 | "Sí, aprobada" | Estado: aprobada. Spec commiteada; empieza la implementación. |
| 2026-11-10 | Prueba de P3: "hubo un video de unos 40s que no lo pude generar osea cuaadno arrancaba el mensaje "Generando X%" llegaba al 100% y volvia al 40-50% aprox y nunca se generó" | Bug: webp-android no pisa un archivo existente y todos los intentos de la escalera (D6) medían el primero (675 KB los 6). Arreglo: borrar el archivo antes de cada intento; test nuevo `rn4_siElPrimerIntentoNoEntra_elSiguienteSeMideDeNuevo`. Con el mismo video (22 s, 528×352) entró en el segundo intento (490 KB). |
| 2026-11-10 | Después del arreglo, sobre si commitear P3: "Sí, todo bien" | UI de P3 confirmada y commiteada. No quedó confirmado explícitamente un video grabado en vertical (se verifica en P4). |

## 16. Verificación (P4, 2026-11-10)

Entorno: Samsung S20 FE (SM-G780G), Android 13, WhatsApp 2.26.42.74. 64 tests unitarios (PC) y 35 instrumentados (teléfono, con `am instrument`, sin borrar los stickers), todos en verde. Prueba manual de Santi durante P3.

| AC | Test automático | Prueba manual |
|---|---|---|
| AC-1 | — (solo UI) | Sí |
| AC-2 | `AnimatedStickerTest` (cuadro en un instante, tamaño de trabajo) | Sí |
| AC-3 | `AnimatedStickerTest.ac3` (desde 3 s en un video de 4,2 s salen 17 cuadros) | No con un video de menos de 3 s |
| AC-4 | — (solo UI) | No confirmado explícitamente |
| AC-5 | — (solo UI) | Sí el progreso; cancelar no confirmado explícitamente |
| AC-6 | `AnimatedStickerTest.ac6` (video de prueba y cuadros sintéticos: 512×512, loop infinito, ≥ 2 cuadros, ≥ 8 ms, ≤ 10 s, primer cuadro correcto) | Sí: los stickers se mueven en WhatsApp |
| AC-7 | `AnimatedStickerTest.ac7` (ruido) y `rn4_...` (la escalera mide cada intento) | Sí: con el bug apareció el mensaje; arreglado, el mismo video entró en el segundo intento |
| AC-8 | `AnimatedPacksTest` | Sí: "Mis animados 2 · animado" |
| AC-9 | `AnimatedPacksTest` (límite compartido) | No (requiere 10 packs) |
| AC-10 | `StickerContentProviderTest.spec003_ac10` | Sí, implícito: WhatsApp lo aceptó como animado |
| AC-11 | `AnimatedTrayTest` | Sí, implícito: el pack tiene ícono en WhatsApp |
| AC-12 | — | Sí: agregado y actualizado (versión 5, 5 stickers) |
| AC-13 | `AnimatedPacksTest` (index.json de antes) | Sí: el pack estático de Santi siguió funcionando |

**Video vertical:** `AnimatedStickerTest.videoVertical_seLeeDerechoPorLosDosCaminos`, con `prueba-vertical.mp4` (el video de prueba con metadata de rotación, generado con `ffmpeg -display_rotation 90 -c copy`, sin recodificar): por tiempo y por índice el cuadro sale de 576×1024 y coincide con el horizontal rotado. Así guarda la cámara los videos verticales; no se probó con uno grabado en el teléfono.

**Auditoría:** se revisó todo el cambio de la rama contra la spec; sin código muerto ni avisos nuevos de Kotlin. El manifest final sigue sin permisos de red.

**No verificado:**
- Videos 1080p/4K de la cámara (tiempo y memoria); el de prueba es 720p.
- Video vertical grabado en el teléfono (solo el generado con ffmpeg).
- Videos cuadrados con rotación (VideoFrames supone que vienen ya rotados).
- AC-3 con un video de menos de 3 s, AC-4 y cancelar en AC-5, AC-9 a mano.
- Lint: siguen los avisos aceptados (`Aligned16KB` por D4, AGP más nuevo disponible, `UseKtx`).

---

## Anexo A · Resultado del spike (2026-11-09/10)

Samsung S20 FE (SM-G780G), Android 13, WhatsApp 2.26.42.74. App descartable con `webp-android` 1.1.2 y un provider con `animated_sticker_pack = 1`.

| Prueba | Resultado |
|---|---|
| Librería | Sin permisos en su manifest ni en el manifest final. Sus 20 `.so` (4 arquitecturas) tienen alineación de 4 KB, no 16 KB (revisado en la PC). |
| Versión 1 (cuadros por tiempo) | Leer 45 cuadros tardó ~10 s; la app se cerró al codificar, sin error de Java registrado. Causa no confirmada (el motivo se perdió al reinstalar). |
| Versión 2 (cuadros por índice + `ARGB_8888`) | Leer 45 cuadros: 0,6–1,3 s. Codificar: 2,2–3,1 s por sticker. |
| Tamaño | 204–363 KB con 15 fps y calidad 70 (videos de 320×342 y 540×540); siempre entró al primer intento. |
| Archivo (revisado en la PC) | 512×512, 45 cuadros de 66 ms (2,97 s), repetición infinita. |
| Ícono | `ImageDecoder` saca el primer cuadro del WebP animado: 96×96, 13–14 KB. |
| WhatsApp, agregar | `add_successful=true`; el sticker se mueve en el chat (Santi). |
| WhatsApp, actualizar | Con versión mayor muestra "Update"; al aceptar, `add_successful=true` y aparecen los 5 stickers. |

No verificado: videos de cámara en alta resolución y de varios minutos, videos rotados (los del spike tenían rotación 0), otros teléfonos.
