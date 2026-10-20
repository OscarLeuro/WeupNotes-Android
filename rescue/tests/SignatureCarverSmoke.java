import com.weupperu.rescate.SignatureCarver;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
/** JVM smoke test: valid PDF/ZIP, synthetic JPG and PNG signatures within surrounding free space. */
public class SignatureCarverSmoke {
    static final ByteArrayOutputStream src = new ByteArrayOutputStream();
    static void pad(int n) { for (int i=0;i<n;i++) src.write(0xAA); }
    public static void main(String[] ignored) throws Exception {
        Path tmp=Files.createTempDirectory("weuprescue-test-");
        pad(2048);
        long jpegAt=src.size();
        src.write(new byte[]{(byte)255,(byte)216,(byte)255,(byte)224}); pad(200);
        src.write(new byte[]{(byte)255,(byte)217});pad(1024);
        long pdfAt=src.size();
        src.write("%PDF-1.4\n1 0 obj << /Type /Catalog >>\nendobj\ntrailer <</Root 1 0 R>>\nstartxref\n0\n%%EOF".getBytes("US-ASCII"));
        pad(655);
        long zipAt=src.size();
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(archive)) {
            z.putNextEntry(new ZipEntry("sample.txt")); z.write("Hello recovered!".getBytes("UTF-8")); z.closeEntry();
        }
        src.write(archive.toByteArray()); pad(200);
        List<SignatureCarver.Result> results = new ArrayList<>();
        SignatureCarver.Report report = SignatureCarver.scan(new ByteArrayInputStream(src.toByteArray()),tmp.toFile(),new AtomicBoolean(),new SignatureCarver.Listener() {
            public void onFound(SignatureCarver.Result r) { results.add(r); }
            public void onProgress(long scanned,int count) { }
        });
        if (results.size()!=3) throw new AssertionError("Expected 3 signatures, got "+results.size());
        if (results.get(0).offset!=jpegAt || results.get(1).offset!=pdfAt || results.get(2).offset!=zipAt) throw new AssertionError("Bad offsets");
        try (ZipFile z = new ZipFile(results.get(2).file)) {
            if (z.getEntry("sample.txt")==null) throw new AssertionError("Bad ZIP");
        }
        for (SignatureCarver.Result r : results) {
            System.out.println(r.extension+" offset="+r.offset+" length="+r.length);
            r.file.delete();
        }
        Files.delete(tmp);
        System.out.println("PASS; read="+report.scannedBytes);
    }
}