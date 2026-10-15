# WeupNotes Android v1.1.2

**Autor: Weupperu.com**

Aplicación Android conectada a `https://notas.weupdevelopers.com/`. Todo el contenido y las sesiones pertenecen a la aplicación web publicada; la aplicación Android no implementa una base de datos de notas alternativa.

## Cambios de v1.1.2

- Eliminados `OfflineStore`, `OfflineActivity`, el editor y guardado de borradores del teléfono, el contador/topbar de cambios pendientes y la lógica de sincronización local. Si se interrumpe la conexión, se muestra únicamente una pantalla con **Reintentar**. No se crean notas sin conexión.
- Rueda de cuatro acciones: Nota, Espacio, Libreta y Buscar. Los botones secundarios bajan de 78 px a **60 px**; iconos a 27 px para conservar su legibilidad. Al disponer de los iconos reales del menú lateral los reutiliza; de otro modo emplea SVG de respaldo.
- El color se recalcula en vivo desde la selección de apariencia, su valor persistido y los colores realmente aplicados a la web, en lugar de imponer rosa. En ausencia de información sobre el tema, el botón utiliza un neutro `#40536b`.
- El widget de pantalla de inicio se conserva como tres accesos **solo online**: Nueva nota, Buscar y Abrir. No muestra ni almacena notas, borradores ni contadores locales.

## Instalar y compilar

Se requieren Android SDK 35, Gradle 8.9 y JDK 17. Ejecutar `gradle --no-daemon :app:assembleDebug` o usar el workflow `.github/workflows/crear-apk.yml` en GitHub Actions. APK de prueba en `app/build/outputs/apk/debug/app-debug.apk`. Compatible con Android 8.0+.

**Firma:** las compilaciones debug de los runners de GitHub pueden utilizar claves distintas. Antes de desinstalar una versión 1.1.0 o 1.1.1, exportar desde la versión anterior cualquier borrador local que se quiera conservar: Android borra los datos de la aplicación al desinstalarla y esta nueva versión retira el lector de borradores.

## Comportamiento y seguridad

- La creación y modificación de notas depende exclusivamente de la conexión al servidor y de los controles de la web. No se inventan endpoints ni se declaran sincronizaciones que no existan.
- WebView restringido al dominio de confianza HTTPS, enlaces externos a aplicaciones externas, permisos de cámara/micrófono bajo demanda y descargas mediante DownloadManager.
- La interfaz y el color del tema dependen de las propiedades y los elementos que publique la web. Los temas y controles del servidor no se modifican con este APK.
- No hay claves privadas ni contraseñas embebidas. Para publicar una app actualizable a largo plazo conviene configurar una clave de firma propia, fuera del repositorio.
