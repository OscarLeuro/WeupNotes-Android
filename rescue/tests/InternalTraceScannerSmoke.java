import com.weupperu.rescate.InternalTraceScanner;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class InternalTraceScannerSmoke {
  public static void main(String[] args) throws Exception {
    File root=Files.createTempDirectory("weup-scan").toFile();
    File hidden=new File(root,".thumbnails"); hidden.mkdir();
    File f=new File(hidden,"unlabeled.bin");
    try (FileOutputStream out=new FileOutputStream(f)) { out.write(new byte[]{(byte)255,(byte)216,(byte)255,(byte)224});out.write(new byte[100]); }
    final int[] count={0};
    InternalTraceScanner.Report r=InternalTraceScanner.scan(root,new AtomicBoolean(false),100,new InternalTraceScanner.Listener(){
      public void onFound(File file,String mime,boolean residual,String reason) { if (!residual || !"image/jpeg".equals(mime)) throw new AssertionError(); count[0]++; }
      public void onProgress(int a,int b) {}
    });
    if (count[0]!=1 || r.matches!=1) throw new AssertionError("Scanner failed");
    System.out.println("InternalTraceScanner smoke OK");
  }
}