package dev.hmg.icondump;
import android.util.Log;
import hmgapi.client.HmgApi;
import java.io.*;
import java.security.MessageDigest;
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
    public static void main(String[] args) {
        try { run(); } catch(Throwable t) { Log.e(TAG,"collection failed",t); }
    }
    static void run() throws Exception {
        File usb=null;
        for(int i=0;i<120 && usb==null;i++){
            usb=usb(); if(usb==null) Thread.sleep(5000);
        }
        if(usb==null){Log.e(TAG,"No registered writable USB after ten minutes");return;}
        File out=new File(usb,"capture-"+System.currentTimeMillis());
        if(!out.mkdirs())throw new IOException("Cannot create capture folder");
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(out,"inventory.txt")),"UTF-8"),true);
        int ok=0,missing=0,failed=0;
        try {
            log.println("K5 firmware dump 1.0.0-rc.1 "+new Date());
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
            Log.i(TAG,"finished copied="+ok+" missing="+missing+" failed="+failed);
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
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            long size=0;
            try(InputStream in=new FileInputStream(source);FileOutputStream stream=new FileOutputStream(tmp)){
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){stream.write(buf,0,n);md.update(buf,0,n);size+=n;}
                stream.getFD().sync();
            }
            if(size!=source.length()||!tmp.renameTo(target))throw new IOException("incomplete copy");
            StringBuilder hex=new StringBuilder();
            for(byte b:md.digest())hex.append(String.format(java.util.Locale.US,"%02x",b&255));
            log.println("FILE "+source+" size="+size+" sha256="+hex);
            return true;
        }catch(Exception e){tmp.delete();log.println("FAILED "+source+" "+e);return false;}
    }
    static File usb(){
        try{
            for(String path:HmgApi.connect().usbFolders()){
                File f=new File(path);
                if(f.isDirectory()&&f.canWrite())return f;
            }
        }catch(Throwable t){Log.w(TAG,"USB lookup unavailable",t);}
        return null;
    }
}
