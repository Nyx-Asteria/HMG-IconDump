package dev.hmg.icondump;
import android.util.Log;
import hmgapi.client.HmgApi;
import java.io.*;
import java.util.Date;

/** Read-only exact-file collector; the only /data exception is the stock navigation engine binary. */
public final class IconDump {
    static final String TAG="HMG-K5FirmwareDump";
    // Incremental navigation capture: previous audio, DiPO and framework dumps are archived in git.
    static final String ENGINE="/data/data/com.mnsoft.navi/BIN/libExSLNavi.so";
    static final String[] PATHS={
        ENGINE,
        "/system/lib/libExSLNavi.so",
        "/system/app/HKMC_iAP2Server.apk","/system/app/HKMC_iAP2Server.odex",
        "/system/app/Iap2Server.apk","/system/app/Iap2Server.odex",
        "/system/app/iap2server.apk","/system/app/iap2server.odex",
        "/system/app/IAP2Server.apk","/system/app/IAP2Server.odex",
        "/system/priv-app/Iap2Server.apk","/system/priv-app/Iap2Server.odex",
        "/system/priv-app/iap2server.apk","/system/priv-app/iap2server.odex",
        "/system/priv-app/IAP2Server.apk","/system/priv-app/IAP2Server.odex"
    };
    static final String[] APP_DIRS={"/system/app","/system/priv-app","/vendor/app"};
    static void status(String message) {
        System.out.println(message); System.out.flush(); Log.i(TAG,message);
    }
    public static void main(String[] args) {
        status("Navigation-only firmware collector RC4 started; waiting for USB or Dump now toggle");
        boolean pending=true, previous=false, diagnosticPending=false, diagnosticPrevious=false;
        while(true) {
            try {
                org.json.JSONObject settings=HmgApi.connect().settings();
                boolean requested=settings.optBoolean("dump_now",false);
                boolean diagnostic=settings.optBoolean("export_cpul_logs",false);
                if(diagnostic && !diagnosticPrevious){diagnosticPending=true;status("CPUL diagnostic export requested");}
                diagnosticPrevious=diagnostic;
                if(requested && !previous){pending=true;status("Manual dump requested");}
                previous=requested;
            } catch(Throwable t){status("Settings lookup failed: "+t);}
            if(pending || diagnosticPending) {
                try {
                    File dest=usb();
                    if(dest!=null){
                        if(diagnosticPending){diagnosticPending=false;exportDiagnostics(dest);}
                        if(pending){pending=false;run(dest);}
                    }
                } catch(Throwable t){pending=false;diagnosticPending=false;status("Collection failed: "+t);Log.e(TAG,"collection failed",t);}
            }
            try{Thread.sleep(5000);}catch(InterruptedException e){return;}
        }
    }
    // HMG-API LogKeeper records HMG-API-tagged mod logs here (current and rotated).
    // Explicit export only; never traverse app data, collect pairing records or dump all logcat.
    static void exportDiagnostics(File usb) throws IOException {
        File dir=new File(usb,"cpul-diagnostics-"+System.currentTimeMillis());
        if(!dir.mkdirs())throw new IOException("Cannot create diagnostic folder");
        File target=new File(dir,"cpul-runtime.log");
        int count=0;
        try(PrintWriter output=new PrintWriter(new OutputStreamWriter(new FileOutputStream(target),"UTF-8"));
            PrintWriter inventory=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir,"inventory.txt")),"UTF-8"))){
            inventory.println("CPUL diagnostic snapshot "+new Date());
            for(String path:new String[]{"/data/hmgapi/logs/hmgapi/log.log.1","/data/hmgapi/logs/hmgapi/log.log"}){
                int lines=0;
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(path),"UTF-8"))){
                    String line;
                    while((line=reader.readLine())!=null){
                        if(!isCpulDiagnostic(line))continue;
                        output.println(line);lines++;
                    }
                    count+=lines;
                    inventory.println("READ "+path+" matching="+lines);
                }catch(IOException e){inventory.println("UNAVAILABLE "+path+" "+e.getClass().getSimpleName());}
            }
            inventory.println("FINISHED matching="+count);
            if(output.checkError()||inventory.checkError())throw new IOException("USB diagnostic write failed");
        }
        status("CPUL diagnostic export finished "+dir+" matching="+count);
    }
    static boolean isCpulDiagnostic(String line){
        int start=line.indexOf("): ");
        if(start<0||!line.substring(0,start).contains("HMG-API"))return false;
        // Exact mod prefix; omit unrelated mods and raw system/network logs.
        String msg=line.substring(start+3);
        return msg.startsWith("[carplay-ultra-lite-k5] ");
    }
    static void run(File usb) throws Exception {
        status("Collecting firmware to "+usb);
        File out=new File(usb,"capture-"+System.currentTimeMillis());
        if(!out.mkdirs())throw new IOException("Cannot create capture folder");
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(out,"inventory.txt")),"UTF-8"),true);
        int ok=0,missing=0,failed=0;
        try {
            log.println("K5 incremental navigation firmware dump 1.0.0-rc.4 "+new Date());
            java.util.Set<String> candidates=new java.util.LinkedHashSet<String>();
            java.util.Collections.addAll(candidates,PATHS);
            // Discover case/vendor variations of the server package, not unrelated APKs.
            for(String path:APP_DIRS){
                File[] files=new File(path).listFiles();
                if(files==null){log.println("UNAVAILABLE DIRECTORY "+path);continue;}
                for(File f:files){
                    String n=f.getName().toLowerCase(java.util.Locale.ROOT);
                    if(f.isFile() && n.contains("iap2") && n.contains("server")
                            && (n.endsWith(".apk") || n.endsWith(".odex"))) candidates.add(f.getAbsolutePath());
                    if(f.isDirectory() && n.contains("iap2") && n.contains("server")){
                        File[] children=f.listFiles();
                        if(children!=null)for(File child:children){
                            String cn=child.getName().toLowerCase(java.util.Locale.ROOT);
                            if(child.isFile() && (cn.endsWith(".apk") || cn.endsWith(".odex"))) candidates.add(child.getAbsolutePath());
                        }
                    }
                }
            }
            for(String path:candidates) {
                File source=new File(path);
                if(!source.isFile()){log.println("MISSING OR INACCESSIBLE "+path);missing++;continue;}
                if(copy(source,out,log))ok++;else failed++;
            }
            log.println("FINISHED copied="+ok+" missing="+missing+" failed="+failed);
            status("Finished "+out+" copied="+ok+" missing="+missing+" failed="+failed);
        } finally {log.close();}
    }
    static boolean copy(File source,File out,PrintWriter log){
        File target=new File(out,"firmware"+source.getAbsolutePath());
        File tmp=new File(target.getPath()+".partial");
        try {
            String canonical=source.getCanonicalPath();
            if(!canonical.startsWith("/system/")&&!canonical.startsWith("/vendor/")
                    && !(ENGINE.equals(source.getAbsolutePath()) && ENGINE.equals(canonical)))throw new IOException("outside approved firmware paths");
            File parent=target.getParentFile();
            if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("mkdir");
            if(!source.canRead())throw new IOException("not readable");
            if(parent.getUsableSpace()<source.length()+1048576L)throw new IOException("insufficient USB space");
            long size=0;
            try(InputStream in=new FileInputStream(source);FileOutputStream stream=new FileOutputStream(tmp)){
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){stream.write(buf,0,n);size+=n;}
                stream.getFD().sync();
            }
            if(size!=source.length()||tmp.length()!=size||!tmp.renameTo(target)||target.length()!=size)throw new IOException("incomplete copy");
            log.println("FILE "+source+" size="+size+" (hash deferred to host)");
            status("Copied "+source+" bytes="+size);
            return true;
        }catch(Exception e){tmp.delete();log.println("FAILED "+source+" "+e);status("FAILED "+source+" "+e);return false;}
    }
    static File usb(){
        try{
            for(String path:HmgApi.connect().usbFolders()){
                File f=new File(path);
                status("Registered USB candidate: "+f);
                if(writable(f))return f;
            }
        }catch(Throwable t){status("Registered USB lookup failed: "+t);}
        for(String root:new String[]{"/storage/usb0","/storage/usb1","/storage/usb2",
                "/mnt/media_rw/usb0","/mnt/media_rw/usb1","/mnt/media_rw/usb2"}){
            File base=new File(root);
            if(!mounted(base))continue;
            File folder=new File(base,"HMGAPI/K5FirmwareDump");
            status("Mounted fallback USB candidate: "+folder);
            if((folder.isDirectory()||folder.mkdirs())&&writable(folder))return folder;
        }
        return null;
    }
    static boolean mounted(File base){
        try {
            String canonical=base.getCanonicalPath();
            BufferedReader r=new BufferedReader(new FileReader("/proc/mounts"));
            try{String line;while((line=r.readLine())!=null){
                String[] f=line.split(" ");
                if(f.length>2&&(f[1].equals(base.getPath())||f[1].equals(canonical)))return true;
            }}finally{r.close();}
        }catch(IOException e){status("Mount check failed: "+e);}
        return false;
    }
    static boolean writable(File dir){
        if(!dir.isDirectory()||!dir.canWrite()){status("USB folder not writable: "+dir);return false;}
        File probe=null;
        try{probe=File.createTempFile(".k5-write-",".tmp",dir);return true;}
        catch(IOException e){status("USB write probe failed: "+e);return false;}
        finally{if(probe!=null)probe.delete();}
    }
}

