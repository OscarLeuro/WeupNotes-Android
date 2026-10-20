package com.weupperu.rescate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only inventory of accessible shared internal storage. DOES NOT access deleted blocks.
 * Only reports still-existing residual COPIES in recognizable trace/cache/backup locations; never ordinary gallery images.
 * Author: Weupperu.com.
 */
public final class InternalTraceScanner {
    public interface Listener {
        void onFound(File file, String mime, boolean residual, String reason);
        void onProgress(int inspected, int matches);
    }
    public static final class Report {
        public final int inspected, matches, restricted;
        public final boolean stopped, capReached;
        Report(int inspected, int matches, int restricted, boolean stopped, boolean capReached) {
            this.inspected=inspected; this.matches=matches; this.restricted=restricted;
            this.stopped=stopped; this.capReached=capReached;
        }
    }
    private static final int MAX_FILES_INSPECTED = 120_000;
    private static final int MAX_DEPTH = 25;
    private static final long MAX_SNIFF = 2L*1024*1024*1024;
    private static final class Node {
        final File f; final int depth;
        Node(File f, int depth) { this.f=f; this.depth=depth; }
    }
    public static Report scan(File root, AtomicBoolean stop, int maxMatches, Listener listener) throws IOException {
        if (!root.isDirectory() || !root.canRead()) throw new IOException("No se puede leer el almacenamiento compartido.");
        String rootPath = root.getCanonicalPath();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new Node(root,0));
        int inspected=0, matches=0, restricted=0;
        while (!queue.isEmpty() && !stop.get() && inspected < MAX_FILES_INSPECTED && matches < maxMatches) {
            Node node = queue.removeFirst();
            File file = node.f;
            // Never follow symbolic links outside the selected root (or at all).
            String canonical;
            try { canonical=file.getCanonicalPath(); }
            catch (IOException e) { restricted++; continue; }
            if (!canonical.equals(file.getAbsolutePath()) || !(canonical.equals(rootPath) || canonical.startsWith(rootPath + File.separator))) continue;
            if (file.isDirectory()) {
                if (node.depth >= MAX_DEPTH || !visited.add(canonical)) continue;
                String path = canonical.replace('\\','/').toLowerCase(Locale.ROOT);
                if (path.endsWith("/android/data") || path.endsWith("/android/obb")) { restricted++; continue; }
                // Do not walk this app's own temporary recovered results.
                if (path.contains("/android/data/com.weupperu.rescate")) continue;
                File[] children=file.listFiles();
                if (children == null) { restricted++; continue; }
                for (File child: children) queue.addLast(new Node(child,node.depth+1));
                continue;
            }
            if (!file.isFile() || !file.canRead()) { restricted++; continue; }
            inspected++;
            String path = file.getAbsolutePath().replace('\\','/').toLowerCase(Locale.ROOT);
            String name = file.getName().toLowerCase(Locale.ROOT);
            // IMPORTANT: do not populate a supposed forensic result list with ordinary
            // gallery entries. A trace is a STILL-EXISTING copy, never proof of deletion.
            String reason = residualReason(canonical.substring(rootPath.length()), name);
            if (reason == null) continue;
            long length=file.length();
            if (length < 96 || length > MAX_SNIFF) continue;
            String mime = sniff(file);
            if (mime == null) continue;
            listener.onFound(file,mime,true,reason);
            matches++;
            if (inspected % 250 == 0 || matches % 15 == 0) listener.onProgress(inspected,matches);
        }
        listener.onProgress(inspected,matches);
        return new Report(inspected,matches,restricted,stop.get(),inspected>=MAX_FILES_INSPECTED || matches>=maxMatches);
    }
    /** Only explicitly residual / hidden / backup locations; not ordinary DCIM/Pictures/Movies. */
    public static String residualReason(String path, String name) {
        String p = path.replace('\\','/').toLowerCase(Locale.ROOT);
        String n = name.toLowerCase(Locale.ROOT);
        // Ignore normal items even if their names contain the word cache/thumb/backup.
        if (n.startsWith(".trashed-") || n.startsWith(".trashed_") || n.startsWith(".trash-"))
            return "Archivo físico con nombre de descarte (borrado no verificable)";
        String[] parts=p.split("/");
        for (int i=0; i<parts.length-1; i++) {
            String segment=parts[i];
            if (segment.equals(".thumbnails") || segment.equals("thumbnails") || segment.equals(".thumb") || segment.equals("thumbs"))
                return "Miniatura/copia física residual (borrado no verificable)";
            if (segment.equals(".cache") || segment.equals("cache") || segment.equals("caches") || segment.equals("cached"))
                return "Copia de caché accesible (borrado no verificable)";
            if (segment.equals(".trash") || segment.equals(".trashed") || segment.equals("trash") || segment.equals("recycle") || segment.equals("recycler"))
                return "Copia física en carpeta de descarte (no es espacio libre)";
            if (segment.equals("lost.dir") || segment.equals("lost+found"))
                return "Fragmento todavía existente en directorio de recuperación";
            if (segment.equals("backup") || segment.equals("backups") || segment.equals(".backup"))
                return "Copia de respaldo todavía existente";
            if (segment.equals("temp") || segment.equals("tmp") || segment.equals(".temp"))
                return "Copia temporal todavía existente";
            if (segment.startsWith(".") && !segment.equals(".") && !segment.equals(".."))
                return "Archivo dentro de carpeta oculta todavía accesible";
        }
        if (n.startsWith(".") && !n.equals(".nomedia")) return "Archivo oculto todavía accesible";
        return null;
    }
    /** Signature inspection, not an assertion of complete file validity. */
    public static String sniff(File file) {
        byte[] h=new byte[32];
        int n;
        try (FileInputStream in = new FileInputStream(file)) { n=in.read(h); }
        catch (IOException e) { return null; }
        if (n < 12) return null;
        if ((h[0]&255)==255 && (h[1]&255)==216 && (h[2]&255)==255) return "image/jpeg";
        if ((h[0]&255)==137 && h[1]==80 && h[2]==78 && h[3]==71 && h[4]==13 && h[5]==10 && h[6]==26 && h[7]==10) return "image/png";
        if (h[0]=='G' && h[1]=='I' && h[2]=='F' && h[3]=='8' && (h[4]=='7'||h[4]=='9') && h[5]=='a') return "image/gif";
        if (h[0]=='R' && h[1]=='I' && h[2]=='F' && h[3]=='F' && h[8]=='W' && h[9]=='E' && h[10]=='B' && h[11]=='P') return "image/webp";
        if (h[4]=='f' && h[5]=='t' && h[6]=='y' && h[7]=='p') {
            String brand=new String(h,8,4,java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.startsWith("he") || brand.equals("mif1") || brand.equals("msf1")) return "image/heic";
            if (brand.equals("qt  ")) return "video/quicktime";
            if (brand.equals("isom") || brand.equals("iso2") || brand.equals("mp41") ||
                    brand.equals("mp42") || brand.equals("avc1") || brand.equals("M4V ") ||
                    brand.equals("3gp4") || brand.equals("3gp5") || brand.equals("3gp6") || brand.equals("dash")) return "video/mp4";
        }
        if ((h[0]&255)==0x1A && (h[1]&255)==0x45 && (h[2]&255)==0xDF && (h[3]&255)==0xA3)
            return "video/x-matroska"; // May also be WebM; validate via preview.
        return null;
    }
    private InternalTraceScanner() {}
}