# Weup Rescue — buscador y rescatador responsable de archivos (Android)

Autor: Weupperu.com · Versión 0.1.0 · Android 8.0+ (API 26)

## Qué hace realmente

- Consulta los archivos que Android mantiene en la papelera multimedia (Android 11+, si Android concede acceso) y solicita autorización del sistema antes de restaurarlos.
- Examina fotos, vídeos y audio **que todavía existen** y a los que se tiene permiso de acceso.
- Recorre una carpeta elegida por el usuario mediante el selector de documentos de Android; localiza archivos antiguos, subcarpetas y nombres ocultos (que empiezan con un punto) que sean accesibles.
- Filtra por nombre y tipo, muestra fecha de última modificación (NO fecha de eliminación) y permite guardar una copia en un destino elegido por el usuario.
- Sin conexión a Internet, sin rastreadores, sin carga de datos a terceros, sin acceso root ni permiso de acceso completo al almacenamiento.

## Lo que NO hace

No es un análisis forense del espacio libre ni puede acceder a bloques borrados del chip de almacenamiento interno. No puede recuperar archivos sobrescritos, invalidados por TRIM, cuyas claves de cifrado se han perdido ni elementos a los que Android no permite acceso. Tampoco enumera todas las papeleras privadas de aplicaciones (Google Fotos, WhatsApp, gestores de archivos, etc.). Por tanto, no asegura recuperar archivos eliminados definitivamente hace meses o años. En Android 11+ el selector impide escoger determinadas raíces, Downloads y Android/data; son restricciones del SO, no fallos del proyecto.

## Cómo generar el APK

**Opción A — GitHub Actions:** crear un repositorio propio, subir el CONTENIDO de esta carpeta a su raíz, ir a Actions > Compilar APK de Weup Rescue > Run workflow. En el trabajo terminado, descargar el artefacto `WeupRescue-APK` y extraer `app-debug.apk`. El APK debug está firmado automáticamente para instalar y probar; no es una firma de distribución en Play Store.

**Opción B — Android Studio:** File > Open > esta carpeta, usar JDK 17, dejar descargar Android SDK 35 y Gradle/Android Gradle Plugin; seleccionar Build > Build Bundle(s) / APK(s) > Build APK(s). Archivo resultante: `app/build/outputs/apk/debug/app-debug.apk`.

**Línea de comandos** (si ya está instalado Gradle 8.9, SDK 35 y JDK 17): `gradle :app:assembleDebug`.

## Uso

1. Conceder solo los permisos de fotos/vídeos/audio que se deseen examinar.
2. Buscar en «Papelera Android» (sujeto al acceso que concede el sistema), «Multimedia actual» o «Elegir carpeta».
3. Tocar una coincidencia. Si está en la papelera, pulsar «Restaurar» y aprobar en el diálogo de Android. En otro caso, usar «Guardar una copia»; Android deja escoger dónde guardarla.
4. Para archivos perdidos de hace mucho tiempo: dejar de usar el dispositivo de origen, revisar copias de Google Fotos/Drive y papeleras de apps. Para una microSD extraíble, examinarla desde una computadora sin escribir datos nuevos sobre ella.

## Privacidad

La búsqueda y copia se realizan en el dispositivo. No se declara permiso INTERNET. El código no envía datos a un servidor. La elección de carpetas y destinos se hace con el sistema Android.

## Notas técnicas

Implementación Java nativa (sin librerías de terceros de interfaz), MediaStore y Storage Access Framework. Límite por exploración: 700 coincidencias y profundidad de 9 niveles; se puede ajustar en `MainActivity.java`. El listado muestra **fecha de modificación**, no fecha del borrado. Algunas capas de fabricantes restringen aún más la papelera o no admiten la consulta de elementos de otras aplicaciones: se informa al usuario si la lectura falla.