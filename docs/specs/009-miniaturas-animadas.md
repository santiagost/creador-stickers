# 009 · Miniaturas animadas y línea dentro del tramo

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-17. |
| **Componentes** | App Android (`app`): miniaturas (`ui/Thumbnails.kt`), pantalla del tramo (`ui/TrimScreen.kt`), ViewModel (`Screen.Trim`, `loadTrimPreview`), `image/TrimMath.kt`. Repositorio, provider y WhatsApp sin cambios. |
| **Rama** | `feature/009-Miniaturas_animadas` desde `main` (`30bf909`). |
| **Viene de** | D12 de la spec 003 (miniaturas quietas "para más adelante") e idea de Santi del 2026-11-17 (línea para recorrer el tramo). |

## 1. Qué problema resuelve

- Los stickers animados se ven quietos (primer cuadro) en Inicio y en el pack: no se distingue bien uno de otro.
- Al elegir el tramo solo se ve el cuadro donde arranca la caja: no hay forma de ver qué hay en el medio o al final sin mover la caja.

## 2. Historias de usuario

- **HU-1** Como usuario, quiero ver los stickers animados en movimiento en la app, como se ven en WhatsApp.
- **HU-2** Como usuario, quiero recorrer con una línea lo que hay dentro de la caja del tramo para saber qué va a tener el sticker.

## 3. Alcance

**Entra:**
- Miniaturas animadas, siempre en movimiento, en Inicio (ícono del pack) y en la pantalla del pack.
- Línea dentro de la caja del tramo, con una perilla debajo de la barra para arrastrarla; arriba se ve el cuadro donde está la línea.

**No entra (backlog):**
- Vista previa del tramo reproduciéndose sola: más trabajo; solo si la línea no alcanza.
- Que el sticker arranque desde la línea: sigue arrancando en el principio de la caja (D7 de la spec 005).
- Mejorar la velocidad de la línea en GIF largos (D5).

## 4. Decisiones

| # | Decisión | Quién |
|---|---|---|
| D1 | La línea se agarra con una perilla redonda debajo de la barra. Arrastrar el resto de la caja la sigue moviendo y los bordes siguen cambiando el largo, como hoy. | Santi (2026-11-17) |
| D2 | La línea guarda su lugar relativo dentro de la caja (por ejemplo, "a la mitad"): al mover la caja o cambiarle el largo, la acompaña y nunca sale de la caja. | Santi (2026-11-17) |
| D3 | La línea arranca al principio de la caja: si no se toca, la pantalla se ve y funciona igual que hoy. | Santi (2026-11-17) |
| D4 | Las miniaturas animadas se mueven siempre, sin tocarlas. Se decodifican chicas (160 px, como hoy) para gastar poco. | Santi (2026-11-17) |
| D5 | En un GIF, buscar un cuadro obliga a leerlo desde el principio; en GIF largos la línea puede ir con retraso. Se acepta. | Santi (2026-11-17) |
| D6 | Para animar se usa el decodificador de Android (`ImageDecoder` → `AnimatedImageDrawable`, API 28+; la app pide 30), sin librerías nuevas. Se muestra con un `ImageView` dentro de `AndroidView`, porque `Image` de Compose no anima un `Drawable`. | Santi (2026-11-17) |
| D7 | Mientras se arrastra la perilla se muestra el cuadro clave más cercano (rápido); al soltarla, el cuadro exacto. Igual que hoy con el inicio de la caja. | Santi (2026-11-17) |

## 5. Reglas de negocio

- **RN-1** La línea está siempre dentro de la caja: entre su principio y su final, ambos incluidos.
- **RN-2** El cuadro de arriba es el que está en la línea: `inicio + lugar × largo`.
- **RN-3** Mover la caja o cambiarle el largo no cambia el lugar relativo de la línea (D2).
- **RN-4** El sticker se arma con el tramo entero, como hoy; la línea solo sirve para mirar. El encuadre usa el cuadro exacto del inicio del tramo (D7 de la 005).
- **RN-5** Si el video dura menos de 2 s no hay caja ni línea (se usa entero, RN-2 de la 005).
- **RN-6** Una miniatura que no se puede animar (archivo quieto o error) se muestra quieta, como hoy.

## 6. Flujo

```
Tramo:  [==|=====|====]      caja con la línea
              ●              perilla: arrastrar → mueve solo la línea → arriba, el cuadro en la línea
        arrastrar la caja → la caja (y la línea con ella) · bordes → largo (la línea mantiene su lugar)
```

## 7. Criterios de aceptación

**Miniaturas**
- **AC-1** Dado un pack animado, en su pantalla los stickers se mueven solos.
- **AC-2** En Inicio, el ícono de un pack animado se mueve.
- **AC-3** Los stickers estáticos se ven igual que hoy.

**Línea del tramo**
- **AC-4** Al abrir el tramo, la línea está al principio de la caja y el cuadro de arriba es el de hoy.
- **AC-5** Arrastro la perilla: la línea se mueve dentro de la caja, no sale de ella, y el cuadro de arriba muestra lo que hay en la línea (exacto al soltar).
- **AC-6** Muevo la caja o cambio su largo: la línea mantiene su lugar relativo y el cuadro de arriba se actualiza.
- **AC-7** Arrastrar dentro de la caja (no la perilla) la sigue moviendo; los bordes siguen cambiando el largo.
- **AC-8** El sticker que se guarda es el del tramo entero, igual que antes.

## 8. Seguridad y privacidad

Sin permisos nuevos ni datos nuevos. Solo se leen los archivos de stickers propios y el video o GIF que eligió el usuario.

## 9. Modelo de datos

Sin cambios en disco. En memoria, `Screen.Trim` suma `cursor: Float` (0 a 1, el lugar relativo de la línea).

## 10. Interfaces

```kotlin
// TrimMath (sin Android, con tests)
fun cursorMs(range: ClipRange, cursor: Float): Long             // RN-2
fun dragCursor(range: ClipRange, cursor: Float, deltaMs: Long): Float  // RN-1: siempre en 0..1

// MainViewModel
fun setTrimCursor(cursor: Float, exact: Boolean)                // carga el cuadro en la línea
```

## 11. Pantallas

- **Tramo:** debajo de la barra, una franja con la perilla redonda alineada con la línea; la línea cruza la caja de arriba abajo. El texto "Tramo de X s desde Y s" sigue; se suma "Viendo Z s" cuando la línea no está al principio.
- **Pack e Inicio:** igual que hoy, pero los animados se mueven.

## 12. Plan de trabajo

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P1 | `TrimMath.cursorMs` y `dragCursor`; `cursor` en `Screen.Trim`; la carga del cuadro usa la línea. | Unitarios de `TrimMath` (límites, caja movida, largo cambiado). | AC-4 a AC-6 (lógica), AC-8 |
| P2 | UI: línea y perilla en `TrimBar`; miniaturas animadas en `Thumbnails.kt`. Un commit cuando Santi confirme. | Instrumentado: un WebP animado se decodifica como `AnimatedImageDrawable`. Manual con los ACs en el teléfono. | AC-1 a AC-7 |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| Muchas miniaturas animadas a la vez (hasta 30 por pack) gastan memoria o batería. | Decodificadas a 160 px (D4); se paran cuando salen de pantalla (al salir de la composición). Medir en el teléfono con el pack más grande. |
| La perilla se confunde con el gesto de la caja. | Perilla en su propia franja, debajo de la barra (D1). |
| GIF largos: la línea va con retraso. | Aceptado (D5); los pedidos viejos se descartan, como hoy. |
| No verificado: que `AnimatedImageDrawable` reproduzca bien los WebP que genera webp-android. | Test instrumentado en P2 antes de la UI. |

## 14. Preguntas abiertas

Ninguna.

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-17 | "Dale, vamos con packs. Una cosa al de las miniaturas al inicio le agregaria la linea entre medio del bloque elegible para ir moviendo hacia adelante o atras para ver que parte del video contiene" | Esta spec. |
| 2026-11-17 | "1. Dale, hagamos asi 2. Estoy de acuerdo 3. Estoy de acuerdo 4. Siempre 5. Dale, no creo que use gif largos" | D1 a D5. |
| 2026-11-17 | "Sí, aprobada" | Estado: aprobada; D6 y D7 confirmadas. |
