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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.Settings;
import android.util.LruCache;
import android.widget.CheckBox;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.MessageDigest;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Weup Rescue v0.3.1 — Weupperu.com
 * Local, read-only retrieval from accessible storage and signature-carving of an explicitly
 * selected, unencrypted forensic SD-card image. Does not bypass Android filesystem encryption.
 */
public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS = 501;
    private static final int REQ_TREE = 502;
    private static final int REQ_SAVE = 503;
    private static final int REQ_RESTORE = 504;
    private static final int REQ_IMAGE = 505;
    private static final int REQ_ALL_FILES = 506;
    private static final int REQ_BULK = 507;
    private static final int REQ_REPORT = 508;
    private static final int MAX_RESULTS = 700;
    private static final int PAGE_SIZE = 45;
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
    private Button trashButton, mediaButton, folderButton, rawButton, stopButton, internalButton, bulkButton;
    private final Set<String> selected = new HashSet<>();
    // All UI collections below are accessed only on Android's main thread.
    private final Map<String,List<WeakReference<ImageView>>> thumbnailWaiters = new HashMap<>();
    private int scanEpoch = 0;
    private int visibleResults = PAGE_SIZE;
    private boolean renderPending = false;
    private final ExecutorService previews = Executors.newFixedThreadPool(2);
    private final LruCache<String,Bitmap> thumbnails = new LruCache<String,Bitmap>(12*1024*1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private boolean busy = false;
    private boolean permissionPromptInProgress = false;
    private boolean permissionRequestedOnce = false;
    private ScanMode permissionMode = ScanMode.CURRENT;
    private FoundItem pendingCopy;
    private ScanMode lastMode = ScanMode.CURRENT;
    private final AtomicBoolean cancelRaw = new AtomicBoolean(false);
    private boolean rawRunning = false;
    private final AtomicBoolean cancelInternal = new AtomicBoolean(false);
    private boolean internalRunning = false;
    private volatile int inspectedInternal = 0;

    private enum ScanMode { TRASH, CURRENT, FOLDER, RAW, INTERNAL }

    private static class FoundItem {
        Uri uri;
        String name, mime, source, location;
        long size, modified;
        boolean trashed, hidden;
        File carvedFile;
        long offset = -1;
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
        TextView title = text("Escaneo forense\nde fotografías.", 29, FG, true);
        title.setLineSpacing(dp(1), 1.03f);
        root.addView(title);
        addSpace(root, 10);
        root.addView(text("Exploración forense local: rastros, miniaturas y copias en el almacenamiento interno accesible; análisis por firmas de una imagen binaria sin cifrar.", 14, MUTED, false));
        addSpace(root, 18);

        LinearLayout warning = vertical();
        warning.setBackground(background(CARD, BORDER, 13));
        warning.setPadding(dp(14), dp(13), dp(14), dp(13));
        warning.addView(text("IMPORTANTE", 11, ACCENT, true));
        addSpace(warning, 5);
        warning.addView(text("El Redmi cifra su memoria interna. Ni el permiso Todos los archivos ni el escaneo por firmas leen bloques ya borrados: se buscan rastros todavía accesibles. No desbloquees el bootloader para intentar recuperarlos; puede borrar los datos.", 13, FG, false));
        root.addView(warning);
        addSpace(root, 18);

        internalButton = button("⌕  ESCANEO FORENSE INTERNO", ACCENT);
        internalButton.setOnClickListener(v -> beginInternalScan());
        root.addView(internalButton, new LinearLayout.LayoutParams(-1, dp(58)));
        addSpace(root, 9);
        trashButton = button("♻  Revisar papelera Android", CARD);
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
        rawButton = button("▧  Analizar imagen binaria sin cifrar", CARD);
        rawButton.setOnClickListener(v -> selectRawImage());
        root.addView(rawButton, new LinearLayout.LayoutParams(-1, dp(54)));
        addSpace(root, 8);
        stopButton = button("■  Detener búsqueda", CARD);
        stopButton.setVisibility(View.GONE);
        stopButton.setOnClickListener(v -> {
            cancelRaw.set(true); cancelInternal.set(true);
            status.setText("Deteniendo búsqueda…");
        });
        root.addView(stopButton, new LinearLayout.LayoutParams(-1, dp(50)));
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
        bulkButton = button("Guardar seleccionadas (0)", ACCENT);
        bulkButton.setEnabled(false);
        bulkButton.setOnClickListener(v -> chooseBulkTarget());
        root.addView(bulkButton, new LinearLayout.LayoutParams(-1, dp(48)));
        addSpace(root, 8);
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
        typeFilter.setSelection(1);
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
        if (requestCode == REQ_ALL_FILES) {
            try {
                if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) scanInternal();
                else message("No se habilitó Todos los archivos. El escaneo de rastros internos requiere ese permiso.");
            } catch (Exception e) { message("No se pudo verificar el permiso: " + safe(e.getMessage(), "acceso restringido")); }
        } else if (requestCode == REQ_BULK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            bulkCopy(data.getData());
        } else if (requestCode == REQ_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try { getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) { /* Some providers only give temporary access. */ }
            scanFolder(tree);
        } else if (requestCode == REQ_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            scanRaw(data.getData());
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
        internalButton.setEnabled(!value);
        if (bulkButton != null) bulkButton.setEnabled(!value && !selected.isEmpty());
        trashButton.setEnabled(!value);
        mediaButton.setEnabled(!value);
        folderButton.setEnabled(!value);
        rawButton.setEnabled(!value);
        stopButton.setVisibility(value && (rawRunning || internalRunning) ? View.VISIBLE : View.GONE);
        status.setText(label);
    }

    private void reset() {
        synchronized (this) { found.clear(); knownUris.clear(); }
        selected.clear(); thumbnails.evictAll(); thumbnailWaiters.clear();
        scanEpoch++; visibleResults = PAGE_SIZE; renderPending = false;
        if (bulkButton != null) { bulkButton.setText("Guardar seleccionadas (0)"); bulkButton.setEnabled(false); }
        render();
    }

    private synchronized boolean add(FoundItem item) {
        if (found.size() >= MAX_RESULTS) return false;
        String key = itemKey(item);
        if (!knownUris.add(key)) return true;
        found.add(item);
        return found.size() < MAX_RESULTS;
    }

    private String itemKey(FoundItem item) {
        return item.carvedFile != null ? "carve:" + item.offset : (item.uri == null ? item.name : item.uri.toString());
    }

    /** On Android 11+, special all-files permission still excludes private /Android/data and deleted blocks. */
    private void beginInternalScan() {
        if (busy) return;
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                .setTitle("Permiso para escaneo interno")
                .setMessage("Esta función requiere permitir 'Administrar todos los archivos' para inspeccionar, en solo lectura, imágenes y miniaturas residuales en el almacenamiento COMPARTIDO del Redmi. No da acceso a bloques borrados ni a datos privados de otras aplicaciones. Sin internet, sin eliminación y sin root.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Abrir ajustes", (d,w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    try { startActivityForResult(intent, REQ_ALL_FILES); }
                    catch (Exception e) {
                        try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_ALL_FILES); }
                        catch (Exception failure) { message("No se pudo abrir el ajuste de almacenamiento."); }
                    }
                }).show();
            return;
        }
        scanInternal();
    }

    /** Only one delayed UI refresh is scheduled, regardless of scan speed. */
    private void scheduleInternalRender() {
        mainHandler.post(() -> {
            if (!internalRunning || renderPending) return;
            renderPending = true;
            mainHandler.postDelayed(() -> {
                renderPending = false;
                if (!internalRunning || isFinishing() || isDestroyed()) return;
                render();
                status.setText("Rastros accesibles: " + countFound() + " · Buscando más archivos…");
            }, 600);
        });
    }

    private synchronized int countFound() { return found.size(); }

    private void scanInternal() {
        if (busy || internalRunning) return;
        try {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                message("No se concedió el acceso al almacenamiento compartido. Habilita 'Administrar todos los archivos'.");
                return;
            }
            File root = Environment.getExternalStorageDirectory();
            if (root == null || !root.isDirectory() || !root.canRead()) {
                message("Android no permite leer el almacenamiento compartido desde esta app.");
                return;
            }
            lastMode = ScanMode.INTERNAL;
            cancelInternal.set(false);
            internalRunning = true;
            reset();
            setBusy(true, "Escaneando firmas JPG/PNG/GIF/WEBP/HEIC y rastros accesibles…");
            new Thread(() -> {
                InternalTraceScanner.Report report = null;
                String error = null;
                java.util.concurrent.atomic.AtomicInteger arrivals = new java.util.concurrent.atomic.AtomicInteger(0);
                try {
                    report = InternalTraceScanner.scan(root, cancelInternal, MAX_RESULTS,
                            new InternalTraceScanner.Listener() {
                        @Override public void onFound(File f, String mime, boolean residual, String reason) {
                            FoundItem item = new FoundItem(Uri.fromFile(f), f.getName(), mime, reason,
                                    f.getParent(), f.length(), f.lastModified(), false, f.getName().startsWith("."));
                            if (add(item)) {
                                int n = arrivals.incrementAndGet();
                                if (n == 1 || n % 12 == 0) scheduleInternalRender();
                            }
                        }
                        @Override public void onProgress(int inspected, int matches) {
                            inspectedInternal = inspected;
                            if (inspected > 0 && inspected % 2000 == 0) mainHandler.post(() -> {
                                if (internalRunning && !isFinishing()) status.setText(
                                        "Archivos revisados: " + inspected + " · imágenes accesibles: " + matches);
                            });
                        }
                    });
                } catch (Exception e) { error=safe(e.getMessage(), e.getClass().getSimpleName()); }
                InternalTraceScanner.Report result=report;
                String failure=error;
                mainHandler.post(() -> {
                    internalRunning=false;
                    renderPending=false;
                    if (isFinishing() || isDestroyed()) return;
                    render();
                    String detail = "Encontradas " + countFound() + " imágenes/copias todavía accesibles";
                    if (result != null) {
                        detail += " · " + result.inspected + " archivos revisados";
                        if (result.restricted > 0) detail += " · " + result.restricted + " ubicaciones restringidas";
                        if (result.capReached) detail += " · límite de seguridad alcanzado";
                        if (result.stopped) detail += " · detenido";
                    }
                    if (failure != null) detail += " · Error: " + failure;
                    detail += ". No es un análisis de sectores borrados; la antigüedad real del borrado no es verificable.";
                    setBusy(false,detail);
                });
            },"weup-internal-traces").start();
        } catch (Exception e) {
            internalRunning = false;
            setBusy(false, "No se pudo iniciar el escaneo: " + safe(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private void chooseBulkTarget() {
        if (selected.isEmpty() || busy) return;
        new AlertDialog.Builder(this).setTitle("Exportar " + selected.size() + " elementos")
                .setMessage("Elige una carpeta de destino, preferiblemente FUERA del medio que se analiza. Cada archivo se copiará sin modificar su origen.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Elegir carpeta", (d,w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    try { startActivityForResult(i,REQ_BULK); }
                    catch (Exception e) { message("No se pudo abrir el selector de destino."); }
                }).show();
    }

    private void bulkCopy(Uri tree) {
        final ArrayList<FoundItem> targets = new ArrayList<>();
        for (FoundItem f: found) if (selected.contains(itemKey(f))) targets.add(f);
        setBusy(true,"Exportando selección…");
        new Thread(() -> {
            int saved=0, failed=0;
            try {
                Uri directory=DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                for (int idx=0; idx<targets.size(); idx++) {
                    FoundItem item=targets.get(idx);
                    try {
                        String safeName = String.format(Locale.ROOT,"%03d_",idx+1) + item.name.replace('/', '_').replace('\\','_');
                        Uri dest = DocumentsContract.createDocument(getContentResolver(),directory,item.mime,safeName);
                        if (dest==null) throw new IOException("No se pudo crear destino");
                        try (InputStream in=openFound(item); OutputStream out=getContentResolver().openOutputStream(dest,"w")) {
                            if (out == null) throw new IOException("Destino no disponible");
                            byte[] data=new byte[65536]; int n;
                            while ((n=in.read(data))!=-1) out.write(data,0,n);
                        }
                        saved++;
                    } catch (Exception e) { failed++; }
                    int done = saved, bad=failed;
                    mainHandler.post(() -> status.setText("Copiados " + done + "/" + targets.size() + " · fallidos " + bad));
                }
            } catch (Exception e) { failed += targets.size() - saved; }
            int good=saved, bad=failed;
            mainHandler.post(() -> { setBusy(false,"Exportación: " + good + " archivos guardados, " + bad + " fallidos.");
                message("Exportados " + good + " archivos."); });
        },"weup-bulk-export").start();
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
        String query=searchInput.getText().toString().toLowerCase(Locale.ROOT).trim();
        int filter=typeFilter.getSelectedItemPosition();
        int shown=0;
        LinearLayout imageRow=null;
        int column=0;
        List<FoundItem> snapshot;
        synchronized (this) { snapshot = new ArrayList<>(found); }
        int eligible = 0;
        for (FoundItem item:snapshot) {
            if (!item.name.toLowerCase(Locale.ROOT).contains(query) || !matchingType(item,filter)) continue;
            eligible++;
            if (shown >= visibleResults) continue;
            if (item.mime.startsWith("image/")) {
                if (imageRow==null || column==3) {
                    imageRow=new LinearLayout(this);
                    imageRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);
                    rowParams.bottomMargin=dp(8);
                    resultList.addView(imageRow,rowParams);
                    column=0;
                }
                LinearLayout tile=vertical();
                tile.setPadding(dp(4),dp(5),dp(4),dp(7));
                tile.setBackground(background(CARD,BORDER,10));
                LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0,-2,1f);
                tileLp.setMargins(dp(2),0,dp(2),0);
                imageRow.addView(tile,tileLp);
                ImageView thumb=new ImageView(this);
                thumb.setBackground(background(BORDER,0,8));
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setImageResource(android.R.drawable.ic_menu_gallery);
                int thumbSize=(getResources().getDisplayMetrics().widthPixels - dp(65))/3;
                tile.addView(thumb,new LinearLayout.LayoutParams(-1,thumbSize));
                requestThumbnail(item,thumb);
                TextView name=text(item.name,11,FG,true);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(name);
                tile.addView(text(sizeText(item.size),10,MUTED,false));
                tile.addView(text(item.carvedFile!=null ? "POR FIRMA" : item.source.contains("Copia") ? "RASTRO ACCESIBLE" : "EXISTENTE",10,ACCENT,true));
                CheckBox check=new CheckBox(this);
                check.setText("Elegir"); check.setTextSize(11); check.setTextColor(FG);
                String key=itemKey(item);
                check.setChecked(selected.contains(key));
                check.setOnCheckedChangeListener((b,checked) -> {
                    if (checked) selected.add(key); else selected.remove(key);
                    bulkButton.setText("Guardar seleccionadas (" + selected.size() + ")");
                    bulkButton.setEnabled(!busy && !selected.isEmpty());
                });
                tile.addView(check);
                Button save=button("Guardar ↓",ACCENT);
                save.setTextSize(11);
                save.setPadding(0,dp(3),0,dp(3));
                save.setOnClickListener(v -> chooseCopyTarget(item));
                tile.addView(save,new LinearLayout.LayoutParams(-1,dp(39)));
                thumb.setOnClickListener(v -> showPreview(item));
                column++;
            } else {
                imageRow=null; column=0;
                LinearLayout card=vertical();
                card.setPadding(dp(13),dp(12),dp(13),dp(12));
                card.setBackground(background(CARD,BORDER,12));
                card.addView(text(item.carvedFile!=null? "RECONSTRUIDO POR FIRMA · VERIFICAR" : item.trashed ? "EN PAPELERA" : "ARCHIVO EXISTENTE",10,ACCENT,true));
                card.addView(text(item.name,15,FG,true));
                card.addView(text(item.source + " · " + sizeText(item.size),12,MUTED,false));
                LinearLayout actions = new LinearLayout(this);
                Button preview=button("Vista previa",CARD);
                preview.setOnClickListener(v -> showPreview(item));
                actions.addView(preview,new LinearLayout.LayoutParams(0,dp(43),1f));
                Button save=button("Guardar ↓",ACCENT);
                save.setOnClickListener(v -> chooseCopyTarget(item));
                actions.addView(save,new LinearLayout.LayoutParams(0,dp(43),1f));
                card.addView(actions);
                LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);
                cardLp.bottomMargin=dp(8);
                resultList.addView(card,cardLp);
            }
            shown++;
        }
        resultCount.setText("RESULTADOS · " + shown + " visibles · " + eligible + " coincidencias · " + snapshot.size() + " totales" +
                (lastMode==ScanMode.INTERNAL ? " · rastros internos" : lastMode==ScanMode.RAW ? " · firmas" : ""));
        if (eligible > shown) {
            Button more = button("Mostrar más fotografías (" + (eligible-shown) + " restantes)", CARD);
            more.setOnClickListener(v -> { visibleResults += PAGE_SIZE; render(); });
            resultList.addView(more,new LinearLayout.LayoutParams(-1,dp(50)));
        }
        if (shown==0) resultList.addView(text(snapshot.isEmpty() ? "Todavía no hay resultados." : "Ningún archivo coincide con el filtro.",13,MUTED,false));
    }

    /** One decoding task per image; all visible copies of a thumbnail share the result. */
    private void requestThumbnail(FoundItem item, ImageView view) {
        String key=itemKey(item);
        view.setTag(key);
        Bitmap existing=thumbnails.get(key);
        if (existing!=null && !existing.isRecycled()) { view.setImageBitmap(existing); return; }
        List<WeakReference<ImageView>> waiters = thumbnailWaiters.get(key);
        if (waiters != null) { waiters.add(new WeakReference<>(view)); return; }
        waiters=new ArrayList<>();
        waiters.add(new WeakReference<>(view));
        thumbnailWaiters.put(key,waiters);
        final int epoch=scanEpoch;
        previews.execute(() -> {
            Bitmap bmp=null;
            try {
                BitmapFactory.Options o=new BitmapFactory.Options();
                o.inJustDecodeBounds=true;
                try (InputStream in=openFound(item)) { BitmapFactory.decodeStream(in,null,o); }
                if (o.outWidth>0 && o.outHeight>0) {
                    BitmapFactory.Options small=new BitmapFactory.Options();
                    int max=Math.max(o.outWidth,o.outHeight);
                    int sample=1;
                    while (max/sample>280) sample*=2;
                    small.inSampleSize=sample;
                    small.inPreferredConfig=Bitmap.Config.RGB_565;
                    try (InputStream in=openFound(item)) { bmp=BitmapFactory.decodeStream(in,null,small); }
                }
            } catch (Exception ignored) { }
            Bitmap result=bmp;
            mainHandler.post(() -> {
                if (epoch != scanEpoch || isFinishing() || isDestroyed()) return;
                List<WeakReference<ImageView>> targets=thumbnailWaiters.remove(key);
                if (result!=null) thumbnails.put(key,result);
                if (targets==null) return;
                for (WeakReference<ImageView> weak : targets) {
                    ImageView target=weak.get();
                    if (target==null || !key.equals(target.getTag())) continue;
                    if (result!=null) target.setImageBitmap(result);
                    else target.setImageResource(android.R.drawable.ic_delete);
                }
            });
        });
    }

    private void itemActions(FoundItem item) {
        if (item.carvedFile != null) {
            showPreview(item);
            return;
        }
        String detail = "Origen: " + item.source
                + "\nUbicación: " + item.location
                + "\nTamaño: " + sizeText(item.size)
                + "\nModificación: " + dateText(item.modified)
                + "\n\nLa modificación no es la fecha del borrado.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(item.name).setMessage(detail).setNegativeButton("Vista previa", (d,which) -> showPreview(item));
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
            try (InputStream in = openFound(item);
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

    /** User chooses an existing forensic image, e.g. an image of a removed microSD created on a PC. */
    private void selectRawImage() {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle("Carving de una imagen forense")
                .setMessage("Selecciona una IMAGEN BINARIA sin cifrar (.img, .dd, .bin) de un medio previamente adquirido en modo forense. "
                        + "Se buscan firmas de JPG, PNG, PDF y ZIP/Office incluso si la tabla de archivos ya no los enumera. "
                        + "No se lee el espacio libre de la memoria interna de Android: elegir una carpeta no equivale a una imagen de disco. "
                        + "Los fragmentos sobrescritos o discontinuos no pueden reconstruirse con este método. "
                        + "Se utiliza hasta 512 MB de caché temporal interna, sin modificar el archivo de origen.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Seleccionar imagen", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivityForResult(i, REQ_IMAGE); }
                    catch (Exception e) { message("El selector de archivos no está disponible."); }
                }).show();
    }

    private File cacheDirectory() {
        return new File(getCacheDir(), "weup_carved");
    }

    private void clearCarveCache() {
        File dir = cacheDirectory();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) f.delete();
    }

    private void scanRaw(Uri imageUri) {
        if (busy) return;
        lastMode = ScanMode.RAW;
        cancelRaw.set(false);
        rawRunning = true;
        reset();
        clearCarveCache();
        setBusy(true, "Analizando imagen en solo lectura… No cierres la aplicación.");
        new Thread(() -> {
            SignatureCarver.Report report = null;
            String error = null;
            try (InputStream source = getContentResolver().openInputStream(imageUri)) {
                if (source == null) throw new java.io.IOException("No se pudo leer la imagen seleccionada");
                report = SignatureCarver.scan(source, cacheDirectory(), cancelRaw, new SignatureCarver.Listener() {
                    @Override public void onProgress(long bytes, int candidateCount) {
                        mainHandler.post(() -> {
                            if (rawRunning) status.setText("Analizados " + sizeText(bytes) + " · " + found.size()
                                    + " candidatos guardados · Pulsa Detener para finalizar.");
                        });
                    }
                    @Override public void onFound(SignatureCarver.Result result) {
                        // JPEG/PNG decoders reject false positives and severely corrupted candidates.
                        if (result.mime.startsWith("image/")) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(result.file.getAbsolutePath(), options);
                            if (options.outWidth < 1 || options.outHeight < 1) {
                                result.file.delete();
                                return;
                            }
                        }
                        String ext = result.extension;
                        String mime = result.mime;
                        if ("zip".equals(ext)) {
                            try (ZipFile archive = new ZipFile(result.file)) {
                                if (archive.getEntry("word/document.xml") != null) { ext="docx"; mime="application/vnd.openxmlformats-officedocument.wordprocessingml.document"; }
                                else if (archive.getEntry("xl/workbook.xml") != null) { ext="xlsx"; mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
                                else if (archive.getEntry("ppt/presentation.xml") != null) { ext="pptx"; mime="application/vnd.openxmlformats-officedocument.presentationml.presentation"; }
                            } catch (Exception ignored) { }
                        }
                        String label = String.format(Locale.ROOT, "recuperado_%012d.%s", result.offset, ext);
                        FoundItem item = new FoundItem(null, label, mime, "Análisis de imagen binaria",
                                "Desplazamiento byte " + result.offset, result.length, 0, false, false);
                        item.carvedFile = result.file;
                        item.offset = result.offset;
                        add(item);
                    }
                });
            } catch (Exception e) { error = safe(e.getMessage(), e.getClass().getSimpleName()); }
            SignatureCarver.Report done = report;
            String failed = error;
            mainHandler.post(() -> {
                rawRunning = false;
                render();
                String summary = found.size() + " candidatos reconstruidos por firmas (no se garantiza integridad). ";
                if (done != null) {
                    summary += "Leídos " + sizeText(done.scannedBytes) + ". ";
                    if (done.stopped) summary += "Exploración detenida por el usuario. ";
                    if (done.cacheLimit) summary += "Se alcanzó el límite de caché de 512 MB. ";
                }
                if (failed != null) summary += "No se completó el escaneo: " + failed + ". ";
                if (found.isEmpty()) summary += "No se hallaron archivos completos reconocibles. ";
                summary += "Usa Vista previa para revisar y Guardar archivo para exportar a Descargas, SD u otro destino.";
                setBusy(false, summary);
            });
        }, "weup-signature-carving").start();
    }

    private InputStream openFound(FoundItem item) throws java.io.IOException {
        if (item.carvedFile != null) return new FileInputStream(item.carvedFile);
        if (item.uri == null) throw new java.io.FileNotFoundException("El archivo no tiene origen disponible");
        if ("file".equals(item.uri.getScheme())) return new FileInputStream(new File(item.uri.getPath()));
        InputStream source = getContentResolver().openInputStream(item.uri);
        if (source == null) throw new java.io.FileNotFoundException("Android no permitió leer el archivo");
        return source;
    }

    private static final class Preview {
        Bitmap bitmap;
        String details;
    }

    private Bitmap decodeImage(FoundItem item) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = openFound(item)) { BitmapFactory.decodeStream(in, null, bounds); }
        if (bounds.outWidth < 1 || bounds.outHeight < 1) throw new java.io.IOException("No se pudo decodificar la imagen; podría estar corrupta.");
        BitmapFactory.Options opt = new BitmapFactory.Options();
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1400) sample *= 2;
        opt.inSampleSize = sample;
        try (InputStream in = openFound(item)) {
            Bitmap picture = BitmapFactory.decodeStream(in, null, opt);
            if (picture == null) throw new java.io.IOException("El contenido está incompleto o no se puede decodificar.");
            return picture;
        }
    }

    private Bitmap decodePdf(FoundItem item) throws Exception {
        ParcelFileDescriptor fd = null;
        File temporary = null;
        try {
            if (item.carvedFile != null) {
                fd = ParcelFileDescriptor.open(item.carvedFile, ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                // Some SAF providers return non-seekable descriptors. A local temporary file ensures PdfRenderer can seek.
                temporary = File.createTempFile("pdf_preview_", ".pdf", getCacheDir());
                long written = 0;
                try (InputStream in = openFound(item);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(temporary))) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) >= 0) {
                        written += n;
                        if (written > 128L*1024*1024) throw new java.io.IOException("PDF demasiado grande para vista previa (128 MB)");
                        out.write(buf, 0, n);
                    }
                }
                fd = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            try (PdfRenderer pdf = new PdfRenderer(fd)) {
                if (pdf.getPageCount() < 1) throw new java.io.IOException("El PDF no tiene páginas legibles");
                try (PdfRenderer.Page page = pdf.openPage(0)) {
                    float scale = 1000f / Math.max(page.getWidth(), 1);
                    int w = Math.max(1, Math.round(page.getWidth() * scale));
                    int h = Math.max(1, Math.round(page.getHeight() * scale));
                    h = Math.min(h, 2400);
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    b.eraseColor(Color.WHITE);
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return b;
                }
            }
        } finally {
            // PdfRenderer closes its descriptor; a second close is harmless.
            if (fd != null) try { fd.close(); } catch (Exception ignored) { }
            if (temporary != null) temporary.delete();
        }
    }

    private String previewArchive(FoundItem item) throws Exception {
        if (item.carvedFile == null) return "El archivo existe. Para revisar su contenido, guárdalo y ábrelo con un lector compatible.";
        StringBuilder sb = new StringBuilder("Contenido del contenedor ZIP/Office:\n\n");
        try (ZipFile zip = new ZipFile(item.carvedFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements() && count < 35) {
                ZipEntry entry = entries.nextElement();
                sb.append(entry.getName()).append("\n"); count++;
            }
            if (entries.hasMoreElements()) sb.append("… y más entradas");
        }
        return sb.toString();
    }

    private Preview makePreview(FoundItem item) throws Exception {
        Preview preview = new Preview();
        String mime = item.mime.toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/") || ends(item.name.toLowerCase(Locale.ROOT), ".jpg", ".jpeg", ".png")) {
            preview.bitmap = decodeImage(item);
        } else if (mime.equals("application/pdf") || item.name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            preview.bitmap = decodePdf(item);
            preview.details = "Primera página del PDF.";
        } else if (ends(item.name.toLowerCase(Locale.ROOT), ".zip", ".docx", ".xlsx", ".pptx")) {
            preview.details = previewArchive(item);
        } else if (mime.startsWith("text/") || ends(item.name.toLowerCase(Locale.ROOT), ".csv", ".json", ".xml")) {
            try (InputStream in = openFound(item); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[4096]; int n = in.read(b);
                if (n > 0) out.write(b, 0, n);
                preview.details = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } else preview.details = "Este formato no tiene previsualizador integrado. Puedes guardarlo para abrirlo en otra aplicación.";
        return preview;
    }

    private void showPreview(FoundItem item) {
        if (item.mime.startsWith("video/") || item.mime.startsWith("audio/")) {
            if (item.uri != null) {
                try {
                    Intent view = new Intent(Intent.ACTION_VIEW);
                    view.setDataAndType(item.uri, item.mime);
                    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(view);
                    return;
                } catch (Exception ignored) { /* Show explanatory preview below. */ }
            }
        }
        ScrollView scroller = new ScrollView(this);
        LinearLayout layout = vertical();
        layout.setPadding(dp(16),dp(12),dp(16),dp(12));
        scroller.addView(layout);
        TextView subtitle = text(item.source + " · " + sizeText(item.size)
                + (item.carvedFile != null ? "\nReconstrucción por firma: comprueba el archivo antes de utilizarlo." : ""), 12, MUTED, false);
        layout.addView(subtitle);
        addSpace(layout, 12);
        TextView loading = text("Preparando vista previa…", 14, FG, false);
        layout.addView(loading);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setView(scroller)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Guardar / descargar", (d,which) -> chooseCopyTarget(item))
                .create();
        dialog.show();
        new Thread(() -> {
            Preview result = null; String problem = null;
            try { result = makePreview(item); }
            catch (Exception e) { problem = safe(e.getMessage(), "Este archivo no se puede previsualizar."); }
            Preview data = result; String failure = problem;
            mainHandler.post(() -> {
                if (!dialog.isShowing()) {
                    if (data != null && data.bitmap != null) data.bitmap.recycle();
                    return;
                }
                layout.removeView(loading);
                if (failure != null) {
                    layout.addView(text("Vista previa no disponible: " + failure
                            + "\nSi es un archivo reconstruido, puede estar incompleto.", 14, FG, false));
                } else if (data != null) {
                    if (data.bitmap != null) {
                        ImageView display = new ImageView(this);
                        display.setImageBitmap(data.bitmap);
                        display.setAdjustViewBounds(true);
                        display.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        layout.addView(display, new LinearLayout.LayoutParams(-1, -2));
                    }
                    if (data.details != null) layout.addView(text(data.details, 13, FG, false));
                }
            });
        }, "weup-preview").start();
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
                .setMessage("1. La propia instalación de cualquier APK escribe en el almacenamiento: si los datos son críticos, evalúa antes una adquisición profesional.\n\n"
                    + "2. Revisa la papelera de Google Fotos, Google Drive, Galería y los gestores de archivos por separado.\n\n"
                    + "3. Busca copias en tus backups, WhatsApp y cuentas de nube.\n\n"
                    + "4. Si ya existe una imagen binaria sin cifrar adquirida por un profesional, usa el analizador de firmas. No desbloquees el bootloader del Redmi: puede borrar los datos.\n\n"
                    + "5. En la memoria interna cifrada, los archivos eliminados definitivamente hace mucho tiempo pueden ser irrecuperables incluso con root.\n\n"
                    + "El análisis por firmas sirve para archivos completos y contiguos; los fragmentados, cifrados o sobrescritos pueden no recuperarse. Guarda los resultados fuera del medio original.")
                .setPositiveButton("Entendido", null)
                .show();
    }
}
