# CLAUDE.md

## Principios (valen siempre, en cualquier proyecto)

1. **No supongas, verificá.** Todo lo que afirmes sobre el código, las APIs, las librerías o los requisitos externos tiene que salir de algo real: el código del repo, la documentación oficial vigente, el resultado de ejecutar algo. Si no pudiste verificarlo, decilo explícitamente ("no verificado").
2. **Ante la duda, preguntá.** Si una decisión es de producto, de alcance o de preferencia mía, preguntame en lugar de decidir. Lo que se puede deducir del código o de la documentación no se pregunta, se busca.
3. **Primero el plan, después la ejecución.** No escribas código de producción hasta que yo apruebe explícitamente el plan o la spec. Explorar, leer, probar ideas descartables y escribir el plan sí está permitido antes.
4. **Lo más simple que resuelva el problema.** Nada de sobreingeniería. Si algo es valioso pero grande, va a "No entra" y se anota para después.
5. **Honestidad sobre el estado real.** Al terminar cualquier tarea decime qué verificaste, cómo, y qué NO verificaste. No des por hecho que algo funciona si no lo probaste.
6. **Respondé en español.**

## Flujo de trabajo (sin saltear pasos)

### 0. Contexto, antes de proponer nada
- Leé lo que te paso (historia, idea, documento) y los archivos de convenciones del repo (README, AGENTS.md, docs de arquitectura o permisos, si existen).
- Revisá el código real de la rama base: qué ya existe, qué se puede reutilizar, dónde engancharía cada cosa. Nombrá archivos, funciones y endpoints reales, no supuestos.
- Si dependemos de algo externo (API de terceros, librería, requisitos de una plataforma), consultá la documentación actual: tu conocimiento puede estar desactualizado.
- Si te paso un documento de otra persona, analizalo críticamente contra el código y contra el tamaño de la tarea.

### 1. Propuesta simple + preguntas
- Proponé la versión más chica que resuelva el problema.
- Decí qué tomarías y qué dejarías para después, con el motivo.
- Hacé solo las preguntas que cambian el diseño (entre 4 y 6), cada una con tu recomendación.
- No escribas la spec ni código hasta que responda.

### 2. Spec / plan escrito
Guardalo en la carpeta de specs del proyecto (por defecto `docs/specs/<ID>-<titulo-corto>.md`). Si esa carpeta está en `.gitignore`, versionala con `git add -f`. Creá la rama de trabajo desde la rama base antes de tocar nada (formato según la sección específica del proyecto).

Estructura (omití lo que no aplique, pero decí que lo omitiste):
- Encabezado: estado (borrador / aprobada, por quién y cuándo), componentes o repos que toca, historias o tareas.
- 1. Qué problema resuelve.
- 2. Historia(s) de usuario.
- 3. Alcance: Entra / No entra (y adónde va lo que no entra).
- 4. Decisiones: tabla D1, D2… con quién decidió. Las que proponés vos van marcadas "Propuesta, a confirmar".
- 5. Reglas de negocio numeradas.
- 6. Flujo (texto o diagrama ASCII).
- 7. Criterios de aceptación AC-1… en formato Dado / Cuando / Entonces, agrupados.
- 8. Seguridad, permisos y privacidad (quién puede qué, qué datos se tocan, qué se valida). Seguir el checklist del proyecto si existe.
- 9. Modelo de datos y migraciones (aditivas, reversibles cuando sea posible).
- 10. Contratos de API / interfaces, y 11. Pantallas, si aplica.
- 12. Plan de trabajo por partes, con tests primero.
- 13. Riesgos. 14. Preguntas abiertas. 15. Historial de comentarios (comentario literal → cómo se resolvió).

Cuando esté lista, avisame para que la comente. No la commitees todavía.

### 3. Revisión
- Yo comento la spec (en el archivo). Aplicá cada comentario, registralo en "Historial de comentarios" y actualizá decisiones, reglas y criterios afectados.
- Recién con mi OK explícito: commit de la spec y empezás a implementar.

### 4. Implementación
- Por partes, un commit por parte, en el orden del plan. Tests primero: cada criterio de aceptación tiene al menos un test.
- Corré tests, typecheck y lint del proyecto antes de cada commit.
- Actualizá el CHANGELOG (si el proyecto tiene uno) en el mismo commit que el código, explicando el porqué.
- Si algo de la spec cambia durante la implementación, actualizá la spec; no la dejes desactualizada.
- No commitees cada ajuste chico de UI: iterá y commiteá cuando yo confirme.
- Si aparece algo no previsto que cambia el alcance o el diseño, frená y preguntame.

### 5. Verificación y entrega
- Probalo en vivo cuando se pueda (entorno local, nunca producción) y decime qué verificaste y qué NO.
- Si el proyecto usa PRs: una por repo, con la plantilla del proyecto, enlazando la spec, e incluyendo "Cómo se probó" y "Lo que no está verificado".

### 6. Auditoría antes de pedir revisión
- Revisá todo el cambio contra la spec y las convenciones: bugs, carreras, permisos, textos, código muerto, conflictos con la rama base. Confirmá con pruebas reales lo que sospechás antes de afirmarlo.
- Corregí, integrá la rama base y volvé a correr todo. Después respondé los comentarios de los revisores uno por uno.

## Tareas chicas
Para cambios triviales (typo, ajuste de texto, bug evidente de una línea) no hace falta spec completa: describí en pocas líneas qué vas a cambiar, esperá mi OK y ejecutá. Ante cualquier duda sobre si es "chica", tratala como grande.

## Convenciones generales
- Ramas: `feature/<ID>-<Titulo_corto>` desde la rama base (ajustable abajo).
- Commits: seguí el formato que ya use el repo (mirá `git log`); si no hay, `[ADD]`, `[FIX]`, `[DOCS]`, `[REFACTOR]`, `[TEST]`.
- Nunca commitees secretos, claves ni archivos de entorno.
- Nunca hagas acciones destructivas o irreversibles (borrar datos, force push, migraciones en producción) sin confirmación mía.

---

## Específico del proyecto (completar en cada repo)

### Proyecto: app Android de stickers para WhatsApp (sin anuncios)
- **Objetivo:** app nativa Android, liviana y rápida, para crear stickers estáticos y animados y agregarlos a WhatsApp. Flujo mínimo: elegir foto, recortar, guardar, agregar. Sin anuncios.
- **Uso:** personal al inicio (APK directo); eventualmente Play Store gratis, con donaciones por enlace externo y posiblemente open source.
- **Rama base:** `main` (cambiar si corresponde). **Stack:** Kotlin, Jetpack Compose, ContentProvider propio, libwebp por NDK o FFmpeg para animados (verificar qué fork de FFmpeg sigue mantenido; ffmpeg-kit fue discontinuado).
- **Integración WhatsApp:** packs de terceros vía ContentProvider + Intent `com.whatsapp.intent.action.ENABLE_STICKER_PACK`; base: repo oficial WhatsApp/stickers. Soportar también WhatsApp Business. Probar siempre en dispositivo real: WhatsApp falla en silencio.
- **Requisitos de stickers (verificar contra la doc actual antes de implementar):** WebP 512x512; estáticos ≤ 100 KB; animados ≤ 500 KB, hasta 10 s, mín. 8 ms por frame; packs de 3 a 30 stickers con ícono de bandeja PNG 96x96 ≤ 50 KB; no se mezclan estáticos y animados en un pack; subir la versión del pack al cambiarlo.
- **Decisión de UX tomada:** "pack personal que se va llenando": cada sticker nuevo se suma al pack actual (con bump de versión) y al llegar a 30 se crea otro pack. Packs estáticos y animados separados.
- **Hoja de ruta:** 1) estáticos + integración WhatsApp + pack que se llena; 2) quitar fondo con ML Kit; 3) animados.
- **Comandos** (en Windows, `.\gradlew.bat` en lugar de `./gradlew`; requieren JDK 17 y `local.properties` con `sdk.dir`):
  - Build: `./gradlew assembleDebug` (APK en `app/build/outputs/apk/debug/`).
  - Tests unitarios (JVM): `./gradlew test`.
  - Tests instrumentados (teléfono conectado por USB): `./gradlew connectedAndroidTest`.
  - Lint: `./gradlew lint`.
  - Instalar en el teléfono: `./gradlew installDebug` (o `adb install -r <apk>`; `adb` está en `%LOCALAPPDATA%\Android\Sdk\platform-tools`).
- **Versiones de build:** AGP 9.4.1 (el máximo que soporta Android Studio 2026.1.4), Gradle 9.6.1, Kotlin 2.4.21 (Kotlin integrado en AGP 9, sin `kotlin-android`), Compose BOM 2026.09.00 (exige `compileSdk 37`). Al subir AGP, verificar la tabla de compatibilidad con la versión de Android Studio instalada.

### Primer pedido
Siguiendo el flujo de arriba, empezá por el paso 0 y el paso 1 para la hoja de ruta, parte 1 (estáticos + integración con WhatsApp + pack que se llena). No escribas código hasta que apruebe el plan.