# Weup Rescue v0.3 — Android Forensic Trace Scanner

Autor: **Weupperu.com**. Java, Android 8.0+, aplicación local, sin INTERNET ni telemetría.

## Qué hace

- **Escaneo forense del almacenamiento interno COMPARTIDO** del Redmi con permiso especial *Administrar todos los archivos*, habilitado explícitamente por la persona en ajustes (Android 11+). `InternalTraceScanner` recorre en solo lectura directorios accesibles, incluyendo ocultos, miniaturas, copias, respaldos y cachés visibles, e identifica JPG, PNG, GIF, WEBP y algunos HEIC por **cabeceras binarias**, aunque la extensión sea errónea o falte.
- Galería de fotografías con **miniaturas automáticas** calculadas fuera del hilo principal y caché LRU limitada, filtro por tipo/nombre; cada resultado se identifica como rastro/copia accesible o imagen existente. Miniatura y decodificación no garantizan integridad ni edad del borrado.
- Selección individual y múltiple y **guardar/exportar** copias mediante selector del sistema sin modificar el origen. Resultados de análisis por firma exportables desde caché temporal de la aplicación.
- Revisión de la papelera multimedia y listado de MediaStore y carpetas autorizadas, como métodos complementarios.
- Carving por firmas JPG, PNG, PDF y ZIP/Office desde una **imagen binaria preexistente y sin cifrar** elegida por el usuario, con máximo de caché de 512 MB y límite de 100 candidatos. No equivale a leer la memoria interna cifrada.
- Botón Detener, límites de inspección y exclusión de symlinks/directorios app-privados, sin red.

## Limitación forense fundamental

`MANAGE_EXTERNAL_STORAGE` no permite leer los sectores previamente borrados ni `/data`, y tampoco concede acceso a los directorios privados de otras apps en `/Android/data`. En el Redmi Note 13 Pro 5G, el cifrado de archivos, TRIM, borrado de claves o sobrescritura pueden hacer imposible recuperar las fotos originales eliminadas definitivamente hace 1–3 años. La aplicación busca rastros **todavía existentes y accesibles**, no promete recuperar bytes inaccesibles. Root/bootloader no se solicitan; no desbloquear el bootloader para probar, ya que puede borrar todos los datos.

## Instalación/compilación

JDK 17, Gradle 8.9, SDK Android 35. `gradle :app:assembleDebug` desde la carpeta `rescue` en GitHub. Pipeline de Actions `.github/workflows/weup-rescue-apk.yml` de la rama `weup-rescue-android`. No instalar en el único dispositivo que guarde restos críticos sin valorar primero el efecto de escribir nuevos datos; incluso instalar una APK genera escrituras.

## Pruebas de motores independientes

```
javac -d /tmp/weup-tests app/src/main/java/com/weupperu/rescate/InternalTraceScanner.java tests/InternalTraceScannerSmoke.java
java -cp /tmp/weup-tests InternalTraceScannerSmoke
javac -d /tmp/weup-tests app/src/main/java/com/weupperu/rescate/SignatureCarver.java tests/SignatureCarverSmoke.java
java -cp /tmp/weup-tests SignatureCarverSmoke
```

## Política de privacidad

Sin permiso de Internet. Nada se sube a servidores. La exploración nunca borra ni modifica los archivos originales. Todo resultado exportado se guarda únicamente tras elegir destino. Autoría: Weupperu.com.


## v0.3.1: estabilidad del escaneo interno
- Lista de resultados publicada mediante snapshots sincronizados para evitar ConcurrentModificationException.
- Galería paginada (45 por bloque): no crea cientos de vistas/decodificaciones simultáneas.
- Desduplicación de tareas de miniaturas; resultados devueltos a todas las vistas visibles.
- Actualización de progreso limitada; validaciones de permiso y acceso antes del escaneo.
- Mantiene escaneo de rastros accesibles, sin prometer lectura de bloques borrados y cifrados.

## v0.4 — Rastros, vídeo y filtros de fechas
- El modo interno solo informa de copias todavía existentes en rutas residuales/ocultas, y EXCLUYE fotos y vídeos ordinarios de DCIM, Pictures o Movies. No demuestra eliminación del original.
- Búsqueda de firmas JPG/PNG/GIF/WEBP/HEIC y MP4/MOV/MKV en las copias accesibles; vista de miniaturas automática, incluida la de vídeo si Android logra decodificar un fotograma.
- Selector desde/hasta y preselección «Hace 1–3 años». Primero se intenta EXIF/fecha de medios; si no existe, se usa la última modificación. NO es fecha de eliminación; los candidatos sin fecha se excluyen cuando hay filtro.
- Se conserva el carving sobre una imagen binaria SIN CIFRAR adquirida por separado. No se leen bloques borrados del almacenamiento interno cifrado con una APK convencional.
- Dos motores independientes con pruebas Java de exclusión de fotos y vídeos normales.
