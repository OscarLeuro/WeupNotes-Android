# WeupNotes — app Android (base web v1.0.8)

**Autor y propietario del proyecto: Weupperu.com**

Este proyecto crea un instalador Android (APK) conectado a `https://notas.weupdevelopers.com/`.
Usa la misma app, usuarios, sesiones, libretas y notas del servidor web. La etiqueta de
versión 1.0.8 hace referencia a la base web declarada para preparar este proyecto. El
contenido que muestra siempre es **la versión que esté efectivamente desplegada** en
el servidor: la APK no contiene el código ni una instantánea offline de WeupNotes.

## Funciones nativas incluidas

- Icono y lanzador WeupNotes; ejecución a pantalla completa de contenido (sin barra del navegador).
- Navegación y atrás de Android, sesión persistente mediante cookies y almacenamiento web.
- Selector de archivos para adjuntos; permisos de micrófono/cámara solicitados solo cuando
  la página de confianza los necesita.
- Barra de carga; mensaje y botón Reintentar para errores de conexión.
- Enlaces externos abiertos con sus apps; HTTPS obligatorio para el servidor principal.
- Descarga de archivos HTTPS con sesión autenticada, mediante DownloadManager.

**Limitaciones conocidas:** requiere Internet para consultar/modificar notas; no incorpora
caché offline ni sincronización nativa independiente. El diseño móvil es el CSS responsive
que sirva la web. Las descargas `blob:` generadas solo en memoria por JavaScript no son
compatibles con DownloadManager. No se ha validado la app contra el servidor en vivo ni
contra un dispositivo físico desde este entorno de preparación.

## Compilación sencilla con Android Studio

1. Instalar Android Studio reciente y abrir esta carpeta (la que contiene `settings.gradle`).
2. Permitir que descargue Android SDK 35, Android Gradle Plugin 8.7.3 y Gradle 8.9.
3. Usar JDK 17.
4. Menú **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK en `app/build/outputs/apk/debug/app-debug.apk`. La variante debug se firma
   automáticamente al compilar y puede instalarse en Android 8.0 o superior.

## Compilación online: GitHub Actions (no exige instalar Android Studio)

1. Crear un repositorio en GitHub y subir el **contenido** de esta carpeta a la raíz;
   incluir `.github/workflows/crear-apk.yml` (es carpeta oculta).
2. En pestaña **Actions**, elegir **Compilar WeupNotes Android APK**, luego **Run workflow**.
3. Al finalizar, abrir la ejecución y descargar el artefacto
   **WeupNotes-v1.0.8-Android-APK**. El archivo ZIP de Actions contiene el APK firmado
   automáticamente para instalación directa en Android.
4. En el teléfono, abrir el APK y autorizar instalaciones desde esa fuente si se solicita.

Alternativamente en una máquina con SDK Android y Gradle 8.9 configurados, ejecutar
`gradle :app:assembleDebug` desde esta carpeta.

## Publicación / firma de distribución

El APK debug es instalable, pero **no** es apropiado como versión definitiva de tienda.
Para publicar, generar un keystore propio y Build > Generate Signed Bundle / APK en
Android Studio, y custodiar la clave privada. No se incluyen contraseñas ni certificados
privados. El package id es `com.weupperu.weupnotes`; para futuras actualizaciones
mantener la misma firma y aumentar `versionCode`.

## Cambios de dominio

Si cambia el dominio oficial, actualizar las constantes `HOME` y `TRUSTED_HOST` en
`app/src/main/java/com/weupperu/weupnotes/MainActivity.java` y compilar una versión nueva.

## Condiciones de seguridad

No se guarda ningún password en el código. Login y notas provienen de la web por HTTPS.
Solo el dominio oficial se abre dentro del WebView. Cámara/micrófono no se conceden a
otros orígenes. Debug WebView se activa únicamente en compilaciones de desarrollo.

## Android v1.1.1 — interfaz y modo sin conexión
- El botón radial se integra en la web y clona los iconos disponibles en el menú lateral (SVG/iconos). La forma/color hereda CSS de tema activo; si un icono no aparece en el DOM se usa un sustituto vectorial. Los iconos son de 31 px.
- Se eliminó el acceso permanente «Locales»; ante una caída de la carga de la web se abre directamente la vista de notas y su editor nativo con autoguardado cifrado.
- Topbar: «X cambios sin guardar» cuenta borradores locales pendientes de confirmar en el servidor. También aparece cuando se está sin conexión. Pulsarlo no afirma una sincronización inexistente.
- **Limitación importante**: sin el ZIP completo del backend web y la API de sincronización versionada, el estado pendiente no puede subir cambios a PostgreSQL. Los borradores se preservan y pueden copiarse o compartirse; no se puede editar offline una nota remota que no ha sido descargada.
- **Firma**: la compilación de depuración de GitHub utiliza firma temporal del runner; no sustituye una clave de firma estable. Antes de desinstalar la versión anterior, exportar sus borradores: Android los elimina al desinstalar.
- Autoría: Weupperu.com.
