package com.weupperu.weupnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Encrypted local drafts: deliberately not labeled synchronized until the server protocol exists. */
public class OfflineActivity extends Activity {
    public static final String ACTION_NEW = "com.weupperu.weupnotes.NEW_DRAFT";
    public static final String ACTION_LIST = "com.weupperu.weupnotes.LIST_DRAFTS";
    public static final String ACTION_SEARCH = "com.weupperu.weupnotes.SEARCH_DRAFTS";
    public static final String EXTRA_ID = "draft_id";
    private LinearLayout layout;
    private EditText titleField, bodyField;
    private String currentId;
    private String initialTitle = "", initialBody = "";
    private boolean editing;
    private int brand;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        brand = getColor(R.color.brand);
        String action = getIntent().getAction();
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id != null || ACTION_NEW.equals(action)) editor(id);
        else list(ACTION_SEARCH.equals(action));
    }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(13)); return d;
    }
    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(19), dp(20), dp(19), dp(20)); c.setBackgroundColor(Color.WHITE);
        setContentView(c); layout = c; return c;
    }
    private TextView text(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(content); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private Button button(String name, Runnable action) {
        Button b = new Button(this); b.setText(name); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackground(rounded(brand));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void list(boolean search) {
        editing = false; column();
        TextView header = text("Mis notas locales", 23, getColor(R.color.ink), true);
        layout.addView(header);
        TextView note = text("Borradores guardados y cifrados en este teléfono. Todavía no están sincronizados con la web.", 13, getColor(R.color.secondary), false);
        note.setPadding(0, dp(5), 0, dp(12)); layout.addView(note);
        CheckBox preview = new CheckBox(this);
        preview.setText("Mostrar título de última nota en widget"); preview.setTextSize(13);
        preview.setChecked(getSharedPreferences("widget",MODE_PRIVATE).getBoolean("show_title",false));
        preview.setOnCheckedChangeListener((v,on) -> {
            getSharedPreferences("widget",MODE_PRIVATE).edit().putBoolean("show_title",on).apply();
            NotesWidget.updateAll(this);
        }); layout.addView(preview);
        EditText query = new EditText(this); query.setSingleLine(true); query.setTextSize(16);
        query.setHint("Buscar en mis notas locales"); layout.addView(query);
        ScrollView scroll = new ScrollView(this); LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL); scroll.addView(results);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button add = button("+ Nueva nota local", () -> editor(null));
        layout.addView(add);
        Button web = button("Volver a WeupNotes web", this::finish);
        LinearLayout.LayoutParams webParam = new LinearLayout.LayoutParams(-1, dp(50));
        webParam.topMargin = dp(7); layout.addView(web, webParam);
        try {
            List<OfflineStore.Draft> drafts = OfflineStore.all(this);
            Runnable render = () -> {
                results.removeAllViews();
                String needle = query.getText().toString().toLowerCase(Locale.ROOT).trim();
                int shown = 0;
                for (OfflineStore.Draft d : drafts) {
                    if (!d.title.toLowerCase(Locale.ROOT).contains(needle) && !d.body.toLowerCase(Locale.ROOT).contains(needle)) continue;
                    shown++;
                    LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL);
                    item.setPadding(dp(12), dp(15), dp(12), dp(15));
                    TextView name = text(d.title.isEmpty() ? "Sin título" : d.title, 17, getColor(R.color.ink), true);
                    item.addView(name);
                    TextView snippet = text(d.body.replace('\n',' ').trim(), 13, getColor(R.color.secondary), false);
                    snippet.setMaxLines(2); item.addView(snippet);
                    item.setOnClickListener(v -> editor(d.id));
                    results.addView(item);
                    View sep = new View(this); sep.setBackgroundColor(0xffeeeeee);
                    results.addView(sep, new LinearLayout.LayoutParams(-1, dp(1)));
                }
                if (shown == 0) results.addView(text("No hay notas locales con ese texto.", 14, getColor(R.color.secondary), false));
            };
            query.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}
                public void afterTextChanged(Editable e){}
            });
            render.run();
            if (search) { query.requestFocus(); query.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
            }, 200); }
        } catch (Exception e) { results.addView(text("No fue posible descifrar las notas locales. No se ha borrado ningún dato.", 14, Color.RED, false)); }
    }

    private void editor(String id) {
        editing = true; currentId = id;
        OfflineStore.Draft draft = null;
        try { if (id != null) draft = OfflineStore.find(this,id); }
        catch (Exception e) { fail("No es posible abrir esta nota cifrada. No se han borrado datos."); return; }
        if (id != null && draft == null) { fail("Esta nota ya no está en el dispositivo."); return; }
        initialTitle = draft == null ? "" : draft.title;
        initialBody = draft == null ? "" : draft.body;
        column();
        TextView header = text("Editor sin conexión", 22, getColor(R.color.ink), true);
        layout.addView(header);
        TextView status = text("Se guarda únicamente en este teléfono. Sin sincronización aún.", 13, getColor(R.color.secondary), false);
        status.setPadding(0, dp(5), 0, dp(12)); layout.addView(status);
        titleField = new EditText(this); titleField.setText(initialTitle); titleField.setHint("Título de la nota");
        titleField.setSingleLine(true); titleField.setTextSize(20); layout.addView(titleField);
        bodyField = new EditText(this); bodyField.setText(initialBody); bodyField.setHint("Escribe aquí. Disponible sin Internet…");
        bodyField.setTextSize(16); bodyField.setGravity(Gravity.TOP);
        bodyField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        layout.addView(bodyField, new LinearLayout.LayoutParams(-1, 0, 1));
        layout.addView(button("Guardar nota local", this::saveDraft));
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = button("Copiar", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("WeupNotes", titleField.getText() + "\n\n" + bodyField.getText()));
            Toast.makeText(this, "Copiado al portapapeles", Toast.LENGTH_SHORT).show();
        });
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button share = button("Compartir", () -> {
            Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, titleField.getText() + "\n\n" + bodyField.getText());
            startActivity(Intent.createChooser(send, "Compartir copia de la nota"));
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(50), 1); rp.leftMargin=dp(7);
        row.addView(share, rp); layout.addView(row);
        if (id != null) {
            Button delete = button("Eliminar borrador local", () -> new AlertDialog.Builder(this)
                .setTitle("¿Eliminar esta nota local?").setMessage("Esta acción no afecta a notas del servidor.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Eliminar", (d,w) -> {
                    try { OfflineStore.delete(this,currentId); finish(); }
                    catch (Exception e) { fail("No se pudo eliminar el borrador."); }
                }).show());
            delete.setTextColor(0xff9c1740); delete.setBackground(rounded(0xfffceaf2)); layout.addView(delete);
        }
    }
    private boolean saveDraft() {
        String title = titleField.getText().toString(), body = bodyField.getText().toString();
        if (title.trim().isEmpty() && body.trim().isEmpty()) { Toast.makeText(this,"Escribe un título o contenido",Toast.LENGTH_SHORT).show(); return false; }
        try {
            OfflineStore.Draft d = OfflineStore.save(this,currentId,title,body);
            currentId = d.id; initialTitle = title; initialBody = body;
            Toast.makeText(this,"Guardado en este teléfono",Toast.LENGTH_SHORT).show();
            list(false);
            return true;
        } catch (Exception e) { fail("No se pudo guardar. Conserva el editor abierto e inténtalo nuevamente."); return false; }
    }
    private void fail(String text) { new AlertDialog.Builder(this).setMessage(text).setPositiveButton("Entendido",null).show(); }
    @Override public void onBackPressed() {
        if (editing && titleField != null && (!initialTitle.equals(titleField.getText().toString()) || !initialBody.equals(bodyField.getText().toString()))) {
            new AlertDialog.Builder(this).setTitle("Cambios sin guardar").setMessage("¿Guardar el borrador antes de salir?")
                .setPositiveButton("Guardar", (d,w)->{ if(saveDraft()) finish(); })
                .setNeutralButton("Continuar editando",null)
                .setNegativeButton("Descartar cambios",(d,w)->finish()).show();
        } else finish();
    }
}
