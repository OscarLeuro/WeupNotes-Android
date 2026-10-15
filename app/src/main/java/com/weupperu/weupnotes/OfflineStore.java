package com.weupperu.weupnotes;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local, AES-GCM encrypted drafts. No optimistic claims of server synchronization. */
public final class OfflineStore {
    private static final String KEY_ALIAS = "com.weupperu.weupnotes.local.v1";
    private static final String FILE = "weupnotes-local-v1.enc";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Object LOCK = new Object();

    public static final class Draft {
        public String id;
        public String title;
        public String body;
        public long modified;
        public Draft(String id, String title, String body, long modified) {
            this.id = id; this.title = title; this.body = body; this.modified = modified;
        }
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("body", body); o.put("modified", modified);
            return o;
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE));
    }

    public static List<Draft> all(Context context) throws Exception {
        synchronized (LOCK) {
            AtomicFile f = file(context);
            if (!f.getBaseFile().exists()) return new ArrayList<>();
            byte[] bytes;
            try (InputStream in = f.openRead(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] part = new byte[8192]; int count;
                while ((count = in.read(part)) != -1) {
                    buffer.write(part, 0, count);
                    if (buffer.size() > MAX_BYTES + 64) throw new IllegalStateException("El archivo local excede el tamaño permitido");
                }
                bytes = buffer.toByteArray();
            }
            if (bytes.length < 29 || bytes[0] != 1) throw new IllegalStateException("Formato de notas locales desconocido");
            byte[] iv = new byte[12];
            System.arraycopy(bytes, 1, iv, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] raw = cipher.doFinal(bytes, 13, bytes.length - 13);
            JSONArray items = new JSONArray(new String(raw, StandardCharsets.UTF_8));
            List<Draft> drafts = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                drafts.add(new Draft(o.getString("id"), o.optString("title"), o.optString("body"), o.optLong("modified")));
            }
            Collections.sort(drafts, (a,b) -> Long.compare(b.modified, a.modified));
            return drafts;
        }
    }

    public static Draft find(Context context, String id) throws Exception {
        if (id == null) return null;
        for (Draft draft : all(context)) if (draft.id.equals(id)) return draft;
        return null;
    }

    private static void write(Context context, List<Draft> drafts) throws Exception {
        JSONArray payload = new JSONArray();
        for (Draft draft : drafts) payload.put(draft.toJson());
        byte[] raw = payload.toString().getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_BYTES) throw new IllegalStateException("Almacenamiento local lleno (4 MB)");
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(raw);
        AtomicFile f = file(context);
        FileOutputStream stream = null;
        try {
            stream = f.startWrite();
            stream.write(1); stream.write(iv); stream.write(encrypted);
            f.finishWrite(stream);
        } catch (Exception e) {
            if (stream != null) f.failWrite(stream);
            throw e;
        }
    }

    public static Draft save(Context context, String id, String title, String body) throws Exception {
        synchronized (LOCK) {
            List<Draft> drafts = all(context);
            if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
            Draft result = new Draft(id, title.trim(), body, System.currentTimeMillis());
            boolean replaced = false;
            for (int i = 0; i < drafts.size(); i++) if (drafts.get(i).id.equals(id)) {
                drafts.set(i, result); replaced = true; break;
            }
            if (!replaced) drafts.add(result);
            write(context, drafts);
            NotesWidget.updateAll(context);
            return result;
        }
    }

    public static void delete(Context context, String id) throws Exception {
        synchronized (LOCK) {
            List<Draft> drafts = all(context);
            if (drafts.removeIf(d -> d.id.equals(id))) {
                write(context, drafts);
                NotesWidget.updateAll(context);
            }
        }
    }

    private OfflineStore() {}
}
