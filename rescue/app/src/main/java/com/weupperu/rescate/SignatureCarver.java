package com.weupperu.rescate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * Read-only stream-based signature carving of an existing, unencrypted raw disk image.
 * Author: Weupperu.com. Only complete, contiguous files are candidates. No raw-device access.
 */
public final class SignatureCarver {
    public static final int MAX_FILES = 100;
    public static final long MAX_CACHE_BYTES = 512L * 1024 * 1024;
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10};
    private static final byte[] JPG = {(byte)255,(byte)216,(byte)255};
    private static final byte[] PDF = {37,80,68,70,45};
    private static final byte[] ZIP = {80,75,3,4};
    private static final byte[] PNG_END = {0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130};
    private static final byte[] PDF_END = {37,37,69,79,70};
    private static final byte[] ZIP_END = {80,75,5,6};

    public interface Listener {
        void onProgress(long scannedBytes, int foundCount);
        void onFound(Result result);
    }
    public static final class Result {
        public final File file;
        public final String extension;
        public final String mime;
        public final long offset;
        public final long length;
        Result(File file, String extension, String mime, long offset) {
            this.file = file; this.extension = extension; this.mime = mime;
            this.offset = offset; this.length = file.length();
        }
    }
    public static final class Report {
        public final long scannedBytes;
        public final int recovered;
        public final boolean stopped, cacheLimit;
        Report(long scannedBytes, int recovered, boolean stopped, boolean cacheLimit) {
            this.scannedBytes = scannedBytes; this.recovered = recovered;
            this.stopped = stopped; this.cacheLimit = cacheLimit;
        }
    }
    private static final class Counting {
        long bytes;
        final BufferedInputStream in;
        Counting(InputStream in) { this.in = new BufferedInputStream(in, 128 * 1024); }
        int read() throws IOException { int b = in.read(); if (b >= 0) bytes++; return b; }
    }
    private static final class Candidate {
        final String ext, mime;
        final byte[] header;
        final long limit;
        final byte[] end;
        Candidate(String ext, String mime, byte[] head, byte[] end, long limit) {
            this.ext=ext; this.mime=mime; this.header=head; this.end=end; this.limit=limit;
        }
    }
    private static final Candidate[] TYPES = {
        new Candidate("png", "image/png", PNG, PNG_END, 64L*1024*1024),
        new Candidate("jpg", "image/jpeg", JPG, new byte[]{(byte)255,(byte)217}, 64L*1024*1024),
        new Candidate("pdf", "application/pdf", PDF, PDF_END, 128L*1024*1024),
        new Candidate("zip", "application/zip", ZIP, ZIP_END, 128L*1024*1024)
    };
    private static boolean suffix(byte[] last, long available, byte[] pattern) {
        if (available < pattern.length) return false;
        for (int i=0; i<pattern.length; i++)
            if (last[(int)((available-pattern.length+i)%last.length)] != pattern[i]) return false;
        return true;
    }
    private static int matchEnd(byte[] ring, long count, byte[] end) {
        if (count < end.length) return 0;
        for (int i=0; i<end.length; i++)
            if (ring[(int)((count - end.length + i) % ring.length)] != end[i]) return 0;
        return 1;
    }
    private static boolean isValidZip(File file) {
        try (ZipFile zip = new ZipFile(file)) { return zip.size() > 0; }
        catch (Exception e) { return false; }
    }
    private static Result capture(Counting input, File directory, Candidate type, long offset,
                                  AtomicBoolean stop, long capacity) throws IOException {
        File target = File.createTempFile("recovered_", "."+type.ext, directory);
        long count = type.header.length;
        byte[] ring = new byte[Math.max(type.end.length, 24)];
        for (int i=0; i<type.header.length; i++) ring[i%ring.length] = type.header[i];
        boolean complete = false;
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target), 64 * 1024)) {
            out.write(type.header);
            while (!stop.get() && count < type.limit && count < capacity) {
                int b = input.read();
                if (b < 0) break;
                out.write(b);
                ring[(int)(count % ring.length)] = (byte)b;
                count++;
                // For the ZIP end-of-central-directory record, consume the 18 fixed bytes + comment.
                if (matchEnd(ring, count, type.end) == 1) {
                    if ("zip".equals(type.ext)) {
                        byte[] fixed = new byte[18];
                        boolean all = true;
                        for (int i=0; i<fixed.length; i++) {
                            int n = input.read(); if (n < 0) { all=false; break; }
                            fixed[i]=(byte)n; out.write(n); count++;
                        }
                        if (!all) break;
                        int comment = (fixed[16]&255) | ((fixed[17]&255)<<8);
                        for (int i=0; i<comment; i++) {
                            int n = input.read(); if (n < 0) { all=false; break; }
                            out.write(n); count++;
                        }
                        complete = all && count >= 22;
                    } else complete = count >= ("jpg".equals(type.ext) ? 128 : "pdf".equals(type.ext) ? 64 : 24);
                    if (complete) break;
                }
            }
        } catch (IOException ex) { target.delete(); throw ex; }
        if (stop.get() || !complete || ("zip".equals(type.ext) && !isValidZip(target))) {
            target.delete(); return null;
        }
        return new Result(target,type.ext,type.mime,offset);
    }
    /** Consumes source sequentially, never writes to the source InputStream's underlying media. */
    public static Report scan(InputStream source, File directory, AtomicBoolean stop, Listener listener)
            throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No se pudo crear caché interna");
        Counting input = new Counting(source);
        byte[] recent = new byte[8];
        long available = 0;
        int recovered = 0;
        long cached = 0, lastProgress = 0;
        boolean cacheLimit = false;
        while (!stop.get() && recovered < MAX_FILES && cached < MAX_CACHE_BYTES) {
            int b = input.read(); if (b < 0) break;
            recent[(int)(available%recent.length)] = (byte)b;
            available++;
            Candidate candidate = null;
            for (Candidate c : TYPES) {
                if (suffix(recent, available, c.header)) { candidate=c; break; }
            }
            if (candidate != null) {
                long offset = input.bytes - candidate.header.length;
                Result result = capture(input, directory, candidate, offset, stop, MAX_CACHE_BYTES - cached);
                available = 0;
                if (result != null) {
                    cached += result.length;
                    recovered++;
                    listener.onFound(result);
                }
            }
            if (input.bytes-lastProgress >= 4L*1024*1024) {
                lastProgress = input.bytes;
                listener.onProgress(input.bytes, recovered);
            }
        }
        if (cached >= MAX_CACHE_BYTES) cacheLimit=true;
        listener.onProgress(input.bytes, recovered);
        return new Report(input.bytes,recovered,stop.get(),cacheLimit);
    }
    private SignatureCarver() {}
}