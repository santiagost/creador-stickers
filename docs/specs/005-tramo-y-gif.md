# 005 · Tramo con caja + GIF

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-13. |
| **Componentes** | App Android (`app`): pantalla de tramo, lectura de video, lectura de GIF nueva, WebP animado. Packs, provider y WhatsApp sin cambios. |
| **Rama** | `feature/005-Tramo_y_GIF` desde `main` (`fdbb1e9`). |

## 1. Qué problema resuelve

Hoy el tramo de un animado es fijo de 3 s y solo se elige dónde arranca, y solo se aceptan videos. Santi quiere elegir el tramo con una caja que se mueve y se achica, como en otras apps, y poder usar GIFs.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero mover una caja sobre la línea de tiempo y ajustar su largo entre 2 y 10 s para elegir el pedazo del video.
- **HU-2** Como usuario, quiero crear un sticker animado desde un GIF.

## 3. Alcance

**Entra:** caja de tramo de 2 a 10 s (arranca en 5 s); el mismo tramo para GIF; selector "Video" / "GIF" en packs animados.

**No entra:** vista previa animada del tramo, recortar el GIF a mano por cuadros, velocidad o reversa (backlog).

## 4. Decisiones

| # | Decisión | Quién |
|---|---|---|
| D1 | La caja arranca en 5 s (o el largo del video/GIF si es más corto), mínimo 2 s, máximo 10 s (límite de WhatsApp). | Santi (2026-11-10) |
| D2 | En un pack animado, "+ Nuevo sticker" ofrece "Video" o "GIF", cada uno con su selector (GIF: `PickVisualMedia.SingleMimeType("image/gif")`). | Santi (2026-11-10) |
| D3 | GIF con `com.github.bumptech.glide:gifdecoder:5.0.9` (lector de GIF de Glide, suelto): Java puro, sin código nativo (no tiene el problema de 16 KB), Android 6+. Revisado en la PC: tiene cantidad de cuadros, duración de cada cuadro y lectura en orden. Licencia (verificada en P0, POM de Maven Central y LICENSE del repo): BSD simplificada + Apache 2.0. No agrega `.so` ni permisos al APK (verificado en P0). | Santi (2026-11-13) |
| D4 | Para no pasarse de memoria, el sticker usa como mucho 75 cuadros: los tramos de hasta 5 s van a 15 fps; los más largos bajan los fps (10 s → 7 fps). La escalera de la spec 003 (D6) sigue bajando desde ahí si no entra en 500 KB. | Santi (2026-11-13) |
| D5 | Los GIF conservan su transparencia (si el GIF tiene fondo transparente, el sticker también). | Santi (2026-11-13) |
| D6 | GIF de más de 20 MB no se aceptan (se avisa). | Santi (2026-11-13) |

## 5. Reglas de negocio

- **RN-1** La caja nunca sale de la línea de tiempo: arrastrarla la mueve entera; tirar de un borde cambia el largo, entre 2 y 10 s, sin pasarse del principio o el final.
- **RN-2** Si el video o GIF dura menos de 2 s se usa entero (si tiene al menos 2 cuadros); si dura entre 2 y 5 s, la caja arranca cubriéndolo entero.
- **RN-3** Cuadros por segundo del sticker: 15 hasta 5 s; para tramos más largos, `floor(75 / segundos)` (D4). Siempre ≥ 2 cuadros, cada uno ≥ 8 ms.
- **RN-4** En un GIF, el tramo se toma sobre su línea de tiempo real (sumando la duración de cada cuadro); si un cuadro declara menos de 20 ms (o no declara nada) se toma como 100 ms (convención de los navegadores; el lector de Glide ya lo hace así, verificado en su bytecode en P2).
- **RN-5** Todo lo demás de la spec 003 sigue igual: encuadre sobre el primer cuadro, escalera hasta 500 KB, loop infinito, mensaje si no entra.

## 6. Flujo

```
Pack animado ── "+ Nuevo sticker" ──► "Video" / "GIF" ──► selector
   ──► Tramo: [=====caja 5 s=====] sobre la línea de tiempo, cuadro de inicio arriba
   ──► Siguiente ──► Encuadre ──► Generando N % ──► vuelve al pack
```

## 7. Criterios de aceptación

- **AC-1** En un pack animado, "+ Nuevo sticker" ofrece "Video" y "GIF"; "GIF" abre el selector mostrando solo GIFs.
- **AC-2** Con un video de 30 s, la caja arranca en 0–5 s; arrastrarla la mueve sin cambiar el largo y nunca sale de 0–30 s.
- **AC-3** Tirar de los bordes cambia el largo entre 2 y 10 s; no se puede achicar a menos de 2 ni agrandar a más de 10.
- **AC-4** Con un video o GIF de 3 s, la caja arranca cubriéndolo entero; con uno de 1 s, se usa entero.
- **AC-5** Mientras se mueve la caja se ve el cuadro donde arranca; el texto dice el largo y el inicio.
- **AC-6** Un tramo de 10 s genera un WebP animado válido (≤ 500 KB, ≤ 10 s, loop infinito) con como mucho 75 cuadros.
- **AC-7** Un GIF genera un WebP animado válido, con su transparencia si la tiene.
- **AC-8** Un GIF de más de 20 MB o roto muestra un mensaje y no rompe la app.

## 8. Seguridad y privacidad

Sin permisos nuevos (el selector da acceso solo al archivo elegido). La librería de GIF no tiene código nativo; se revisa en el manifest final que no agregue permisos.

## 9. Modelo de datos

Sin cambios.

## 10. Interfaces

```kotlin
/** Cálculo de la caja (sin Android, con tests en la PC). */
object TrimMath {
    fun initial(durationMs: Long): ClipRange
    fun move(range: ClipRange, deltaMs: Long, durationMs: Long): ClipRange
    fun resizeStart(range: ClipRange, deltaMs: Long, durationMs: Long): ClipRange
    fun resizeEnd(range: ClipRange, deltaMs: Long, durationMs: Long): ClipRange
    fun fpsFor(lengthMs: Long): Int
}

/** Fuente de cuadros: video (VideoFrames, ya existe) o GIF (GifFrames, nuevo). */
interface AnimationSource { val info: ...; fun frameAt(...); fun frames(startMs, lengthMs, fps, ...) }
```

## 11. Pantallas

**Tramo:** el cuadro de inicio arriba; abajo, una barra con la duración total y la caja (con dos manijas en los bordes); texto "Tramo de 5,0 s desde 12,3 s"; "Cancelar" / "Siguiente".

## 12. Plan de trabajo

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P0 | Dependencia `gifdecoder`, licencia y manifest final. | Build, tests y lint. | — |
| P1 | `TrimMath`; largo del tramo y fps variables en `VideoFrames`/`AnimatedWebp`. | Unitarios (caja) e instrumentados (tramo de 10 s del video de prueba). | AC-2, AC-3, AC-4, AC-6 |
| P2 | `GifFrames`. | Instrumentados con un GIF de prueba generado con ffmpeg desde el video de prueba (y uno con transparencia). | AC-7, AC-8 |
| P3 | UI: caja de tramo, menú Video/GIF. | Manual. | AC-1, AC-5 |
| P4 | Verificación y cierre. | Todos. | Todos |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Tramos de 10 s quedan con pocos fps y calidad baja para entrar en 500 KB. | Es el límite de WhatsApp; la caja deja elegir tramos más cortos. |
| GIFs con cuadros de duración 0 o raros. | RN-4 y AC-8. |
| Memoria con GIFs grandes. | D6 (20 MB) y D4 (75 cuadros). |

## 14. Preguntas abiertas

Ninguna.

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-10 | "GIF como fuente de animados y elegir el tramo, como en varias apps que el tramo inical sea de 5s (o la duracion maxima que permita WA) y se pueda mover esa "caja" de 5seg a lo largo del video para seleccionar el trozo, tambien que esa caja se pueda achicar hasta 2 o 3 segundos." | Esta spec. |
| 2026-11-10 | P5: "Maximo 10s y minimo 2s" / P6: "Dale, hacemos que permita selector entre gif y videos" | D1, D2. |
| 2026-11-13 | "Sí, aprobada" (con D3 a D6 como estaban) | Estado: aprobada; D3 a D6 confirmadas. |
