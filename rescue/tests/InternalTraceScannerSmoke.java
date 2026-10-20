import com.weupperu.rescate.InternalTraceScanner;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Verifies ordinary gallery files never enter residual results, including videos. */
public class InternalTraceScannerSmoke {
    static void save(File f, byte[] header) throws Exception {
        f.getParentFile().mkdirs();
        try(FileOutputStream o=new FileOutputStream(f)) { o.write(header); o.write(new byte[128]); }
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("weup-residual-").toFile();
        byte[] jpeg={(byte)255,(byte)216,(byte)255,(byte)224};
        byte[] mp4={0,0,0,24,'f','t','y','p','i','s','o','m',0,0,0,0};
        save(new File(root,"DCIM/Camera/IMG_2024.jpg"),jpeg);
        save(new File(root,"Movies/myclip.mp4"),mp4);
        save(new File(root,"Pictures/backup-party.jpg"),jpeg);
        save(new File(root,"DCIM/.thumbnails/old.bin"),jpeg);
        save(new File(root,"Movies/.cache/old_video.bin"),mp4);
        final List<String> got=new ArrayList<>();
        InternalTraceScanner.Report r=InternalTraceScanner.scan(root,new AtomicBoolean(),50,new InternalTraceScanner.Listener(){
            public void onFound(File f,String mime,boolean residual,String reason) { if(!residual) throw new AssertionError("Normal file"); got.add(f.getName()+":"+mime); }
            public void onProgress(int a,int b){}
        });
        if(r.matches!=2 || !got.contains("old.bin:image/jpeg") || !got.contains("old_video.bin:video/mp4")) throw new AssertionError(got.toString());
        if(InternalTraceScanner.residualReason("/DCIM/Camera/IMG.jpg","IMG.jpg")!=null) throw new AssertionError("gallery accepted");
        System.out.println("Internal residual scanner OK; ordinary gallery excluded; photo and video signatures accepted");
    }
}
