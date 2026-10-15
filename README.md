# WeupNotes Android v1.1.0 (vista web + captura offline)

Autoría y producto: **Weupperu.com**.

La pantalla web está conectada exclusivamente por HTTPS a `https://notas.weupdevelopers.com/`. Conserva inicio de sesión, perfiles y libretas de la versión que esté desplegada en el servidor. No se distribuyen contraseñas ni claves privadas.

## Novedades Android v1.1.0

- Menú rápido flotante inferior derecho con 4 acciones en arco: Nota, Espacio, Libreta y Buscar. Pulsación normal: abre la rueda, se toca una opción. Pulsación sostenida: deslizar el dedo hasta una opción y soltar para ejecutarla. Botón atrás o tocar fuera cierra la rueda. Atributos accesibles para TalkBack.
- Cuando la web está cargada, el botón intenta activar los controles visibles del sitio mediante etiquetas accesibles y texto; la página también recibe `weupnotes:native-action` con `detail.action`: `note`, `space`, `notebook` o `search` para que la versión web implemente un enlace estable al integrarse. **No se inventan URLs ni llamadas POST al servidor**. Si no se reconoce una opción, el usuario ve el aviso correspondiente, o pasa a captura/búsqueda local para Nota/Buscar.
- Captura y edición de borradores **locales** incluso sin Internet. Almacenamiento cifrado mediante Android Keystore, AES-256-GCM y archivo de escritura atómica; título/cuerpo, guardar, volver a editar, buscar, copiar, compartir y eliminar con confirmación. Los borradores se distinguen claramente de notas de servidor.
- Acceso "Locales" en la app, incluido en pantalla de fallo de conexión.
- Widget de inicio 4×2: cuenta de notas locales, captura rápida, buscar y ver todas. El título de la última nota aparece **solo si la persona activa voluntariamente la vista previa** en la pantalla Mis notas locales; inicialmente se oculta por privacidad.

## Alcance REAL de la sincronización

**Esta versión no sincroniza aún los borradores locales con las notas del servidor y tampoco descarga notas del servidor para editarlas offline.** El código entregado inicialmente era solo un contenedor WebView y no incluía el backend, su API, rutas de autenticación, modelo de versiones ni sincronización. No se envían datos a rutas supuestas ni se borran borradores bajo una falsa confirmación. Para completar sincronización bidireccional es imprescindible adaptar y verificar la última versión COMPLETA del backend WeupNotes, no solo cambiar el APK. Consulta `CONTRATO-SINCRONIZACION.md`.

Los borradores locales permanecen **solo en este dispositivo** y no forman parte del backup automático (`allowBackup=false`); se perderán si desinstalas la app o borras su almacenamiento. Copiar o compartir permite extraerlos voluntariamente. No desinstales la app cuando contenga borradores sin exportar. No se garantizan acciones online automatizadas hasta integrar eventos estables en la web.

## Compilar

Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, SDK 35. `gradle :app:assembleDebug`. El flujo GitHub Actions genera el APK debug verificando que exista y adjuntándolo en los artefactos de cada ejecución. Android 8.0+ (`minSdk 26`). Identificador `com.weupperu.weupnotes`, `versionCode 110`.

**Firma:** la firma debug de runners distintos puede cambiar de compilación a compilación. Si Android señala conflicto de firma al actualizar, antes de desinstalar conserva/exporta tus borradores. Para versiones de distribución configurar un keystore privado estable por GitHub Secrets, sin subirlo al repositorio. No se garantiza la sustitución directa del APK 1.0.8 de pruebas.

## Protección

WebView solo carga la URL oficial dentro de la app. Sin SSL inválido, navegación externa en navegador, solicitud de permisos cámara/micrófono únicamente bajo el dominio de confianza y solo cuando se necesitan. Archivo local AES-GCM, sin respaldo automático y sin vista previa de widget por defecto.
