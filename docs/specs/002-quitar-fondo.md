# 002 · Quitar fondo + encuadre con imán

| | |
|---|---|
| **Estado** | Implementada y verificada el 2026-11-09 (sección 16). Aprobada por Santi el 2026-11-09. |
| **Componentes** | App Android (`app`): pantalla de encuadre, cálculo del encuadre, nuevo módulo de quitar fondo. No toca packs, provider ni WhatsApp. |
| **Hoja de ruta** | Parte 2 de 3 (1: estáticos + WhatsApp, hecha; 2: quitar fondo; 3: animados). |
| **Rama** | `feature/002-Quitar_fondo` desde `main` (`073cd9f`). |
| **Evidencia** | Spike en el S20 FE del 2026-11-09 (Anexo A). |

---

## 1. Qué problema resuelve

Hoy un sticker es un recorte cuadrado de la foto, con su fondo. Para que parezca un sticker "de verdad" hace falta quedarse solo con el sujeto (persona, mascota, objeto) sobre fondo transparente, sin el borde blanco que agrega el creador de WhatsApp. Además, en la parte 1 encuadrar una foto que entra justo de ancho (o de alto) en el cuadro cuesta: al moverla se corre de costado con facilidad.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero poder quitar el fondo de la foto con un toque mientras la encuadro, solo cuando yo lo decida.
- **HU-2** Como usuario, quiero que al quitar el fondo el sujeto quede centrado y entero en el cuadro, sin tener que reencuadrar.
- **HU-3** Como usuario, quiero que el encuadre "se pegue" cuando la foto entra justo de ancho o de alto, para moverla en el otro sentido sin que se corra.

## 3. Alcance

**Entra**
- Botón "Quitar fondo" en la pantalla de encuadre, apagado por defecto, que se puede prender y apagar.
- Quitar fondo con ML Kit Subject Segmentation, en el teléfono.
- Encuadre automático sobre el sujeto al prender "Quitar fondo".
- Indicador de "Quitando fondo…" y mensajes claros si no hay sujeto o si ML Kit no está disponible.
- Descarga del modelo al instalar la app.
- Encuadre con imán (escala y posición) en la pantalla de encuadre, con o sin fondo quitado.

**No entra (y adónde va)**
- Borde blanco opcional → más adelante, apagado por defecto (Santi, 2026-11-09).
- Retoque manual con pincel (borrar/restaurar zonas) → backlog; el spike no mostró que haga falta.
- Elegir un sujeto entre varios (ML Kit lo permite) → backlog. Con varias personas ML Kit toma solo algunas (Anexo A).
- Bordes suaves con la máscara de confianza → backlog, solo si se ven dientes en los stickers (ver D8).
- Alternativa abierta a ML Kit (para F-Droid) → si se decide publicar en F-Droid (D1).
- Quitar el fondo de stickers ya guardados → backlog (D13 de la spec 001: la foto original no se guarda).
- Rediseño de UI → sigue pendiente para cuando estén casi todas las funcionalidades (spec 001).

## 4. Decisiones

| # | Decisión | Motivo | Quién |
|---|---|---|---|
| D1 | ML Kit Subject Segmentation (`play-services-mlkit-subject-segmentation:16.0.0-beta1`), aunque ate la app a Google Play services. | Recorta personas, mascotas y objetos; en el spike funcionó bien. La alternativa abierta es mucho más trabajo. | Santi (2026-11-09) |
| D2 | "Quitar fondo" apagado por defecto; se recorta solo si el usuario lo decide. | No todas las fotos tienen un sujeto recortable. | Santi (2026-11-09) |
| D3 | Borde blanco: más adelante, como opción apagada por defecto. | Mantener esta parte chica. | Santi (2026-11-09) |
| D4 | Al prender "Quitar fondo", el encuadre se ajusta solo al sujeto. | Evita reencuadrar cada vez. | Santi (2026-11-09) |
| D5 | Encuadre con imán en ambos ejes (escala y posición). Umbrales iniciales ±6 % y 24 dp, a ajustar probando. | Pedido de Santi al responder la propuesta. Detalle en RN-8 a RN-10. | Santi (2026-11-09) |
| D6 | ML Kit queda detrás de una interfaz chica (`BackgroundRemover`). | Para poder cambiarlo si ML Kit se discontinúa o si se publica en F-Droid. | Santi (2026-11-09) |
| D7 | El modelo se pide al instalar (`meta-data` `com.google.mlkit.vision.DEPENDENCIES` = `subject_segment`). | Recomendación de la doc; sin modelo, ML Kit no devuelve nada. | Santi (2026-11-09) |
| D8 | Se usa el "foreground bitmap" de ML Kit tal cual (alfa 0 o 255, sin bordes suaves). | En el spike los recortes se vieron bien; al reducir a 512 el borde se suaviza solo si la foto es más grande. Bordes suaves van al backlog. | Santi (2026-11-09) |
| D9 | Se quita el fondo de la foto entera ya decodificada (lado largo ≤ 2048), antes de encuadrar. El resultado se guarda en memoria mientras dura el encuadre: apagar y prender no reprocesa. | Una sola pasada de ML Kit (~300 ms) y el encuadre sigue funcionando igual. | Santi (2026-11-09) |
| D10 | Al apagar "Quitar fondo" se conserva el encuadre actual. | Si el usuario ya ajustó, no pierde lo hecho. | Santi (2026-11-09) |
| D11 | El spike (`spike-quitar-fondo/`) no se versiona; se borra al terminar la parte 2 (con OK de Santi). | Igual que en la parte 1. | Santi (2026-11-09) |
| D13 | Se quitan del manifest los permisos `INTERNET` y `ACCESS_NETWORK_STATE` que agrega ML Kit (`tools:node="remove"`). La app sigue sin acceso a la red. | Solo sirven para mandar métricas de uso a Google; el modelo lo baja Play services. | Santi (2026-11-09) |
| D12 | Los tests instrumentados de quitar fondo usan fotos de Santi guardadas en `app/src/androidTest/assets/fondo/` (quedan en el repo de GitHub). | Con imágenes sintéticas no está garantizado que ML Kit detecte un sujeto. | Santi (2026-11-09) |

## 5. Reglas de negocio

**Quitar fondo**
- **RN-1** "Quitar fondo" arranca apagado en cada foto nueva.
- **RN-2** La primera vez que se prende en una foto, se procesa con ML Kit; mientras tanto se muestra "Quitando fondo…" y "Guardar" queda deshabilitado. Las veces siguientes (prender/apagar) usan el resultado ya calculado.
- **RN-3** Si ML Kit no encuentra sujeto (todo transparente), se avisa "No encontré nada para recortar en esta foto" y el botón vuelve a apagado.
- **RN-4** Si ML Kit falla o no está disponible (sin Play services, modelo sin bajar), se avisa y el botón vuelve a apagado; la foto se puede seguir usando normal.
- **RN-5** Al prender, el encuadre se ajusta para que la caja del sujeto entre completa en el cuadro con un margen del 5 % por lado, centrada.
- **RN-6** Al apagar, se vuelve a la foto original conservando el encuadre (D10).
- **RN-7** El sticker se guarda con lo que se ve: con fondo quitado, todo lo que no es sujeto queda transparente (sigue valiendo RN-1 y RN-2 de la spec 001: WebP 512×512 ≤ 100 KB, sin borde).

**Encuadre con imán** (umbrales iniciales, D5)
- **RN-8** Escala: si el zoom queda a menos de ±6 % de "ancho justo" (el ancho de la foto ocupa el cuadro) o de "alto justo", se pega a ese valor.
- **RN-9** Posición, por eje: si la foto llena el cuadro en ese eje y un borde de la foto queda a menos de 24 dp del borde del cuadro, se pega al borde; si la foto es más chica que el cuadro en ese eje, solo se pega al centro (a menos de 24 dp), nunca a los bordes de adentro.
- **RN-10** El imán es "duro": mientras el dedo siga moviendo la foto, la posición real se sigue acumulando, y recién se despega cuando supera el umbral. Por eso una foto vertical a ancho justo se puede mover de arriba a abajo sin correrse de costado.
- **RN-11** Lo que se guarda es lo que se ve (la posición pegada), no la posición "real" del dedo.

## 6. Flujo

```
Encuadre (foto original, "Quitar fondo" apagado)
   │
   ├─ prender "Quitar fondo"
   │     ├─ ¿ya calculado? ── sí ──► mostrar sujeto ─┐
   │     └─ no ──► "Quitando fondo…" ──► ML Kit       │
   │                 ├─ sujeto ──► guardar en memoria ─┤──► encuadre automático (RN-5)
   │                 ├─ vacío ───► "No encontré nada…", apagado
   │                 └─ error ───► mensaje, apagado
   │
   ├─ apagar "Quitar fondo" ──► foto original, mismo encuadre (RN-6)
   │
   ├─ mover / pellizcar ──► posición real ──► imán (RN-8 a RN-10) ──► lo que se ve
   │
   └─ Guardar ──► WebP con lo que se ve (RN-7, RN-11) ──► pack (sin cambios respecto de la parte 1)
```

## 7. Criterios de aceptación

**Quitar fondo**
- **AC-1** Dada una foto recién elegida, entonces "Quitar fondo" está apagado y se ve la foto original.
- **AC-2** Dada una foto con un sujeto claro, cuando prendo "Quitar fondo", entonces veo "Quitando fondo…", y después el sujeto sobre el fondo a cuadros, entero y centrado con margen (RN-5).
- **AC-3** Dado el fondo quitado, cuando apago y vuelvo a prender, entonces el cambio es inmediato (no reprocesa) y el encuadre se conserva al apagar.
- **AC-4** Dado el fondo quitado, cuando guardo, entonces el sticker es WebP 512×512 ≤ 100 KB y todo lo que no es sujeto es transparente.
- **AC-5** Dada una foto sin sujeto, cuando prendo "Quitar fondo", entonces veo "No encontré nada para recortar en esta foto" y el botón queda apagado.
- **AC-6** Dado que ML Kit falla o no está disponible, cuando prendo "Quitar fondo", entonces veo un mensaje, el botón queda apagado y puedo guardar la foto normal.
- **AC-7** Mientras se está quitando el fondo, "Guardar" está deshabilitado y "Cancelar"/volver sigue funcionando.

**Encuadre con imán**
- **AC-8** Dada una foto vertical a ancho justo, cuando la arrastro de arriba a abajo con un desvío lateral menor a 24 dp, entonces no se corre de costado.
- **AC-9** Dada la misma foto, cuando la arrastro de costado más de 24 dp, entonces se despega y se mueve.
- **AC-10** Cuando hago zoom y paso cerca de ancho justo o alto justo (±6 %), entonces la escala se pega a ese valor.
- **AC-11** Dada una foto horizontal a alto justo, se cumple lo mismo que en AC-8 y AC-9 con los ejes invertidos.
- **AC-12** Cuando guardo con la foto pegada al imán, entonces el sticker sale con la posición pegada (la que se ve).

**Integración**
- **AC-13** Un sticker con fondo quitado se suma al pack y se agrega/actualiza en WhatsApp igual que cualquier otro (prueba manual).
- **AC-14** Dado que la app no tiene permisos de red (D13), cuando quito el fondo de una foto con sujeto, entonces funciona igual.

## 8. Seguridad, permisos y privacidad

- No se agregan permisos. La foto se procesa en el teléfono; ni la app ni ML Kit la suben (según la doc de ML Kit, el procesamiento es on-device).
- ML Kit corre dentro de Google Play services, bajo sus términos.
- **Hallazgo de P0 (verificado en el manifest final):** la dependencia de ML Kit trae `com.google.android.datatransport` (`transport-backend-cct` y `transport-runtime`), que agrega los permisos `INTERNET` y `ACCESS_NETWORK_STATE`. Es el canal con el que las librerías de Google mandan métricas de uso. Se quitan (D13): verificado en el manifest final que la app no tiene permisos de red. **Pendiente verificar en P2** que quitar fondo funciona sin ellos (AC-14).
- El modelo lo baja Play services en su propio proceso, no la app.
- El resultado sin fondo vive en memoria mientras dura el encuadre; no se guarda en disco salvo el sticker final (como en la parte 1, D13).

## 9. Modelo de datos

Sin cambios: el sticker sin fondo es un sticker estático más (spec 001, sección 9). Sin migraciones.

## 10. Interfaces

```kotlin
/** D6: quitar fondo, intercambiable. Implementación: MlKitBackgroundRemover. */
interface BackgroundRemover : Closeable {
    /** Pesado (~300 ms): llamar desde una corrutina. */
    suspend fun remove(photo: Bitmap): RemovalResult
}

sealed interface RemovalResult {
    /** [subject] tiene el tamaño de la foto; [bounds] es la caja del sujeto (alfa > 0), en píxeles de la foto. */
    data class Removed(val subject: Bitmap, val bounds: CropMath.Box) : RemovalResult
    data object NoSubject : RemovalResult
    data class Unavailable(val reason: String) : RemovalResult
}
```

La caja usa `CropMath.Box` (sin Android) en lugar de `android.graphics.Rect`, para que `fitBox` se pueda probar en la PC. `StickerImages.alphaBounds` la calcula a partir del canal alfa.

Cálculos nuevos en `CropMath` (sin Android, con tests en la PC): `fitBox(...)` para RN-5 y `magnet(...)` para RN-8 a RN-10.

## 11. Pantallas

**Encuadre** (spec 001, 11.2) con:
- Un botón de dos estados "Quitar fondo" entre la foto y los botones "Cancelar"/"Guardar".
- Mientras procesa: el botón muestra "Quitando fondo…" con una ruedita, y "Guardar" se deshabilita.
- Mensajes en un Snackbar.
- El imán no se ve: se siente al mover.

Sin cambios en Inicio.

## 12. Plan de trabajo (un commit por parte, tests primero)

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P0 | Dependencia de ML Kit, `meta-data` del modelo, quitar permisos de red (D13), `.gitignore` para spikes. | Build, tests y lint en verde; manifest final sin permisos de red. | — |
| P1 | `CropMath.fitBox` y `CropMath.magnet`. | Unitarios en la PC. | AC-8 a AC-12 (cálculo), RN-5 |
| P2 | `BackgroundRemover` con ML Kit. | Instrumentados en el teléfono con las fotos de prueba de D12. | AC-2, AC-4, AC-5, AC-14 (parte técnica) |
| P3 | UI: botón, estados, mensajes, imán conectado a los gestos. | Prueba manual guiada por los ACs. | AC-1 a AC-13 |
| P4 | Auditoría, verificación completa, cierre de la spec, borrar el spike (con OK). | Todos los tests + guion manual. | Todos |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| ML Kit Subject Segmentation es beta y no cambia desde noviembre de 2023; puede discontinuarse. | D6: queda detrás de una interfaz. Si deja de funcionar, la app sigue andando sin quitar fondo (RN-4). |
| Sin Google Play services no hay quitar fondo. | RN-4. Para uso personal en el S20 FE no aplica. |
| Con varias personas, ML Kit toma solo algunas. | Limitación aceptada; "elegir sujeto" al backlog. |
| Bordes duros (alfa 0/255): en fotos chicas pueden verse dientes. | D8; si pasa, usar la máscara de confianza (backlog). |
| El imán puede sentirse "pegajoso" de más o de menos. | Umbrales ajustables en P3 iterando con Santi. |
| `connectedAndroidTest` desinstala la app y borra los stickers. | Usar `am instrument` (CLAUDE.md). |

## 14. Preguntas abiertas

Ninguna. Las tres del borrador y la P4 surgida en P0 (permiso de internet de ML Kit) quedaron resueltas (sección 15).

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-09 | Pregunta 1 (spike): "Bueno dale. Nos ahorra trabajo" | Spike hecho (Anexo A). |
| 2026-11-09 | Pregunta 2 (ML Kit): "De momento si" | D1. |
| 2026-11-09 | Pregunta 3 (apagado por defecto): "Si, solo se recorta si el usuario lo decide" | D2, RN-1. |
| 2026-11-09 | Pregunta 4 (borde blanco): "Dale, lo dejemos como una opcion para mas adelante" | D3, "No entra". |
| 2026-11-09 | Pregunta 5 (encuadre automático): "Dale. Sumado a esto algo que me gustaria sumar seria que viste que nosotros en la parte 1, al momento de ajustar la foto se ajusta sobre un cuadrado que va a ser el sticker, bueno que ese encuadre se ponga como mas "duro" cuando la foto entra ahi completa asi es mas facil de centrarla una vez movida. Entonces si la foto es mas larga que primero quede el ancho de imagen como mas fijo y se puede mover de arriba a abajo sin que se salga de los costados tan facil, lo mismo con una foto ancha pero con la altura" | D4 y D5; HU-3; RN-8 a RN-11; AC-8 a AC-12. |
| 2026-11-09 | Sobre el spike: "Estuve probando con varias fotos en general y la verdad que recorta bastante bien. Seguramente hayan casos particulares que no recorta muy bien peor de momento no vi ninguno" | Retoque manual queda en "No entra". |
| 2026-11-09 | "Confirmo D6, D7, D8, D9, D10, D11" | Esas decisiones pasan a "Santi (2026-11-09)". |
| 2026-11-09 | P1 (umbrales del imán): "Si empecemos con eso y vamos ajustando" | D5 con umbrales iniciales ±6 % y 24 dp; se ajustan en P3. |
| 2026-11-09 | P2 (fotos de prueba): "Te cargo fotos mias" | Nueva D12: fotos de Santi en `app/src/androidTest/assets/fondo/`. |
| 2026-11-09 | P3 (encuadre al apagar): "Si, conservarlo" | D10 confirmada. |
| 2026-11-09 | "Sí, aprobada" | Estado: aprobada. Spec commiteada; empieza la implementación. |
| 2026-11-09 | P4 (permiso de internet que agrega ML Kit, surgida en P0): "Sí, quitalo" | Nueva D13; sección 8 actualizada; nuevo AC-14 para verificarlo en P2. |
| 2026-11-09 | Prueba de P3, punto 7: "Cuando la imagen ocupa todo el cuadro se siente bien, cuando, cuando no hay fondo en una arte del cuadro se siente medio raro porque la imagen va queriendo pegarse en los costados o arriba" | RN-9: si la foto no llena el cuadro en un eje, solo se pega al centro. Test actualizado. |
| 2026-11-09 | Después del ajuste: "Se siente bien" | UI de P3 confirmada y commiteada; umbrales iniciales (±6 %, 24 dp) se quedan. |

## 16. Verificación (P4, 2026-11-09)

Entorno: Samsung S20 FE (SM-G780G), Android 13, WhatsApp 2.26.42.74. 57 tests unitarios (PC) y 24 instrumentados (teléfono, con `am instrument`, sin borrar los stickers), todos en verde. Prueba manual de Santi con el guion de P3.

| AC | Test automático | Prueba manual |
|---|---|---|
| AC-1 | — (solo UI) | Sí |
| AC-2 | `BackgroundRemoverTest` (con sujeto) | Sí: quita el fondo en menos de 2 s y queda centrado |
| AC-3 | — (solo UI) | Sí: al apagar conserva el encuadre; al prender vuelve a centrar |
| AC-4 | `BackgroundRemoverTest.ac4` (WebP 512 ≤ 100 KB, esquinas transparentes) | Sí: el sticker se ve igual que en la app |
| AC-5 | `BackgroundRemoverTest.ac5` (imagen plana) | Sí: aparece el mensaje |
| AC-6 | — | No: requiere un teléfono sin Play services o sin el modelo |
| AC-7 | — (solo UI) | Sí: cancelar mientras quita el fondo vuelve sin guardar |
| AC-8 a AC-11 | `MagnetAndFitTest` | Sí: se pega y se despega; ajustado RN-9 tras la prueba |
| AC-12 | `MagnetAndFitTest` (aplicar el imán dos veces da lo mismo) | Sí, implícito: el sticker guardado coincide con lo que se veía |
| AC-13 | — | Sí: sticker sin fondo en el pack y en WhatsApp |
| AC-14 | `BackgroundRemoverTest` corre con la app sin permisos de red | Sí: quitar fondo funcionó en la app sin permisos de red |

**Auditoría:** se revisó todo el cambio de la rama contra la spec (sin código muerto ni registros de depuración). El manifest final sigue sin permisos de red (D13).

**No verificado:**
- AC-6 (ML Kit no disponible): solo por código; el mensaje no se vio en un teléfono real.
- El contraste del botón "Fondo quitado" con los colores del sistema (texto blanco sobre el color de selección): no se revisó con captura.
- Fotos de cámara de 12 MP con quitar fondo (las pruebas usaron fotos de hasta 1600 px), otros teléfonos.
- Lint: siguen los 2 avisos aceptados (AGP más nuevo disponible; sugerencia de estilo `UseKtx`).

---

## Anexo A · Resultado del spike (2026-11-09)

Samsung S20 FE (SM-G780G), Android 13. App descartable con ML Kit Subject Segmentation (`16.0.0-beta1`), foto decodificada con lado largo ≤ 2048 como en la app.

| Prueba | Resultado |
|---|---|
| Modelo | Disponible al abrir la app por primera vez (`areModulesAvailable = true`). |
| Una persona | Recorte perfecto (Santi). |
| Un animal | Recorte perfecto (Santi). |
| Un objeto (un pan) | Recorte perfecto (Santi). |
| Varias personas | Toma solo algunas (Santi). |
| Foto sin sujeto claro | No recorta nada: 0 % opaco (log). |
| Dos monitores y una notebook | Quitó bien el fondo y la mesa (Santi). |
| Tiempo | Primera pasada 712 ms; después entre 279 y 413 ms (fotos de 212×209 a 1600×900). |
| Repetir la misma foto | Mismo resultado cada vez (determinístico). |
| Bordes | Alfa solo 0 o 255 (sin semitransparencias), revisado en dos PNG en la PC. Ampliado 8×, el borde muestra escalones y en algún tramo una línea fina del fondo. A tamaño normal no se notó. |

No verificado: fotos de cámara de 12 MP reducidas a 2048 (las del spike eran de hasta 1600 px), memoria con fotos grandes, otros teléfonos.
