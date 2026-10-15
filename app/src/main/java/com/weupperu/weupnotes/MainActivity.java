package com.weupperu.weupnotes;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.Gravity;
import android.view.MotionEvent;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import java.util.Locale;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * WeupNotes Android shell. Autor: Weupperu.com.
 * El contenido y las cuentas son los mismos del servidor web; NO se aloja una copia local.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://notas.weupdevelopers.com/";
    private static final String TRUSTED_HOST = "notas.weupdevelopers.com";
    private static final int FILE_PICKER_REQUEST = 1701;
    private static final int MEDIA_PERMISSION_REQUEST = 1702;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private ValueCallback<Uri[]> uploadCallback;
    private PermissionRequest mediaRequest;
    private boolean mainFrameError = false;
    private FrameLayout wheelHost;
    private View wheelShade;
    private Button wheelButton;
    private final Button[] wheelOptions = new Button[4];
    private final float[] wheelDx = new float[4];
    private final float[] wheelDy = new float[4];
    private boolean wheelOpen;
    private boolean startedWheelOpen;
    private int highlighted = -1;
    private float wheelDownX, wheelDownY;


    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        initWebView();
        initOverlay();
        initWheel();
        if (state != null) {
            webView.restoreState(state);
            if (webView.getUrl() == null) webView.loadUrl(HOME);
        } else {
            webView.loadUrl(HOME);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true); // necesario para adjuntos seleccionados por la persona
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(Color.WHITE);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return routeNavigation(request.getUrl());
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameError = false;
                errorPanel.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (mainFrameError) errorPanel.setVisibility(View.VISIBLE);
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse response) {
                if (req.isForMainFrame() && response.getStatusCode() >= 500) showError();
            }
            // Los errores SSL no se sobrescriben: Android bloquea certificados no válidos.
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                root.removeView(webView);
                webView.destroy();
                initWebView();
                root.removeView(progress);
                root.addView(progress);
                errorPanel.bringToFront();
                wheelHost.bringToFront();
                showError();
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                if (value >= 100) progress.setVisibility(View.GONE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                       FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try {
                    Intent chooser = params.createIntent();
                    chooser.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(chooser, FILE_PICKER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException | SecurityException ex) {
                    uploadCallback = null;
                    callback.onReceiveValue(null);
                    toast(R.string.file_picker_failed);
                    return true;
                }
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> askMediaPermission(request));
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (mediaRequest == request) mediaRequest = null;
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog,
                                                     boolean isUserGesture, Message resultMsg) {
                if (!isUserGesture) return false;
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        if (req.isForMainFrame()) {
                            Uri link = req.getUrl();
                            if (isTrusted(link)) webView.loadUrl(link.toString());
                            else routeNavigation(link);
                        }
                        v.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        webView.setDownloadListener((url, userAgent, disposition, mimeType, length) ->
            download(url, userAgent, disposition, mimeType));
        root.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void initOverlay() {
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.brand)));
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        root.addView(progress, bar);

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setBackgroundColor(Color.WHITE);
        errorPanel.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView icon = new TextView(this);
        icon.setText("✎");
        icon.setTextColor(getColor(R.color.brand));
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(45);
        errorPanel.addView(icon);
        TextView title = new TextView(this);
        title.setText(R.string.offline_title);
        title.setTextColor(getColor(R.color.ink));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(15);
        errorPanel.addView(title, titleParams);
        TextView description = new TextView(this);
        description.setText(R.string.offline_message);
        description.setTextColor(getColor(R.color.secondary));
        description.setTextSize(15);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = dp(11);
        errorPanel.addView(description, descParams);
        Button retry = new Button(this);
        retry.setAllCaps(false);
        retry.setText(R.string.try_again);
        retry.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.brand));
        bg.setCornerRadius(dp(12));
        retry.setBackground(bg);
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(dp(180), dp(48));
        retryParams.topMargin = dp(24);
        errorPanel.addView(retry, retryParams);
        retry.setOnClickListener(v -> {
            if (!hasNetwork()) {
                toast(R.string.offline_message);
                return;
            }
            mainFrameError = false;
            errorPanel.setVisibility(View.GONE);
            webView.loadUrl(HOME);
        });
        errorPanel.addView(buttonForOffline());
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
    }

    private Button buttonForOffline() {
        Button local = new Button(this);
        local.setText("Mis notas locales"); local.setAllCaps(false);
        local.setTextColor(getColor(R.color.brand));
        local.setOnClickListener(v -> openLocal(OfflineActivity.ACTION_LIST));
        return local;
    }

    private android.graphics.drawable.GradientDrawable circle(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(80));
        return shape;
    }

    /** A native pie-style quick action menu, supporting hold-drag-release and regular taps. */
    private void initWheel() {
        wheelHost = new FrameLayout(this);
        root.addView(wheelHost, new FrameLayout.LayoutParams(-1, -1));
        wheelShade = new View(this); wheelShade.setBackgroundColor(0x44000000);
        wheelShade.setOnClickListener(v -> closeWheel());
        wheelHost.addView(wheelShade, new FrameLayout.LayoutParams(-1, -1));
        wheelShade.setVisibility(View.GONE);
        Button locals = new Button(this);
        locals.setText("▤ Locales"); locals.setAllCaps(false); locals.setTextSize(13);
        locals.setTextColor(getColor(R.color.brand));
        locals.setBackground(circle(getColor(R.color.light_brand)));
        locals.setElevation(dp(5)); locals.setContentDescription("Ver notas locales sin conexión");
        FrameLayout.LayoutParams localPosition = new FrameLayout.LayoutParams(dp(102),dp(43),Gravity.START|Gravity.BOTTOM);
        localPosition.leftMargin=dp(16); localPosition.bottomMargin=dp(26);
        wheelHost.addView(locals,localPosition);
        locals.setOnClickListener(v -> { closeWheel(); openLocal(OfflineActivity.ACTION_LIST); });
        String[] names = {"✎\nNota", "▣\nEspacio", "▤\nLibreta", "⌕\nBuscar"};
        String[] descriptions = {"Agregar una nota", "Agregar un nuevo espacio",
            "Agregar una nueva libreta", "Buscar"};
        for (int i=0; i<4; i++) {
            final int which = i;
            double angle = Math.toRadians(180 + 30 * i);
            wheelDx[i] = (float)(Math.cos(angle)*dp(143));
            wheelDy[i] = (float)(Math.sin(angle)*dp(143));
            Button option = new Button(this);
            option.setText(names[i]); option.setTextSize(12); option.setAllCaps(false);
            option.setTextColor(Color.WHITE); option.setBackground(circle(getColor(R.color.brand)));
            option.setElevation(dp(6)); option.setContentDescription(descriptions[i]);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(74),dp(74),Gravity.END|Gravity.BOTTOM);
            lp.rightMargin = Math.round(dp(10) - wheelDx[i]);
            lp.bottomMargin = Math.round(dp(12) - wheelDy[i]);
            wheelHost.addView(option,lp);
            option.setOnClickListener(v -> { closeWheel(); activate(which); });
            option.setVisibility(View.GONE); wheelOptions[i] = option;
        }
        wheelButton = new Button(this);
        wheelButton.setText("+"); wheelButton.setTextSize(29); wheelButton.setTextColor(Color.WHITE);
        wheelButton.setAllCaps(false); wheelButton.setBackground(circle(getColor(R.color.brand)));
        wheelButton.setElevation(dp(9)); wheelButton.setContentDescription("Acciones rápidas");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.END|Gravity.BOTTOM);
        lp.rightMargin=dp(18); lp.bottomMargin=dp(20);
        wheelHost.addView(wheelButton,lp);
        wheelButton.setOnClickListener(v -> { if (wheelOpen) closeWheel(); else openWheel(); });
        wheelButton.setOnTouchListener((v,event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startedWheelOpen = wheelOpen;
                    wheelDownX=event.getRawX(); wheelDownY=event.getRawY();
                    if (!wheelOpen) openWheel();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float offsetX=event.getRawX()-wheelDownX;
                    float offsetY=event.getRawY()-wheelDownY;
                    int best=-1; float distance=dp(47);
                    for (int i=0;i<4;i++) {
                        float dx=offsetX-wheelDx[i], dy=offsetY-wheelDy[i];
                        float d=(float)Math.sqrt(dx*dx+dy*dy);
                        if (d<distance) { distance=d; best=i; }
                    }
                    highlight(best);
                    return true;
                case MotionEvent.ACTION_UP:
                    int chosen=highlighted;
                    if (chosen>=0) { closeWheel(); v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); activate(chosen); }
                    else if (startedWheelOpen) closeWheel();
                    highlight(-1);
                    v.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    highlight(-1); if (!startedWheelOpen) closeWheel(); return true;
            }
            return false;
        });
    }
    private void openWheel() {
        wheelOpen=true;
        wheelShade.setVisibility(View.VISIBLE);
        for(Button b: wheelOptions) b.setVisibility(View.VISIBLE);
        wheelButton.setText("×"); wheelButton.setContentDescription("Cerrar acciones rápidas");
    }
    private void closeWheel() {
        wheelOpen=false;
        wheelShade.setVisibility(View.GONE);
        for(Button b: wheelOptions) b.setVisibility(View.GONE);
        wheelButton.setText("+"); wheelButton.setContentDescription("Acciones rápidas");
        highlight(-1);
    }
    private void highlight(int selected) {
        highlighted=selected;
        for (int i=0;i<wheelOptions.length;i++) {
            Button b=wheelOptions[i]; boolean active=i==selected;
            b.setScaleX(active?1.17f:1f); b.setScaleY(active?1.17f:1f);
            b.setBackground(circle(active?getColor(R.color.ink):getColor(R.color.brand)));
        }
    }
    private void openLocal(String action) {
        Intent intent = new Intent(this, OfflineActivity.class);
        intent.setAction(action); startActivity(intent);
    }
    private boolean webReady() {
        String url=webView.getUrl();
        return hasNetwork() && !mainFrameError && url!=null && isTrusted(Uri.parse(url));
    }
    private void activate(int action) {
        if (action==0) {
            webAction("note", ()->openLocal(OfflineActivity.ACTION_NEW));
        } else if(action==1) {
            webAction("space", ()->unavailable("La creación de espacios requiere abrir la web con conexión. Usa el botón Crear espacio de WeupNotes."));
        } else if(action==2) {
            webAction("notebook", ()->unavailable("La creación de libretas requiere abrir la web con conexión. Usa el botón Crear libreta de WeupNotes."));
        } else {
            webAction("search", ()->openLocal(OfflineActivity.ACTION_SEARCH));
        }
    }
    private void unavailable(String message) {
        new AlertDialog.Builder(this).setTitle("Acción en WeupNotes web")
            .setMessage(message).setPositiveButton("Entendido",null).show();
    }
    /** Try the actual visible controls, never invent undocumented POST routes. */
    private void webAction(String action, Runnable fallback) {
        if (!webReady()) { fallback.run(); return; }
        // Dispatching this event lets the server-side mobile shell provide a stable hook later.
        String js="(function(){"+
            "const a='"+action+"';document.dispatchEvent(new CustomEvent('weupnotes:native-action',{detail:{action:a}}));"+
            "const norm=s=>(s||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().trim();"+
            "if(a==='search'){let x=document.querySelector('input[type=search], input[aria-label*=Buscar], input[placeholder*=Buscar]');"+
            "if(x){x.focus();x.click();return true;}}"+
            "const rx={note:/((nueva?|crear|agregar|anadir)\\s+(una?\\s+)?nota)/,"+
            "space:/((nuevo?|crear|agregar|anadir)\\s+(un?\\s+)?espacio)/,"+
            "notebook:/((nueva?|crear|agregar|anadir)\\s+(una?\\s+)?libreta)/,"+
            "search:/buscar/}[a];"+
            "if(!rx)return false;const xs=Array.from(document.querySelectorAll('button,[role=button],a'));"+
            "const el=xs.find(e=>e.getClientRects().length&&rx.test(norm([e.getAttribute('aria-label'),e.getAttribute('title'),e.innerText].filter(Boolean).join(' '))));"+
            "if(el){el.click();return true;}return false;})()";
        webView.evaluateJavascript(js, result -> {
            if (!"true".equals(result)) fallback.run();
        });
    }

    private boolean isTrusted(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) &&
            TRUSTED_HOST.equalsIgnoreCase(uri.getHost());
    }

    private boolean routeNavigation(Uri uri) {
        if (isTrusted(uri)) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return true;
        try {
            Intent intent;
            if ("intent".equalsIgnoreCase(scheme)) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    String fallback = intent.getStringExtra("browser_fallback_url");
                    if (fallback != null && fallback.startsWith("https://"))
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
                }
                return true;
            }
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme) ||
                  "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme) ||
                  "sms".equalsIgnoreCase(scheme) || "geo".equalsIgnoreCase(scheme) ||
                  "whatsapp".equalsIgnoreCase(scheme))) return true;
            intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (Exception e) {
            toast(R.string.no_app);
        }
        return true;
    }

    private void askMediaPermission(PermissionRequest request) {
        if (!isTrusted(request.getOrigin())) {
            request.deny();
            return;
        }
        List<String> needed = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.RECORD_AUDIO);
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.CAMERA);
            } else {
                request.deny(); // No se conceden recursos WebView desconocidos.
                return;
            }
        }
        if (needed.isEmpty()) request.grant(request.getResources());
        else {
            if (mediaRequest != null) mediaRequest.deny();
            mediaRequest = request;
            requestPermissions(needed.toArray(new String[0]), MEDIA_PERMISSION_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code != MEDIA_PERMISSION_REQUEST || mediaRequest == null) return;
        boolean allowed = grants.length == permissions.length && grants.length > 0;
        for (int value : grants) allowed &= value == PackageManager.PERMISSION_GRANTED;
        if (allowed) mediaRequest.grant(mediaRequest.getResources());
        else mediaRequest.deny();
        mediaRequest = null;
    }

    private void download(String url, String userAgent, String disposition, String mimeType) {
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            toast(R.string.download_failed); // DownloadManager no admite URLs blob:.
            return;
        }
        try {
            String name = URLUtil.guessFileName(url, disposition, mimeType);
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setMimeType(mimeType);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) == null
                ? "" : CookieManager.getInstance().getCookie(url));
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle(name);
            if (Build.VERSION.SDK_INT >= 29)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            else
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            toast(R.string.download_started);
        } catch (Exception ex) {
            toast(R.string.download_failed);
        }
    }

    private void showError() {
        mainFrameError = true;
        errorPanel.setVisibility(View.VISIBLE);
        errorPanel.bringToFront();
        progress.setVisibility(View.GONE);
    }

    private boolean hasNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities cap = cm.getNetworkCapabilities(n);
        return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density + 0.5f);
    }
    private void toast(int resource) {
        Toast.makeText(this, resource, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == FILE_PICKER_REQUEST && uploadCallback != null) {
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            uploadCallback = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }
    @Override public void onBackPressed() {
        if (wheelOpen) { closeWheel(); return; }
        if (errorPanel.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    @Override protected void onDestroy() {
        if (uploadCallback != null) {
            uploadCallback.onReceiveValue(null);
            uploadCallback = null;
        }
        if (mediaRequest != null) {
            mediaRequest.deny();
            mediaRequest = null;
        }
        root.removeView(webView);
        webView.destroy();
        CookieManager.getInstance().flush();
        super.onDestroy();
    }
}
