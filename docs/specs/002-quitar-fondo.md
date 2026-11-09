# 002 · Quitar fondo + encuadre con imán

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-09. |
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
- **RN-9** Posición, por eje: si un borde de la foto queda a menos de 24 dp del borde del cuadro, se pega al borde; si la foto es más chica que el cuadro en ese eje y queda a menos de 24 dp del centro, se pega al centro.
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

## 8. Seguridad, permisos y privacidad

- No se agregan permisos. La foto se procesa en el teléfono; ni la app ni ML Kit la suben (según la doc de ML Kit, el procesamiento es on-device).
- ML Kit corre dentro de Google Play services, bajo sus términos. **No verificado:** si Play services envía métricas de uso de la API a Google.
- El modelo lo baja Play services, no la app (la app no gana permiso de internet).
- El resultado sin fondo vive en memoria mientras dura el encuadre; no se guarda en disco salvo el sticker final (como en la parte 1, D13).

## 9. Modelo de datos

Sin cambios: el sticker sin fondo es un sticker estático más (spec 001, sección 9). Sin migraciones.

## 10. Interfaces

```kotlin
/** D6: quitar fondo, intercambiable. */
interface BackgroundRemover {
    /** Pesado: llamar fuera del hilo principal. */
    suspend fun remove(photo: Bitmap): RemovalResult
}

sealed interface RemovalResult {
    /** [subject] tiene el tamaño de la foto; [bounds] es la caja del sujeto (alfa > 0). */
    data class Removed(val subject: Bitmap, val bounds: Rect) : RemovalResult
    data object NoSubject : RemovalResult
    data class Unavailable(val reason: String) : RemovalResult
}
```

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
| P0 | Dependencia de ML Kit, `meta-data` del modelo, `.gitignore` para spikes. | Build, tests y lint en verde. | — |
| P1 | `CropMath.fitBox` y `CropMath.magnet`. | Unitarios en la PC. | AC-8 a AC-12 (cálculo), RN-5 |
| P2 | `BackgroundRemover` con ML Kit. | Instrumentados en el teléfono con las fotos de prueba de D12. | AC-2, AC-4, AC-5 (parte técnica) |
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

Ninguna. Las tres del borrador quedaron resueltas (sección 15).

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
