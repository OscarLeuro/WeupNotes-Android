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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** Seamless encrypted notes fallback. No claim of remote sync until the server confirms it. Weupperu.com. */
public class OfflineActivity extends Activity {
    public static final String ACTION_NEW = "com.weupperu.weupnotes.NEW_DRAFT";
    public static final String ACTION_LIST = "com.weupperu.weupnotes.LIST_DRAFTS";
    public static final String ACTION_SEARCH = "com.weupperu.weupnotes.SEARCH_DRAFTS";
    public static final String EXTRA_ID = "draft_id";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout layout;
    private EditText titleField, bodyField;
    private TextView status, pendingBanner;
    private String currentId;
    private boolean editing, initializing, saveFailed;
    private int brand;
    private Runnable delayedSave;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        brand = getSharedPreferences("appearance",MODE_PRIVATE).getInt("accent",getColor(R.color.brand));
        String action = getIntent().getAction();
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id != null || ACTION_NEW.equals(action)) editor(id);
        else list(ACTION_SEARCH.equals(action));
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;
    }
    private Button button(String label,Runnable action) {
        Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackground(rounded(brand));b.setOnClickListener(v->action.run());return b;
    }
    private void column() {
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackgroundColor(Color.WHITE);
        c.setPadding(dp(17),0,dp(17),dp(13));setContentView(c);layout=c;
        pendingBanner=text("",12,Color.WHITE,true);pendingBanner.setGravity(Gravity.CENTER);
        pendingBanner.setPadding(dp(4),dp(9),dp(4),dp(9));pendingBanner.setBackgroundColor(brand);
        pendingBanner.setOnClickListener(v->pendingClicked());layout.addView(pendingBanner);
        refreshBanner();
    }
    private boolean online() {
        ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return false;Network n=cm.getActiveNetwork();
        NetworkCapabilities cap=cm.getNetworkCapabilities(n);
        return cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    private void refreshBanner() {
        if(pendingBanner==null)return;
        try {
            int count=OfflineStore.all(this).size();
            pendingBanner.setText(count+(count==1?" cambio sin guardar":" cambios sin guardar")+(online()?" · Pendiente de sincronizar":" · Sin conexión"));
        } catch(Exception e) { pendingBanner.setText("No se pudieron leer los cambios pendientes"); }
    }
    private void pendingClicked() {
        flushSave();
        if(!online()) { Toast.makeText(this,"Los cambios quedan guardados en este dispositivo",Toast.LENGTH_LONG).show();return; }
        new AlertDialog.Builder(this).setTitle("Cambios pendientes")
            .setMessage("Tus notas están guardadas y cifradas en el dispositivo. Aún no se pueden subir automáticamente: el servidor debe implementar la API de sincronización con detección de conflictos. No se borrarán ni se marcarán como guardadas en la web sin confirmación.")
            .setPositiveButton("Entendido",null).show();
    }
    private void list(boolean focusSearch) {
        handler.removeCallbacksAndMessages(null);editing=false;currentId=null;titleField=null;bodyField=null;column();
        TextView header=text("Notas",23,getColor(R.color.ink),true);header.setPadding(0,dp(15),0,dp(4));layout.addView(header);
        TextView sub=text(online()?"Puedes seguir escribiendo; los cambios están pendientes de sincronizar.":"Puedes seguir escribiendo sin Internet. Se guarda automáticamente en este teléfono.",13,getColor(R.color.secondary),false);
        sub.setPadding(0,0,0,dp(9));layout.addView(sub);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setHint("Buscar notas");layout.addView(search);
        ScrollView scroll=new ScrollView(this);LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);scroll.addView(results);
        layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        layout.addView(button("+ Nueva nota",()->editor(null)));
        Button back=button(online()?"Volver a mis libretas":"Reconectar",this::finish);
        back.setTextColor(brand);back.setBackground(rounded(0xfff3f3f3));layout.addView(back);
        try {
            List<OfflineStore.Draft> drafts=OfflineStore.all(this);
            Runnable render=()->{
                results.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT).trim();int visible=0;
                for(OfflineStore.Draft draft:drafts) {
                    if(!draft.title.toLowerCase(Locale.ROOT).contains(q)&&!draft.body.toLowerCase(Locale.ROOT).contains(q))continue;
                    visible++;
                    LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(dp(12),dp(14),dp(12),dp(14));
                    item.addView(text(draft.title.isEmpty()?"Sin título":draft.title,17,getColor(R.color.ink),true));
                    TextView excerpt=text(draft.body.replace('\n',' ').trim(),13,getColor(R.color.secondary),false);excerpt.setMaxLines(2);item.addView(excerpt);
                    item.setOnClickListener(v->editor(draft.id));results.addView(item);
                    View line=new View(this);line.setBackgroundColor(0xffeeeeee);results.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
                }
                if(visible==0) results.addView(text(q.isEmpty()?"Aún no hay notas guardadas en este dispositivo.":"No se encontraron notas.",14,getColor(R.color.secondary),false));
            };
            search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(Editable e){} });
            render.run();
            if(focusSearch){search.requestFocus();search.postDelayed(()->{
                InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(imm!=null)imm.showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);
            },180);}
        } catch(Exception e) {
            results.addView(text("No se pudieron abrir las notas cifradas. No se ha eliminado ningún dato.",14,Color.RED,false));
        }
    }
    private void editor(String id) {
        handler.removeCallbacksAndMessages(null);editing=true;currentId=id;OfflineStore.Draft draft=null;
        try {if(id!=null)draft=OfflineStore.find(this,id);}catch(Exception e){fail("No se pudo abrir esta nota cifrada. Los datos se han conservado.");return;}
        if(id!=null&&draft==null){fail("No se encontró esta nota en el dispositivo.");return;}
        column();
        TextView header=text("‹   Nota",22,getColor(R.color.ink),true);header.setPadding(0,dp(15),0,dp(12));header.setOnClickListener(v->onBackPressed());layout.addView(header);
        titleField=new EditText(this);bodyField=new EditText(this);initializing=true;
        titleField.setText(draft==null?"":draft.title);titleField.setHint("Título");titleField.setSingleLine(true);titleField.setTextSize(21);layout.addView(titleField);
        bodyField.setText(draft==null?"":draft.body);bodyField.setHint("Empieza a escribir...");bodyField.setTextSize(16);bodyField.setGravity(Gravity.TOP);
        bodyField.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        layout.addView(bodyField,new LinearLayout.LayoutParams(-1,0,1));
        status=text("Guardado automáticamente en este dispositivo · Pendiente de sincronizar",12,getColor(R.color.secondary),false);
        status.setPadding(0,dp(6),0,dp(8));layout.addView(status);
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){queueSave();}
            public void afterTextChanged(Editable e){} };
        titleField.addTextChangedListener(watcher);bodyField.addTextChangedListener(watcher);initializing=false;
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button share=button("Compartir",()->{flushSave();Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,titleField.getText()+"\n\n"+bodyField.getText());startActivity(Intent.createChooser(send,"Compartir nota"));});
        actions.addView(share,new LinearLayout.LayoutParams(0,dp(45),1));
        Button copy=button("Copiar",()->{flushSave();ClipboardManager clip=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clip.setPrimaryClip(ClipData.newPlainText("WeupNotes",titleField.getText()+"\n\n"+bodyField.getText()));Toast.makeText(this,"Copiado",Toast.LENGTH_SHORT).show();});
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(45),1);cp.leftMargin=dp(7);actions.addView(copy,cp);layout.addView(actions);
        if(id!=null) {
            Button delete=button("Eliminar nota",()->new AlertDialog.Builder(this).setTitle("Eliminar nota de este dispositivo")
                .setMessage("Esto no elimina una nota del servidor.").setNegativeButton("Cancelar",null)
                .setPositiveButton("Eliminar",(d,w)->{handler.removeCallbacksAndMessages(null);editing=false;
                    try {OfflineStore.delete(this,currentId);list(false);}catch(Exception e){fail("No se pudo eliminar.");}}).show());
            delete.setTextColor(0xff922445);delete.setBackground(rounded(0xfffceaf2));layout.addView(delete);
        }
        titleField.requestFocus();
    }
    private void queueSave() {
        if(initializing||!editing)return;
        if(status!=null)status.setText("Guardando...");
        if(delayedSave!=null)handler.removeCallbacks(delayedSave);
        delayedSave=()->saveNote(false);handler.postDelayed(delayedSave,650);
    }
    private boolean saveNote(boolean quiet) {
        if(!editing||titleField==null||bodyField==null)return true;
        String title=titleField.getText().toString(),body=bodyField.getText().toString();
        if(currentId==null&&title.trim().isEmpty()&&body.trim().isEmpty())return true;
        try {
            currentId=OfflineStore.save(this,currentId,title,body).id;saveFailed=false;
            if(status!=null)status.setText("Guardado en este dispositivo · Pendiente de sincronizar");
            refreshBanner();return true;
        } catch(Exception e){saveFailed=true;if(status!=null)status.setText("No se pudo guardar: mantén esta pantalla abierta");
            if(!quiet)fail("No fue posible guardar. Conserva el editor abierto y vuelve a intentarlo.");return false;}
    }
    private boolean flushSave() {
        if(delayedSave!=null){handler.removeCallbacks(delayedSave);delayedSave=null;}
        return saveNote(false);
    }
    private void fail(String msg){new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Entendido",null).show();}
    @Override public void onBackPressed() {
        if(editing){if(!flushSave())return;list(false);}else finish();
    }
    @Override protected void onPause(){if(editing)flushSave();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
