# 001 · Stickers estáticos + integración con WhatsApp + pack que se va llenando

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-08 (borrador del 2026-11-06). |
| **Componentes** | App Android nueva (repo `creador-stickers`, módulo único `app`). |
| **Hoja de ruta** | Parte 1 de 3 (1: estáticos + WhatsApp; 2: quitar fondo; 3: animados). |
| **Rama** | `feature/001-Estaticos_WhatsApp` desde `main`, después del commit inicial que hace Santi. |
| **Evidencia** | Spike en dispositivo real del 2026-11-06 (Anexo A). |

---

## 1. Qué problema resuelve

Hacer stickers propios para WhatsApp a partir de fotos, rápido y sin anuncios. El creador nativo de WhatsApp ya existe, pero agrega un **borde blanco** a todos los stickers y no arma packs persistentes de forma cómoda (los stickers se mandan directo y solo quedan si se agregan a favoritos). Esta parte entrega el flujo mínimo: **elegir foto → encuadrar → guardar → tenerlo en WhatsApp**, con stickers sin borde, acumulados en un pack personal que se va llenando.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero elegir una foto, encuadrarla en un cuadrado (moviendo y haciendo zoom) y guardarla como sticker, sin bordes agregados.
- **HU-2** Como usuario, quiero que mis stickers se acumulen en un pack personal y poder agregarlo a WhatsApp cuando tenga al menos 3.
- **HU-3** Como usuario, quiero que, cuando sumo o borro stickers, la app me avise y me deje actualizar el pack en WhatsApp con un toque.
- **HU-4** Como usuario, quiero borrar un sticker que no me gustó.

## 3. Alcance

**Entra**
- Pantalla principal con los packs y sus stickers, y el estado de cada pack respecto de WhatsApp.
- Elegir una foto con el Photo Picker del sistema.
- Pantalla de encuadre cuadrado con pan y zoom; al alejar, lo que queda fuera de la foto es transparente.
- Generación del sticker: WebP 512×512, ≤ 100 KB. Ícono de bandeja PNG 96×96, ≤ 50 KB.
- Pack que se va llenando: hasta 30 stickers por pack; al llenarse se crea otro; hasta 10 packs (límite de WhatsApp).
- Agregar el pack a WhatsApp y actualizarlo después de cambios (reenviando el intent; ver D8).
- Borrar stickers, respetando el mínimo de 3 en packs ya agregados (D9, a confirmar).
- ContentProvider con el contrato oficial de WhatsApp.

**No entra (y adónde va)**
- Quitar fondo (ML Kit) → parte 2. Ahí también va el **borde blanco opcional, apagado por defecto**.
- Stickers animados → parte 3.
- Texto, dibujo, filtros, rotación → backlog "Editor".
- Elegir emoji por sticker → backlog (en la parte 1 es fijo, D6).
- WhatsApp Business → backlog (D7, a confirmar).
- Crear packs a mano aunque el actual no esté lleno, y elegir a qué pack va cada sticker → versión siguiente (D15).
- Reordenar stickers, mover entre packs, renombrar packs → backlog.
- Backup/exportación de stickers → backlog.
- Publicación (Play Store, R8, App Bundle, donaciones, "Acerca de") → cuando se decida publicar.
- Varios idiomas → backlog (la UI va en español).

## 4. Decisiones

| # | Decisión | Motivo | Quién |
|---|---|---|---|
| D1 | Kotlin + Jetpack Compose, una sola Activity, un solo módulo. Sin framework de DI ni librería de navegación (2 pantallas, se navega con estado). | Velocidad de desarrollo; uso personal. | Santi (stack; el resto, con la aprobación de la spec 2026-11-08) |
| D2 | Persistencia en un JSON (`filesDir/packs/index.json`) con `kotlinx.serialization`. Sin Room. | Son ≤ 10 packs × 30 stickers. `kotlinx.serialization` se prueba en tests de JVM (`org.json` no funciona en tests unitarios sin Android). | Santi (2026-11-08) |
| D3 | Photo Picker del sistema (`PickVisualMedia`), sin permisos de almacenamiento. | No pide permisos; disponible en Android 11+. | Santi (2026-11-08) |
| D4 | Encuadre propio en Compose (cuadro fijo, pan + zoom, zoom-out deja margen transparente). Sin librería de recorte. | El único caso es cuadrado; evita una dependencia basada en Views. | Santi (2026-11-08) |
| D5 | `minSdk 30`, codificación con `Bitmap.compress(WEBP_LOSSY)` bajando la calidad hasta ≤ 100 KB. | El teléfono de uso es Android 13; `WEBP_LOSSY` existe desde API 30 (verificado en la doc de Android). | Santi (2026-11-08) |
| D6 | Emoji fijo 😀 en cada sticker. | WhatsApp exige 1 a 3 emojis por sticker (no acepta texto en ese campo). | Santi |
| D7 | Solo WhatsApp común (`com.whatsapp`): el intent va con `setPackage("com.whatsapp")`. Business queda fuera. | Santi usa solo WhatsApp común y no se puede probar Business. CLAUDE.md pedía soportar ambos: Santi confirmó dejarlo fuera. | Santi (2026-11-08) |
| D8 | Para que WhatsApp vea cambios en un pack ya agregado, la app **reenvía el intent** y el usuario toca **"Update"** en WhatsApp. La app guarda la última versión confirmada por WhatsApp para saber cuándo hace falta. | Probado en el spike: subir `image_data_version` solo no alcanza; reenviar el intent sí (Anexo A). | Santi (spike aprobado) |
| D9 | Borrar stickers entra en la parte 1, con el mínimo de 3 en packs ya agregados a WhatsApp. | Borrar + "Update" funcionó en el spike. | Santi (2026-11-08) |
| D10 | Nombres: packs "Mis stickers 1", "Mis stickers 2"…; publisher fijo "Santi". Identificador interno `pack_<n>` que nunca se reutiliza. | Lo más simple; renombrar va al backlog. | Santi (2026-11-08) |
| D11 | `applicationId` `com.santi.stickers`, nombre visible "Stickers". | Placeholder razonable; cambia fácil antes de publicar. | Santi (2026-11-08) |
| D12 | `allowBackup="false"`. | Igual que el ejemplo oficial; evita restaurar packs que WhatsApp ya no tiene. Backup va al backlog. | Santi (2026-11-08) |
| D13 | No se guarda la foto original: solo el WebP final. | Privacidad y espacio; editar después no entra. | Santi (2026-11-08) |
| D14 | El spike (`spike-pack-update/`) no se versiona; sus resultados quedan en el Anexo A. Se borra cuando la app funcione (con OK de Santi). | Código descartable. | Santi (2026-11-08) |
| D15 | El "pack que se va llenando" (RN-3) es el comportamiento de **esta versión**. Más adelante se podrán crear packs a mano aunque el actual no esté lleno. Para no bloquear eso, el modelo ya guarda una lista de packs independientes; lo único que cambiará es la regla que elige a qué pack va un sticker nuevo (hoy: el último). | Pedido de Santi al aprobar la spec; queda fuera de la parte 1 para mantenerla chica. | Santi (2026-11-08) |

## 5. Reglas de negocio

- **RN-1** Todo sticker guardado es WebP estático de exactamente 512×512 px y ≤ 100 KB (102 400 bytes).
- **RN-2** La zona del cuadro que no cubre la foto queda transparente. La app no agrega bordes ni fondo.
- **RN-3** Un sticker nuevo va al **último pack** si tiene menos de 30 stickers; si no, se crea un pack nuevo. (Regla de esta versión; la creación manual de packs llega después, ver D15.)
- **RN-4** Con 10 packs y el último lleno no se pueden crear más stickers: la app lo explica y no abre el selector.
- **RN-5** Todo cambio de contenido de un pack (sticker agregado o borrado, ícono regenerado) suma 1 a su `image_data_version`.
- **RN-6** Un pack solo se puede enviar a WhatsApp si tiene al menos 3 stickers.
- **RN-7** Estado de cada pack respecto de WhatsApp:
  - **Sin agregar**: WhatsApp dice que no lo tiene (whitelist = 0 o sin respuesta).
  - **Al día**: WhatsApp lo tiene y `versión actual == última versión confirmada`.
  - **Para actualizar**: WhatsApp lo tiene y `versión actual > última versión confirmada`.
- **RN-8** La "última versión confirmada" se guarda **solo** cuando WhatsApp devuelve `RESULT_OK` con `add_successful=true` al intent. Cancelar no la cambia.
- **RN-9** El estado se recalcula al volver a la app (onResume), consultando a WhatsApp (whitelist), porque el usuario pudo quitar el pack desde WhatsApp.
- **RN-10** Borrar: un pack que **alguna vez** se agregó a WhatsApp no puede quedar con menos de 3 stickers. Un pack nunca agregado puede quedar con menos; si queda vacío, se elimina.
- **RN-11** El ícono de bandeja (PNG 96×96, ≤ 50 KB) se genera del primer sticker del pack; si se borra ese sticker, se regenera del nuevo primero (cuenta como cambio, RN-5).
- **RN-12** Cada sticker lleva el emoji 😀 y texto de accesibilidad vacío.
- **RN-13** El ContentProvider solo entrega archivos que figuren en un pack (sticker o ícono); cualquier otro pedido devuelve nulo.
- **RN-14** Identificadores de pack `pack_<n>` y de archivo `s<n>.webp` con contadores que nunca retroceden (no se reutilizan aunque se borre).

## 6. Flujo

```
[Inicio: packs + stickers + estado WA]
   │
   ├─ "+" ──► ¿hay lugar? (RN-3/RN-4) ──no──► mensaje "llegaste al máximo"
   │              │ sí
   │              ▼
   │        Photo Picker ──cancela──► Inicio
   │              │ foto
   │              ▼
   │        Encuadre (pan/zoom) ──cancela──► Inicio
   │              │ Guardar
   │              ▼
   │        Generar WebP 512 ≤100 KB ──► sumar al pack (RN-3), version+1 (RN-5)
   │              ▼
   │        Inicio (el pack queda "Sin agregar" o "Para actualizar")
   │
   ├─ "Agregar a WhatsApp" / "Actualizar en WhatsApp" (≥3 stickers, RN-6)
   │        ──► intent ENABLE_STICKER_PACK ──► WhatsApp muestra el pack ("Add to WhatsApp" / "Update")
   │              ├─ OK + add_successful ──► guardar versión confirmada (RN-8) ──► "Al día"
   │              └─ cancelado ──► sin cambios
   │
   └─ mantener apretado un sticker ──► "¿Borrar?" ──► (RN-10) ──► version+1 ──► "Para actualizar"
```

## 7. Criterios de aceptación

**Crear sticker**
- **AC-1** Dado que estoy en Inicio, cuando toco "+" y elijo una foto, entonces se abre el encuadre con la foto centrada cubriendo el cuadro.
- **AC-2** Dado el encuadre, cuando muevo y hago zoom y toco "Guardar", entonces se guarda un sticker que muestra exactamente lo que estaba dentro del cuadro.
- **AC-3** Dado el encuadre, cuando alejo la foto más allá del cuadro y guardo, entonces las zonas sin foto quedan transparentes y no hay borde agregado.
- **AC-4** Dado cualquier foto (incluida una de 12 MP con mucho detalle), cuando la guardo, entonces el archivo es WebP de 512×512 y pesa ≤ 100 KB.
- **AC-5** Dado el Photo Picker o el encuadre, cuando cancelo, entonces vuelvo a Inicio sin cambios.

**Pack que se va llenando**
- **AC-6** Dado que no hay packs, cuando guardo el primer sticker, entonces se crea "Mis stickers 1" con ese sticker.
- **AC-7** Dado que el último pack tiene 30 stickers, cuando guardo otro, entonces se crea "Mis stickers N+1" con ese sticker.
- **AC-8** Dado 10 packs con el último lleno, cuando toco "+", entonces veo un mensaje que explica el límite y no se abre el selector.
- **AC-9** Dado un pack, cuando le sumo o borro un sticker, entonces su `image_data_version` aumenta en 1.

**WhatsApp**
- **AC-10** Dado un pack con menos de 3 stickers, entonces el botón de WhatsApp no está disponible y se indica cuántos faltan.
- **AC-11** Dado un pack "Sin agregar" con ≥ 3 stickers, cuando toco "Agregar a WhatsApp" y confirmo en WhatsApp, entonces el pack aparece en el panel de stickers de WhatsApp y la app lo muestra "Al día".
- **AC-12** Dado un pack "Al día", cuando le sumo un sticker, entonces la app lo muestra "Para actualizar".
- **AC-13** Dado un pack "Para actualizar", cuando toco "Actualizar en WhatsApp" y toco "Update" en WhatsApp, entonces el sticker nuevo aparece en WhatsApp y la app muestra "Al día".
- **AC-14** Dado que cancelo en WhatsApp, entonces el estado del pack no cambia.
- **AC-15** Dado que quité el pack desde WhatsApp, cuando vuelvo a la app, entonces el pack figura "Sin agregar".

**Borrar**
- **AC-16** Dado un pack agregado con más de 3 stickers, cuando mantengo apretado un sticker y confirmo, entonces se borra y el pack pasa a "Para actualizar".
- **AC-17** Dado un pack agregado con exactamente 3 stickers, cuando intento borrar uno, entonces la app lo impide y explica el mínimo.
- **AC-18** Dado un pack nunca agregado con 1 sticker, cuando lo borro, entonces el pack desaparece; el próximo sticker crea un pack con identificador nuevo.
- **AC-19** Dado que borro el primer sticker de un pack, entonces el ícono de bandeja se regenera del nuevo primero.

**Provider**
- **AC-20** Dado WhatsApp consultando `metadata`, `stickers/<id>` y `stickers_asset/<id>/<archivo>`, entonces recibe columnas y archivos según el contrato oficial (sección 10).
- **AC-21** Dado un pedido de un archivo que no está en ningún pack (incluido `../`), entonces el provider devuelve nulo.
- **AC-22** Dado un cambio en un pack, cuando WhatsApp vuelve a consultar, entonces ve los datos nuevos sin reiniciar la app.

## 8. Seguridad, permisos y privacidad

- **Permisos de la app**: ninguno peligroso. Sin `INTERNET`, sin almacenamiento (Photo Picker).
- **ContentProvider**: `exported="true"` (lo exige WhatsApp), `readPermission="com.whatsapp.sticker.READ"`, solo lectura (`insert/update/delete` lanzan `UnsupportedOperationException`). Solo sirve archivos listados en un pack (RN-13), sin rutas construidas a partir de la URI más allá del identificador y el nombre, que se validan contra el índice.
- **`<queries>`**: solo `com.whatsapp` (y `com.whatsapp.w4b` no, mientras D7 deje Business fuera).
- **Datos**: las fotos se leen una vez desde el URI del picker y no se guardan (D13). Los stickers quedan en el almacenamiento interno de la app. No sale nada del teléfono salvo lo que el usuario agrega a WhatsApp.
- **Backup**: desactivado (D12).
- No hay checklist de seguridad del proyecto; se usa esta lista.

## 9. Modelo de datos

Archivos en `filesDir/packs/`:

```
packs/
  index.json
  pack_1/
    tray.png
    s1.webp
    s2.webp
  pack_2/
    ...
```

`index.json`:

```json
{
  "schemaVersion": 1,
  "nextPackNumber": 3,
  "nextStickerNumber": 45,
  "packs": [
    {
      "identifier": "pack_1",
      "name": "Mis stickers 1",
      "imageDataVersion": 7,
      "lastConfirmedVersion": 7,
      "everAdded": true,
      "stickers": [ { "file": "s1.webp", "emojis": ["😀"] } ]
    }
  ]
}
```

- Escritura atómica (archivo temporal + rename). Los `.webp` se escriben antes de actualizar el índice; al borrar, primero se actualiza el índice y después se borra el archivo.
- `schemaVersion` permite migrar en partes futuras (por ejemplo, packs animados agregarán `animated`).
- Sin migraciones: es la primera versión.

## 10. Contratos

**ContentProvider** — autoridad `com.santi.stickers.stickercontentprovider` (mismo contrato que `StickerContentProvider` del repo oficial WhatsApp/stickers):

| URI | Respuesta |
|---|---|
| `content://<autoridad>/metadata` | Todos los packs con ≥ 3 stickers. |
| `content://<autoridad>/metadata/<id>` | Ese pack. |
| `content://<autoridad>/stickers/<id>` | Columnas `sticker_file_name`, `sticker_emoji` (separados por coma), `sticker_accessibility_text`. |
| `content://<autoridad>/stickers_asset/<id>/<archivo>` | `AssetFileDescriptor` del archivo (`image/webp` o `image/png` para `tray.png`). |

Columnas de metadata: `sticker_pack_identifier`, `sticker_pack_name`, `sticker_pack_publisher`, `sticker_pack_icon`, `android_play_store_link`, `ios_app_download_link`, `sticker_pack_publisher_email`, `sticker_pack_publisher_website`, `sticker_pack_privacy_policy_website`, `sticker_pack_license_agreement_website`, `image_data_version`, `whatsapp_will_not_cache_stickers` (siempre 0; deprecado desde WhatsApp 2.25.9.78), `animated_sticker_pack` (siempre 0).

**Intent para agregar/actualizar**: acción `com.whatsapp.intent.action.ENABLE_STICKER_PACK`, paquete `com.whatsapp`, extras `sticker_pack_id`, `sticker_pack_authority`, `sticker_pack_name`. Resultado observado en el spike: `RESULT_OK` + `add_successful=true`, o `RESULT_CANCELED` + `user_cancelled=true`; en error de validación, extra `validation_error`.

**Whitelist**: `content://com.whatsapp.provider.sticker_whitelist_check/is_whitelisted?authority=<autoridad>&identifier=<id>`, columna `result` (1 = agregado).

## 11. Pantallas

1. **Inicio**: un bloque por pack con nombre, `N/30`, estado (Sin agregar / Al día / Para actualizar) y el botón correspondiente ("Agregar a WhatsApp", "Actualizar en WhatsApp", o "Faltan N stickers" deshabilitado). Grilla de stickers. Botón flotante "+". Mantener apretado un sticker → diálogo "¿Borrar este sticker?". Estado vacío: "Creá tu primer sticker".
2. **Encuadre**: foto a pantalla completa con un cuadro fijo; fuera del cuadro, oscurecido. Gestos de arrastrar y pellizcar. Fondo a cuadros dentro del cuadro para ver la transparencia. Botones "Cancelar" y "Guardar".

Diseño visual: Material 3 por defecto, sin trabajo de diseño propio en esta parte.

## 12. Plan de trabajo (un commit por parte, tests primero)

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P0 | Commit inicial en `main` (CLAUDE.md, .gitignore, esta spec; **lo hace Santi**), rama, esqueleto Gradle + Compose que compila. Completar comandos en CLAUDE.md. | `./gradlew assembleDebug test lint` en verde. | — |
| P1 | Modelo + repositorio de packs (JSON, contadores, RN-3 a RN-10, RN-14), lógica de estado de sync (RN-7/RN-8). | Unitarios en JVM con carpeta temporal. | AC-6 a AC-9, AC-12, AC-14, AC-16 a AC-18 (lógica) |
| P2 | Generación de sticker y tray: transformación del encuadre → bitmap 512 → WebP ≤ 100 KB; PNG 96. | Unitarios para el cálculo del encuadre; instrumentados en el teléfono para tamaño/formato/transparencia. | AC-2 a AC-4, AC-19 |
| P3 | ContentProvider. | Instrumentados: consultas por `ContentResolver`, columnas, rechazo de archivos no listados. | AC-20 a AC-22 |
| P4 | Integración WhatsApp: intent, resultado, whitelist, estado en onResume. | Unitarios para interpretar resultados; prueba manual en el teléfono. | AC-10, AC-11, AC-13 a AC-15 |
| P5 | UI: Inicio, Photo Picker, encuadre, borrar. | Prueba manual guiada por los ACs. | AC-1, AC-5, AC-8, AC-16, AC-17 |
| P6 | Verificación completa en el S20 FE contra todos los ACs; actualizar spec; borrar el spike (con OK). | Guion manual. | Todos |

Comandos previstos: `./gradlew assembleDebug`, `./gradlew test`, `./gradlew connectedAndroidTest` (con el teléfono conectado), `./gradlew lint`.

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| WhatsApp cambia cómo maneja packs de terceros (en el issue #1089 del repo oficial anunciaron mover stickers a almacenamiento permanente "que no dependa de sincronizar con las apps después de la importación inicial"). | El flujo de D8 ya pide confirmación explícita. Si deja de funcionar, se detecta en la verificación de P6; plan B: reemitir con otro identificador. |
| WhatsApp falla en silencio. | Mostrar `validation_error` cuando venga; probar siempre en el teléfono. |
| Probado en un solo dispositivo y una versión de WhatsApp (2.26.42.74). | Aceptado para uso personal. |
| Límite de 10 packs (300 stickers). | RN-4 con mensaje claro; borrar libera lugar. |
| Gestos del encuadre más complejos de lo previsto. | Separar el cálculo (testeable) de la UI; si se complica, frenar y consultar antes de sumar una librería. |

## 14. Preguntas abiertas

Ninguna. Las cinco preguntas del borrador quedaron resueltas (ver sección 15).

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-08 | "Te voy confirmando: D2, D3, D4, D5, D7, D9, D10, D11, D12, D13, D14" | Esas decisiones pasan a "Santi (2026-11-08)" en la sección 4. |
| 2026-11-08 | P1: "Hago yo el primer commit cuando aprobemos la spec" | Encabezado y P0 indican que el commit inicial en `main` lo hace Santi; la rama se crea después. |
| 2026-11-08 | P2: "Si" | D7 confirmada: Business fuera de la parte 1. |
| 2026-11-08 | P3: "Si" | D9 confirmada: borrar entra en la parte 1. |
| 2026-11-08 | P4: "Si" | D10 y D11 confirmadas. |
| 2026-11-08 | P5: "SI" | D12 confirmada. |
| 2026-11-08 | "La spec ya estaria aprobada" | Estado: aprobada. D1 se da por confirmada con la aprobación. |
| 2026-11-08 | "la idea de usar un pack hasta que se llene y luego ahi se crea otro podria ser para la primera version pero luego que este la opcion de crear packs aunque otros no esten llenos" | Nueva D15 y nueva línea en "No entra" (sección 3). Sin cambios en reglas ni criterios de esta versión. |

---

## Anexo A · Resultado del spike (2026-11-06)

Samsung S20 FE (SM-G780G), Android 13, WhatsApp 2.26.42.74. App descartable con el mismo contrato de provider, que registraba cada consulta de WhatsApp.

| Prueba | Resultado |
|---|---|
| Agregar pack de 3 stickers | WhatsApp lee metadata, ícono, lista y archivos (dos veces) y devuelve `RESULT_OK` + `add_successful=true`. Whitelist = 1. Rápido. |
| Sumar un sticker y subir `image_data_version` | WhatsApp solo vuelve a leer metadata + ícono al abrir el panel. **No baja el sticker nuevo**, ni cerrándolo desde Recientes varias veces. |
| Reenviar el intent con versión mayor | WhatsApp muestra la hoja del pack con los 4 stickers y botón **"Update"**. Al aceptarlo, el sticker aparece. `RESULT_OK` + `add_successful=true`. |
| Reenviar el intent sin cambios de versión | La hoja muestra solo "Remove" (sin "Update"). Cancelar devuelve `user_cancelled=true`. |
| Quitar un sticker y subir versión | Mismo comportamiento: no se refleja solo. Se actualizó con el **"Refresh"** manual del menú "…" del pack en WhatsApp. |
| Reemitir con otro identificador | No se probó (no hizo falta). |

No verificado: sincronización automática después de horas; comportamiento en otras versiones de WhatsApp u otros teléfonos.
