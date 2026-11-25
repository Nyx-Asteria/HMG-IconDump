package dev.hmg.icondump;
import android.util.Log;
import hmgapi.client.HmgApi;
import java.io.*;
import java.util.Date;

/** Read-only, fixed allowlist; no /data, logs, pairing records or credentials. */
public final class IconDump {
    static final String TAG="HMG-K5FirmwareDump";
    static final String[] PATHS={
        "/system/app/HKMC_RadioNA.apk","/system/app/HKMC_RadioNA.odex",
        "/system/app/HKMC_Media.apk","/system/app/HKMC_Media.odex",
        "/system/app/HKMC_DiPO.apk","/system/app/HKMC_DiPO.odex",
        "/system/framework/automotive.jar","/system/framework/automotive.odex",
        "/system/framework/automotive-service.jar","/system/framework/automotive-service.odex",
        "/system/framework/framework.jar","/system/framework/framework.odex",
        "/system/framework/services.jar","/system/framework/services.odex",
        "/system/framework/com.hkmc.ext.hcan.jar","/system/framework/com.hkmc.ext.hcan.odex",
        "/system/lib/libCarPlayCore.so","/system/lib/libiap2session.so",
        "/system/lib/libleafiaaudio.so","/system/lib/libaudioflinger.so",
        "/system/lib/libaudiopolicy.so","/system/lib/libaudio.so","/system/lib/libmedia.so",
        "/system/etc/audio_policy.conf","/system/etc/audio_effects.conf",
        "/system/etc/mixer_paths.xml"
    };
    static final String[] AUDIO_DIRS={"/system/lib/hw","/vendor/lib/hw","/system/vendor/lib/hw"};
    static void status(String message) {
        System.out.println(message); System.out.flush(); Log.i(TAG,message);
    }
    public static void main(String[] args) {
        status("Firmware collector RC3 started; waiting for USB or Dump now toggle");
        boolean pending=true, previous=false;
        while(true) {
            try {
                boolean requested=HmgApi.connect().settings().optBoolean("dump_now",false);
                if(requested && !previous){pending=true;status("Manual dump requested");}
                previous=requested;
            } catch(Throwable t){status("Settings lookup failed: "+t);}
            if(pending) {
                try {
                    File dest=usb();
                    if(dest!=null){pending=false;run(dest);}
                } catch(Throwable t){pending=false;status("Collection failed: "+t);Log.e(TAG,"collection failed",t);}
            }
            try{Thread.sleep(5000);}catch(InterruptedException e){return;}
        }
    }
    static void run(File usb) throws Exception {
        status("Collecting firmware to "+usb);
        File out=new File(usb,"capture-"+System.currentTimeMillis());
        if(!out.mkdirs())throw new IOException("Cannot create capture folder");
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(out,"inventory.txt")),"UTF-8"),true);
        int ok=0,missing=0,failed=0;
        try {
            log.println("K5 firmware dump 1.0.0-rc.3 "+new Date());
            for(String path:PATHS) {
                File source=new File(path);
                if(!source.isFile()){log.println("MISSING "+path);missing++;continue;}
                if(copy(source,out,log))ok++;else failed++;
            }
            for(String path:AUDIO_DIRS){
                File[] files=new File(path).listFiles();
                if(files==null){log.println("UNAVAILABLE DIRECTORY "+path);continue;}
                for(File f:files){
                    String n=f.getName();
                    if(f.isFile() && n.endsWith(".so") && (n.startsWith("audio.primary.") || n.startsWith("audio_policy.") || n.startsWith("audio.a2dp.") || n.startsWith("audio.usb."))){
                        if(copy(f,out,log))ok++;else failed++;
                    }
                }
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
            if(!canonical.startsWith("/system/")&&!canonical.startsWith("/vendor/"))throw new IOException("outside firmware roots");
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

