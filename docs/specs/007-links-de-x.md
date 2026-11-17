# 007 · Stickers desde links de X

| | |
|---|---|
| **Estado** | Aprobada por Santi el 2026-11-17. |
| **Componentes** | App Android (`app`): manifest (permiso de internet, recibir texto, quitar las métricas de ML Kit), lectura de links de X y descarga nueva, ViewModel, menú del pack. Packs, provider, WhatsApp, tramo y encuadre sin cambios. |
| **Rama** | `feature/007-Links_de_X` desde `main` (`2599add`). |
| **Viene de** | Spec 006 (recibir compartidos): X comparte solo el link, nunca el archivo (verificado 2026-11-17). |

## 1. Qué problema resuelve

Santi quiere hacer stickers con videos y GIF de X. La app de X solo comparte el link, así que la app tiene que bajar el video a partir del link (como Snaptube).

## 2. Historias de usuario

- **HU-1** Como usuario, quiero compartir un tweet desde X a la app y hacer un sticker animado con su video o GIF.
- **HU-2** Como usuario, quiero pegar un link de X que copié y hacer lo mismo.

## 3. Alcance

**Entra:** recibir texto compartido con un link de X; "Pegar link de X" en el menú de los packs animados; bajar el video y seguir como con un video compartido (spec 006).

**No entra:** tweets privados, de cuentas protegidas o que piden iniciar sesión; otras redes (Instagram, TikTok…); elegir entre varios videos de un tweet; el plan B con un servicio de terceros (fxtwitter), que queda anotado por si X rompe el camino elegido.

## 4. Decisiones

| # | Decisión | Quién |
|---|---|---|
| D1 | Los datos del tweet salen de la dirección que usa X para los tweets incrustados en otras páginas (`cdn.syndication.twimg.com/tweet-result`), sin cuenta. No es oficial: si X la cambia, deja de andar (se avisa, RN-6). Solo se le manda a X el número del tweet. | Santi (2026-11-17) |
| D2 | La app vuelve a tener permiso de internet, y se le quita a ML Kit la parte que manda métricas de uso a Google (`TransportBackendDiscovery` de datatransport), así sigue sin mandar nada que no sea lo de X. Se verifica en P0 que "Quitar fondo" siga andando. | Santi (2026-11-17) |
| D3 | El link entra de dos formas: compartiendo texto desde X (la app pasa a aparecer al compartir texto) y con "Pegar link de X" en el menú "Video / GIF" de un pack animado. | Santi (2026-11-17) |
| D4 | Si el tweet tiene varios videos, se usa el primero y se avisa. | Santi (2026-11-17) |
| D5 | Se baja el MP4 de mejor calidad de hasta 720p (el sticker sale a 512x512). Si pesa más de 50 MB, se avisa y no se baja. | Santi (2026-11-17) |
| D6 | El video bajado se guarda en la caché de la app y se borra al terminar o cancelar (y lo que quede, al abrir la app). | Santi (2026-11-17) |

**Verificado el 2026-11-17 desde la PC:**
- La dirección de D1 responde (HTTP 200) con dos tweets de video de NASA (Artemis II y Roman): trae `mediaDetails` con `type: "video"`, la duración y los MP4 en 270p, 360p, 720p, 1080p y 4K, más una lista HLS (que no se usa).
- El MP4 se baja sin cuenta ni encabezados especiales (33 s a 720p = 3,8 MB).
- Hoy no controla el `token` de la dirección (anda con cualquiera o sin él). Igual se calcula como lo hace el código de los tweets incrustados, por si empieza a controlarlo.
- Tweet inexistente: HTTP 404 (página HTML); número inválido: HTTP 400. Ambos caen en RN-6.

**Verificado en P0 (2026-11-17):**
- Con los links de Santi: el de video viene como `type: "video"` (7,7 s, MP4 de 366x270, 488x360 y 976x720); el "GIF" como `type: "animated_gif"`, con un solo MP4 de `video.twimg.com/tweet_video/…` con bitrate 0 y sin resolución en la dirección (62 KB). RN-4 elige ese único MP4.
- Con internet y sin `TransportBackendDiscovery` en el S20 FE: "Quitar fondo" anda (5 tests de quitar fondo OK) y datatransport registra `Transport backend 'cct' is not registered`, o sea, descarta las métricas en vez de subirlas. La app no tiene trabajos de subida programados (otras apps del teléfono sí).
- Para los tests se guardaron las respuestas recortadas (solo el tipo y los videos, sin texto ni usuario) en `app/src/test/resources/x/`.

## 5. Reglas de negocio

- **RN-1** Un link sirve si es de `x.com`, `twitter.com` (con o sin `www.`/`mobile.`) y tiene `/status/<número>` (también `/i/status/<número>`); se ignora lo que venga después (`?s=…`, `/photo/1`, `/video/1`). Si el texto compartido trae varios links, se usa el primero de X.
- **RN-2** Si el texto no trae un link de X: "Eso no es un link de X." (la app aparece al compartir cualquier texto, D3).
- **RN-3** Del tweet se toman solo `video` y `animated_gif`; si no tiene ninguno: "Ese tweet no tiene video ni GIF." Si tiene varios, D4.
- **RN-4** De cada video se elige el MP4 (`video/mp4`) de mayor bitrate con alto y ancho ≤ 720 en el lado corto; si no hay, el de menor resolución. El tamaño se mira antes de bajar (D5).
- **RN-5** Mientras baja se ve "Bajando video… N %" con "Cancelar". Al terminar sigue igual que un video compartido: "¿A qué pack?" (desde Compartir) o directo al tramo (desde "Pegar link", que ya está dentro de un pack).
- **RN-6** Sin internet, tweet borrado/privado/protegido o respuesta que no se entiende: un mensaje claro ("No hay conexión.", "No se pudo leer ese tweet. Puede ser privado o haberse borrado.") y no se rompe la app.
- **RN-7** "Pegar link de X" lee el portapapeles solo al tocarlo (Android muestra el aviso de que se pegó). Si no hay un link de X: "No copiaste un link de X."

## 6. Flujo

```
X ── Compartir (texto) ──► Stickers ── link de X ──► Bajando video… ──► ¿A qué pack? (animados) ──► Tramo ──► Encuadre
Pack animado ── + Nuevo sticker ──► Video / GIF / Pegar link de X ──► Bajando video… ──► Tramo ──► Encuadre
```

## 7. Criterios de aceptación

- **AC-1** Dado un tweet con video, cuando lo comparto desde X a Stickers, entonces se baja, elijo un pack animado y sigo con el tramo; el sticker queda en ese pack.
- **AC-2** Dado un tweet con "GIF", lo mismo que AC-1.
- **AC-3** Dado un link de X copiado, cuando toco "Pegar link de X" en un pack animado, entonces se baja y sigo con el tramo de ese pack.
- **AC-4** Mientras baja veo el porcentaje; "Cancelar" corta la descarga y vuelve a donde estaba.
- **AC-5** Compartir un texto sin link de X, o un tweet sin video, avisa (RN-2, RN-3).
- **AC-6** Sin conexión, o con un tweet borrado o privado, avisa y no se rompe la app (RN-6).
- **AC-7** "Quitar fondo" sigue andando, y la app no tiene el componente de métricas de ML Kit (se ve en el manifest final).
- **AC-8** Un video de más de 50 MB a 720p no se baja y se avisa.

## 8. Seguridad y privacidad

- **Permiso nuevo:** `INTERNET` (y no `ACCESS_NETWORK_STATE`, que sigue quitado). Solo se conecta a `cdn.syndication.twimg.com` y a la dirección del MP4 que devuelve X (solo se aceptan `https://video.twimg.com/…`; cualquier otra se rechaza).
- **Qué se manda:** el número del tweet y la descarga del MP4. Nada del usuario.
- **Métricas de ML Kit:** se quita el componente que las sube (D2).
- **Contenido de terceros:** el video es de quien lo publicó; la app lo usa para un sticker personal. Riesgo para Play Store anotado en la sección 13.

## 9. Modelo de datos

Sin cambios. Archivo temporal en la caché (D6).

## 10. Interfaces

```kotlin
/** Sin Android, con tests en la PC. */
object XLinks {
    fun statusId(text: String): String?                       // RN-1
    fun syndicationToken(id: String): String                  // como los tweets incrustados
    fun pickVideo(json: String): XVideoPick                   // RN-3, RN-4 → Video(url, isGif, more) / NoVideo / Unreadable
}

/** Con Android: red y archivo. */
object XDownloader {
    suspend fun download(context: Context, statusId: String, onProgress: (Float) -> Unit): XDownload  // Ok(file, more) / errores de RN-6 / TooBig
}
```

## 11. Pantallas

- **Bajando video:** el mismo estilo que "Generando sticker": texto "Bajando video… N %", barra de progreso, "Cancelar".
- **Menú del pack animado:** "Video", "GIF", "Pegar link de X".

## 12. Plan de trabajo

| Parte | Contenido | Tests | ACs |
|---|---|---|---|
| P0 | Manifest (internet y quitar el componente de métricas) y prueba en el teléfono de que "Quitar fondo" anda; leer un tweet con "GIF" (link de Santi) desde la PC. Se anota el resultado acá. | Manual + los instrumentados de quitar fondo. | AC-7 |
| P1 | `XLinks`: link → número, token, elección del video desde el JSON (con respuestas guardadas de la prueba). | Unitarios. | RN-1 a RN-4 |
| P2 | `XDownloader` + recibir texto en el manifest. | Instrumentado contra X real (un tweet de NASA), marcado como "necesita internet". | AC-6, AC-8 |
| P3 | UI: "Bajando video…", recibir texto, "Pegar link de X". Un commit cuando Santi confirme. | Manual con los ACs. | AC-1 a AC-5 |

## 13. Riesgos

| Riesgo | Mitigación |
|---|---|
| X cambia o cierra la dirección de D1. | Mensaje claro (RN-6); el plan B (fxtwitter u otro) queda anotado para otra spec. |
| Play Store rechaza la app por bajar contenido de otra plataforma. | Hoy es para uso personal. Si se publica, se puede apagar esta función. |
| Quitar el componente de métricas rompe algo de ML Kit. | P0 lo prueba antes de escribir el resto. |
| La app aparece al compartir cualquier texto. | RN-2: avisa si no es de X. |

## 14. Preguntas abiertas

Ninguna.

## 15. Historial de comentarios

| Fecha | Comentario de Santi (literal) | Cómo se resolvió |
|---|---|---|
| 2026-11-17 | "una forma como snaptube que si descargar videos o gifs si se los comparte desde el link de X" | Esta spec. |
| 2026-11-17 | "apruebo todas, la 2 intenta quitarle las metricas como decis" | D1 a D5. |
| 2026-11-17 | "No encontre un tweet que tenga varios videos […] igualemnte deja implementado la de agarrar el primer video. CUalquier cosa cuando encuentre uno lo pruebo y te digo" | D4; sin test con un tweet real de varios videos (no verificado). |
| 2026-11-17 | Pasó un link con video y uno con GIF; "Aprobada" | Estado: aprobada; D6 confirmada; los links se usan en P0. |
