# Weup Rescue v0.2 · Weupperu.com

Proyecto Android Java, Android 8.0+ (API 26), sin INTERNET, sin analítica, sin dependencias de terceros en tiempo de ejecución.

## Recuperación y límites importantes

**No existe una APK ordinaria capaz de hacer el equivalente a Recuva sobre los sectores borrados de la memoria interna cifrada de Android.** MediaStore/SAF dan acceso a archivos existentes y algunas papeleras, no al espacio libre. TRIM, sobrescritura y la pérdida de claves de cifrado pueden hacer una recuperación imposible incluso con root. No prometemos recuperar archivos eliminados definitivamente.

**Análisis profundo real de imagen de microSD:** usa `ACTION_OPEN_DOCUMENT` para seleccionar una imagen binaria (.img/.dd/.bin), creada previamente y de forma segura desde una tarjeta microSD EXTRAÍBLE sin cifrar. `SignatureCarver` lee secuencialmente los bytes de esa imagen sin escribir en la fuente y busca firmas de:

- JPEG: SOI a EOI, validación de dimensiones con BitmapFactory.
- PNG: cabecera a IEND, validación de dimensiones con BitmapFactory.
- PDF: `%PDF-` hasta `%%EOF`, vista previa de la primera página si el renderer puede decodificarlo.
- ZIP: firma local a fin de directorio central, validación con ZipFile; detecta DOCX, XLSX, PPTX por sus entradas internas.

Solo detecta candidatos completos **contiguos**; las firmas no prueban por sí solas que los archivos sean correctos. Los tamaños máximos de cada candidato son 64 MB para JPEG/PNG y 128 MB para PDF/ZIP; máximo 100 candidatos y 512 MB de caché interna por exploración. Para evitar pérdida o sobrescritura accidental, **se recomienda generar la imagen de una microSD en otro dispositivo** y guardar las exportaciones en otro destino. No puede analizar directamente la memoria interna ni leer el espacio libre de una carpeta seleccionada. No accede a dispositivos de bloque sin root, ni evade el cifrado.

## Pantalla de resultados

- Cada coincidencia lleva su procedencia: `Archivo existente`, `En papelera` o `Reconstruido por firma · verificar`.
- `Vista previa`: imágenes decodificadas, primera página PDF, lista de entradas ZIP/Office o muestra de texto; audio/vídeo existentes se abren con reproductor compatible de Android. La vista previa puede fallar para archivos cifrados o incompletos.
- `Guardar archivo ↓`: selector nativo de Android (`ACTION_CREATE_DOCUMENT`) para elegir Descargas, microSD accesible u otra ubicación. El origen se abre en modo lectura, el destino recibe copia independiente; no hay ninguna descarga desde un servidor. Papelera: restauración sujeta a autorización de Android.
- Indica desplazamiento de byte aproximado para imagen binaria, **no fecha inventada de eliminación**; búsqueda y filtrado por tipo/nombre; botón Detener durante el escaneo binario.
- La caché de resultados reconstruidos es temporal; guarda enseguida lo que te interese. Al iniciar otro análisis binario se descartan resultados temporales del anterior.

## Compilación

JDK 17, Android SDK 35, Gradle 8.9. Abrir carpeta en Android Studio y ejecutar `gradle :app:assembleDebug`. También hay workflow de GitHub Actions para el proyecto ubicado en `rescue/` dentro de la rama independiente `weup-rescue-android` del repositorio. APK generado: `app/build/outputs/apk/debug/app-debug.apk` (firma de depuración del compilador; distintas máquinas pueden producir distintas claves debug y Android podría requerir desinstalar la anterior versión antes de instalar esta).

Prueba unitaria sin Android: `javac -d /tmp/weup-test app/src/main/java/com/weupperu/rescate/SignatureCarver.java tests/SignatureCarverSmoke.java && java -cp /tmp/weup-test SignatureCarverSmoke`.

## Privacidad

Permisos mínimos de multimedia para consultar elementos existentes; exploración de carpetas/imágenes y exportación con el selector del sistema. El manifiesto no incluye permiso INTERNET. El programa no hace root, no escribe en imágenes fuente ni elimina archivos originales. Autor: Weupperu.com.