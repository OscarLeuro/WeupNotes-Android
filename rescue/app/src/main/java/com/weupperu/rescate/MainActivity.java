package com.weupperu.rescate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Weup Rescue v0.1 — Weupperu.com
 * Finds media still present, accessible files and media items retained in Android's trash.
 * It does NOT perform raw-sector deleted-file recovery.
 */
public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 501;
    private static final int REQ_TREE = 502;
    private static final int REQ_SAVE = 503;
    private static final int REQ_RESTORE = 504;
    private static final int MAX_RESULTS = 700;
    private static final int MAX_DEPTH = 9;

    private static final int BG = Color.rgb(16, 18, 27);
    private static final int CARD = Color.rgb(29, 32, 44);
    private static final int FG = Color.rgb(246, 246, 251);
    private static final int MUTED = Color.rgb(173, 178, 192);
    private static final int ACCENT = Color.rgb(233, 33, 118);
    private static final int BORDER = Color.rgb(53, 57, 73);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<FoundItem> found = new ArrayList<>();
    private final Set<String> knownUris = new HashSet<>();
    private LinearLayout resultList;
    private TextView status, resultCount;
    private EditText searchInput;
    private Spinner typeFilter;
    private Button trashButton, mediaButton, folderButton;
    private boolean busy = false;
    private boolean permissionPromptInProgress = false;
    private boolean permissionRequestedOnce = false;
    private ScanMode permissionMode = ScanMode.CURRENT;
    private FoundItem pendingCopy;
    private ScanMode lastMode = ScanMode.CURRENT;

    private enum ScanMode { TRASH, CURRENT, FOLDER }

    private static class FoundItem {
        Uri uri;
        String name, mime, source, location;
        long size, modified;
        boolean trashed, hidden;
        FoundItem(Uri uri, String name, String mime, String source, String location,
                  long size, long modified, boolean trashed, boolean hidden) {
            this.uri = uri;
            this.name = name;
            this.mime = mime;
            this.source = source;
            this.location = location;
            this.size = size;
            this.modified = modified;
            this.trashed = trashed;
            this.hidden = hidden;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        makeUi();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable background(int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    private void addSpace(LinearLayout box, int h) {
        View v = new View(this);
        box.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private Button button(String title, int color) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextColor(FG);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setBackground(background(color, color == ACCENT ? 0 : BORDER, 12));
        b.setMinHeight(dp(48));
        return b;
    }

    private void makeUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(20), dp(18), dp(32));
        scroll.addView(root);
        setContentView(scroll);

        TextView brand = text("WEUP  /  RESCUE", 12, ACCENT, true);
        root.addView(brand);
        addSpace(root, 10);
        TextView title = text("Encuentra lo que aún\npuede recuperarse.", 29, FG, true);
        title.setLineSpacing(dp(1), 1.03f);
        root.addView(title);
        addSpace(root, 10);
        root.addView(text("Busca elementos de la papelera de Android, archivos existentes y copias antiguas u ocultas dentro de carpetas autorizadas.", 14, MUTED, false));
        addSpace(root, 18);

        LinearLayout warning = vertical();
        warning.setBackground(background(CARD, BORDER, 13));
        warning.setPadding(dp(14), dp(13), dp(14), dp(13));
        warning.addView(text("IMPORTANTE", 11, ACCENT, true));
        addSpace(warning, 5);
        warning.addView(text("No analiza sectores borrados ni recupera datos sobrescritos. Una fecha antigua indica última modificación, NO fecha de eliminación.", 13, FG, false));
        root.addView(warning);
        addSpace(root, 18);

        trashButton = button("♻  Revisar papelera Android", ACCENT);
        trashButton.setOnClickListener(v -> startMedia(ScanMode.TRASH));
        root.addView(trashButton, new LinearLayout.LayoutParams(-1, dp(54)));
        addSpace(root, 8);
        mediaButton = button("▧  Buscar multimedia existente", CARD);
        mediaButton.setOnClickListener(v -> startMedia(ScanMode.CURRENT));
        root.addView(mediaButton, new LinearLayout.LayoutParams(-1, dp(52)));
        addSpace(root, 8);
        folderButton = button("▣  Elegir carpeta y buscar copias", CARD);
        folderButton.setOnClickListener(v -> chooseFolder());
        root.addView(folderButton, new LinearLayout.LayoutParams(-1, dp(52)));
        addSpace(root, 8);
        Button infoButton = button("ⓘ  Consejos para archivos muy antiguos", CARD);
        infoButton.setOnClickListener(v -> showGuide());
        root.addView(infoButton, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(root, 20);

        status = text("Selecciona un tipo de exploración. No se modifica ni elimina ningún archivo.", 13, MUTED, false);
        root.addView(status);
        addSpace(root, 14);
        LinearLayout hr = vertical();
        hr.setBackgroundColor(BORDER);
        root.addView(hr, new LinearLayout.LayoutParams(-1, dp(1)));
        addSpace(root, 12);
        resultCount = text("RESULTADOS · 0", 12, FG, true);
        root.addView(resultCount);
        addSpace(root, 10);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setHint("Filtrar por nombre…");
        searchInput.setHintTextColor(MUTED);
        searchInput.setTextColor(FG);
        searchInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchInput.setBackground(background(CARD, BORDER, 10));
        root.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(45)));
        addSpace(root, 7);
        typeFilter = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("Todos los tipos", "Imágenes", "Vídeos", "Audio", "Documentos", "Otros", "Nombres ocultos"));
        typeFilter.setAdapter(adapter);
        typeFilter.setBackground(background(CARD, BORDER, 10));
        root.addView(typeFilter, new LinearLayout.LayoutParams(-1, dp(45)));
        addSpace(root, 12);
        resultList = vertical();
        root.addView(resultList);
        addSpace(root, 14);
        TextView footer = text("Privacidad: exploración local y con permisos. Sin conexión a Internet.  ·  Weupperu.com", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        typeFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { render(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private String[] missingMediaPermissions() {
        if (Build.VERSION.SDK_INT < 23) return new String[0];
        List<String> ask = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            String[] media = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            };
            for (String permission : media)
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) ask.add(permission);
            if (Build.VERSION.SDK_INT >= 34
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED) {
                ask.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return ask.toArray(new String[0]);
    }

    private void startMedia(ScanMode mode) {
        if (busy || permissionPromptInProgress) return;
        if (mode == ScanMode.TRASH && Build.VERSION.SDK_INT < 30) {
            message("La papelera multimedia requiere Android 11 o superior. Prueba la exploración de carpetas.");
            return;
        }
        String[] missing = missingMediaPermissions();
        if (missing.length > 0 && !permissionRequestedOnce) {
            permissionRequestedOnce = true;
            permissionMode = mode;
            permissionPromptInProgress = true;
            requestPermissions(missing, REQ_PERMISSIONS);
            return;
        }
        scanMedia(mode);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == REQ_PERMISSIONS) {
            permissionPromptInProgress = false;
            boolean atLeastOneGranted = false;
            for (int result : grants) if (result == PackageManager.PERMISSION_GRANTED) atLeastOneGranted = true;
            if (atLeastOneGranted) scanMedia(permissionMode);
            else message("Sin permiso de lectura no se puede explorar multimedia. Puedes elegir una carpeta igualmente.");
        }
    }

    private void chooseFolder() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(i, REQ_TREE); }
        catch (Exception e) { message("Tu dispositivo no permite abrir el selector de carpetas."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try { getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) { /* Some providers only give temporary access. */ }
            scanFolder(tree);
        } else if (requestCode == REQ_SAVE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (pendingCopy != null) copyTo(pendingCopy, data.getData());
            pendingCopy = null;
        } else if (requestCode == REQ_SAVE) {
            pendingCopy = null;
        } else if (requestCode == REQ_RESTORE) {
            if (resultCode == RESULT_OK) {
                message("Android confirmó la restauración del elemento.");
                scanMedia(ScanMode.TRASH);
            } else {
                message("Restauración cancelada o no autorizada.");
            }
        }
    }

    private void setBusy(boolean value, String label) {
        busy = value;
        trashButton.setEnabled(!value);
        mediaButton.setEnabled(!value);
        folderButton.setEnabled(!value);
        status.setText(label);
    }

    private void reset() {
        found.clear(); knownUris.clear();
        render();
    }

    private synchronized boolean add(FoundItem item) {
        if (found.size() >= MAX_RESULTS) return false;
        if (!knownUris.add(item.uri.toString())) return true;
        found.add(item);
        return found.size() < MAX_RESULTS;
    }

    private void scanMedia(ScanMode mode) {
        if (busy) return;
        lastMode = mode;
        reset();
        setBusy(true, mode == ScanMode.TRASH ? "Consultando papelera multimedia…" : "Buscando archivos multimedia todavía existentes…");
        new Thread(() -> {
            int failed = 0;
            ContentResolver resolver = getContentResolver();
            String volume = Build.VERSION.SDK_INT >= 29 ? MediaStore.VOLUME_EXTERNAL : "external";
            List<Uri> collections = new ArrayList<>();
            collections.add(MediaStore.Images.Media.getContentUri(volume));
            collections.add(MediaStore.Video.Media.getContentUri(volume));
            collections.add(MediaStore.Audio.Media.getContentUri(volume));
            if (Build.VERSION.SDK_INT >= 29) collections.add(MediaStore.Downloads.getContentUri(volume));
            for (Uri collection : collections) {
                try {
                    queryCollection(resolver, collection, mode == ScanMode.TRASH);
                } catch (Exception e) { failed++; }
                if (found.size() >= MAX_RESULTS) break;
            }
            int failedFinal = failed;
            mainHandler.post(() -> {
                Collections.sort(found, Comparator.comparingLong(i -> i.modified == 0 ? Long.MAX_VALUE : i.modified));
                render();
                String summary = found.size() + " coincidencias accesibles. ";
                if (mode == ScanMode.TRASH) summary += "Solo incluye elementos de papelera que Android deja consultar. ";
                else summary += "Estos archivos todavía existen; no representan archivos eliminados definitivamente. ";
                if (failedFinal > 0) summary += failedFinal + " colección(es) restringidas por Android. ";
                if (found.size() == MAX_RESULTS) summary += "Límite de " + MAX_RESULTS + " resultados alcanzado.";
                setBusy(false, summary);
            });
        }, "weup-media-scan").start();
    }

    private void queryCollection(ContentResolver resolver, Uri collection, boolean trash) {
        List<String> cols = new ArrayList<>(Arrays.asList(
                MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE));
        if (Build.VERSION.SDK_INT >= 29) cols.add(MediaStore.MediaColumns.RELATIVE_PATH);
        if (Build.VERSION.SDK_INT >= 30) cols.add(MediaStore.MediaColumns.IS_TRASHED);
        Cursor c;
        if (Build.VERSION.SDK_INT >= 30) {
            Bundle args = new Bundle();
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, trash ? MediaStore.MATCH_ONLY : MediaStore.MATCH_EXCLUDE);
            args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.MediaColumns.DATE_MODIFIED + " ASC");
            args.putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_RESULTS);
            c = resolver.query(collection, cols.toArray(new String[0]), args, null);
        } else {
            c = resolver.query(collection, cols.toArray(new String[0]), null, null, MediaStore.MediaColumns.DATE_MODIFIED + " ASC");
        }
        if (c == null) return;
        try (Cursor cursor = c) {
            int id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int mime = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int modified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
            int location = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
            int trashed = cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED);
            while (cursor.moveToNext()) {
                long itemId = cursor.getLong(id);
                String itemName = safe(cursor.getString(name), "archivo_" + itemId);
                String type = safe(cursor.getString(mime), "application/octet-stream");
                Uri itemUri = Uri.withAppendedPath(collection, String.valueOf(itemId));
                long epochSeconds = cursor.isNull(modified) ? 0 : cursor.getLong(modified);
                long itemSize = cursor.isNull(size) ? -1 : cursor.getLong(size);
                String path = location >= 0 ? safe(cursor.getString(location), "Almacenamiento multimedia") : "Almacenamiento multimedia";
                boolean isTrash = trashed >= 0 && cursor.getInt(trashed) != 0;
                FoundItem item = new FoundItem(itemUri, itemName, type,
                        trash ? "Papelera multimedia" : "Multimedia existente", path,
                        itemSize, epochSeconds > 0 ? epochSeconds * 1000L : 0, trash || isTrash,
                        itemName.startsWith("."));
                if (!add(item)) break;
            }
        }
    }

    private void scanFolder(Uri treeUri) {
        if (busy) return;
        lastMode = ScanMode.FOLDER;
        reset();
        setBusy(true, "Revisando carpetas autorizadas. No se escribirá ningún dato en ellas…");
        new Thread(() -> {
            String error = null;
            try {
                String rootId = DocumentsContract.getTreeDocumentId(treeUri);
                walkTree(treeUri, rootId, "Carpeta elegida", 0, new HashSet<>());
            } catch (Exception e) { error = e.getMessage(); }
            String fail = error;
            mainHandler.post(() -> {
                Collections.sort(found, Comparator.comparingLong(i -> i.modified == 0 ? Long.MAX_VALUE : i.modified));
                render();
                String message = found.size() + " archivos existentes encontrados. Los nombres que comienzan por punto se marcan como ocultos.";
                if (found.size() == MAX_RESULTS) message += " Se alcanzó el límite de exploración.";
                if (fail != null) message += " Algunas ubicaciones no fueron accesibles: " + fail;
                setBusy(false, message);
            });
        }, "weup-folder-scan").start();
    }

    private void walkTree(Uri tree, String parentId, String path, int depth, Set<String> visited) {
        if (depth > MAX_DEPTH || found.size() >= MAX_RESULTS || !visited.add(parentId)) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] cols = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        Cursor c = null;
        try {
            c = getContentResolver().query(children, cols, null, null, null);
            if (c == null) return;
            int idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
            int modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            List<String[]> dirs = new ArrayList<>();
            while (c.moveToNext() && found.size() < MAX_RESULTS) {
                String docId = c.getString(idCol);
                if (docId == null) continue;
                String name = safe(c.getString(nameCol), "Sin nombre");
                String mime = safe(c.getString(mimeCol), "application/octet-stream");
                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                if (isDir) {
                    if (depth < MAX_DEPTH) dirs.add(new String[]{docId, path + "/" + name});
                    continue;
                }
                long bytes = c.isNull(sizeCol) ? -1 : c.getLong(sizeCol);
                long modified = c.isNull(modCol) ? 0 : c.getLong(modCol);
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
                if (!add(new FoundItem(uri, name, mime, "Carpeta autorizada", path,
                        bytes, modified, false, name.startsWith(".")))) break;
            }
            // Close this cursor before opening recursive provider queries.
            c.close(); c = null;
            for (String[] dir : dirs) {
                if (found.size() >= MAX_RESULTS) break;
                walkTree(tree, dir[0], dir[1], depth + 1, visited);
            }
        } catch (Exception ignored) {
            // Some document providers reject individual children; continue with accessible content.
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean matchingType(FoundItem item, int filter) {
        if (filter == 0) return true;
        if (filter == 6) return item.hidden;
        String mime = item.mime.toLowerCase(Locale.ROOT);
        String name = item.name.toLowerCase(Locale.ROOT);
        if (filter == 1) return mime.startsWith("image/") || ends(name, ".jpg", ".jpeg", ".png", ".heic", ".webp", ".gif");
        if (filter == 2) return mime.startsWith("video/") || ends(name, ".mp4", ".mov", ".mkv", ".avi");
        if (filter == 3) return mime.startsWith("audio/") || ends(name, ".mp3", ".wav", ".m4a", ".ogg");
        if (filter == 4) return mime.startsWith("text/") || ends(name, ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".zip", ".csv");
        return !(matchingType(item, 1) || matchingType(item, 2) || matchingType(item, 3) || matchingType(item, 4));
    }

    private boolean ends(String value, String... suffixes) {
        for (String suffix : suffixes) if (value.endsWith(suffix)) return true;
        return false;
    }

    private void render() {
        if (resultList == null || resultCount == null || searchInput == null || typeFilter == null) return;
        resultList.removeAllViews();
        String query = searchInput.getText().toString().toLowerCase(Locale.ROOT).trim();
        int filter = typeFilter.getSelectedItemPosition();
        int shown = 0;
        for (FoundItem item : found) {
            if (!item.name.toLowerCase(Locale.ROOT).contains(query) || !matchingType(item, filter)) continue;
            LinearLayout card = vertical();
            card.setBackground(background(CARD, BORDER, 12));
            card.setPadding(dp(13), dp(12), dp(13), dp(12));
            String category = item.trashed ? "EN PAPELERA" : item.hidden ? "NOMBRE OCULTO" : "ARCHIVO EXISTENTE";
            TextView chip = text(category, 10, item.trashed ? ACCENT : MUTED, true);
            card.addView(chip);
            addSpace(card, 4);
            TextView title = text(item.name, 15, FG, true);
            title.setMaxLines(2);
            card.addView(title);
            addSpace(card, 5);
            card.addView(text(item.source + "  ·  " + sizeText(item.size), 12, MUTED, false));
            card.addView(text("Modificado: " + dateText(item.modified), 12, MUTED, false));
            card.setOnClickListener(v -> itemActions(item));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            resultList.addView(card, lp);
            shown++;
        }
        resultCount.setText("RESULTADOS · " + shown + " de " + found.size() + "   ·   antiguos primero");
        if (shown == 0) {
            TextView empty = text(found.isEmpty() ? "Todavía no hay resultados." : "Ningún archivo coincide con el filtro.", 13, MUTED, false);
            empty.setPadding(dp(3), dp(12), dp(3), dp(12));
            resultList.addView(empty);
        }
    }

    private void itemActions(FoundItem item) {
        String detail = "Origen: " + item.source
                + "\nUbicación: " + item.location
                + "\nTamaño: " + sizeText(item.size)
                + "\nModificación: " + dateText(item.modified)
                + "\n\nLa modificación no es la fecha del borrado.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(item.name).setMessage(detail).setNegativeButton("Cerrar", null);
        if (item.trashed && Build.VERSION.SDK_INT >= 30) {
            dialog.setNeutralButton("Guardar copia", (d, which) -> chooseCopyTarget(item));
            dialog.setPositiveButton("Restaurar", (d, which) -> restore(item));
        } else {
            dialog.setPositiveButton("Guardar copia", (d, which) -> chooseCopyTarget(item));
        }
        dialog.show();
    }

    private void chooseCopyTarget(FoundItem item) {
        pendingCopy = item;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(item.mime.contains("/") && !item.mime.equals(DocumentsContract.Document.MIME_TYPE_DIR)
                ? item.mime : "application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, item.name.replace("/", "_").replace("\\", "_") );
        try { startActivityForResult(i, REQ_SAVE); }
        catch (Exception e) { pendingCopy = null; message("No se pudo abrir el selector de destino."); }
    }

    private void copyTo(FoundItem item, Uri destination) {
        setBusy(true, "Copiando a la ubicación que elegiste…");
        new Thread(() -> {
            boolean success = false;
            String error = "Archivo no accesible para copiar. Prueba a restaurarlo primero si está en la papelera.";
            try (InputStream in = getContentResolver().openInputStream(item.uri);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (in == null || out == null) throw new IllegalStateException("El proveedor no permitió abrir el archivo.");
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.flush();
                success = true;
            } catch (Exception e) { if (e.getMessage() != null) error = e.getMessage(); }
            boolean done = success;
            String failure = error;
            mainHandler.post(() -> {
                setBusy(false, done ? "Copia guardada en el destino seleccionado." : "No se pudo copiar: " + failure);
                message(done ? "Copia guardada correctamente." : "No se pudo copiar: " + failure);
            });
        }, "weup-copy").start();
    }

    private void restore(FoundItem item) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            PendingIntent intent = MediaStore.createTrashRequest(getContentResolver(),
                    Collections.singletonList(item.uri), false);
            startIntentSenderForResult(intent.getIntentSender(), REQ_RESTORE, null, 0, 0, 0);
        } catch (Exception e) {
            message("Android no concedió acceso para restaurar este archivo: " + safe(e.getMessage(), "operación no disponible"));
        }
    }

    private String dateText(long date) {
        return date > 0 ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, new Locale("es", "PE")).format(new Date(date)) : "Desconocida";
    }

    private String sizeText(long bytes) {
        if (bytes < 0) return "Tamaño desconocido";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
    private void message(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }

    private void showGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Cómo aumentar las posibilidades")
                .setMessage("1. Evita instalar más apps o grabar archivos nuevos en el dispositivo de origen.\n\n"
                    + "2. Revisa la papelera de Google Fotos, Google Drive, Galería y los gestores de archivos por separado.\n\n"
                    + "3. Busca copias en tus backups, WhatsApp y cuentas de nube.\n\n"
                    + "4. Si el archivo estaba en una microSD extraíble, deja de usarla y analízala desde una computadora con un lector de tarjetas.\n\n"
                    + "5. En la memoria interna cifrada, los archivos eliminados definitivamente hace mucho tiempo pueden ser irrecuperables incluso con root.\n\n"
                    + "La aplicación no promete reconstruir información que ya no existe.")
                .setPositiveButton("Entendido", null)
                .show();
    }
}