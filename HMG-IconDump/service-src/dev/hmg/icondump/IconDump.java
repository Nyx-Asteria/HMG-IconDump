package dev.hmg.icondump;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import hmgapi.client.HmgApi;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
/** Read-only launcher resource and firmware collector, Android 4.4 compatible. */
public final class IconDump {
    private static final String TAG="HMG-IconDump";
    private static final String[] NAMES={
        "hom_soundmood_66","hom_hev_66","hom_nmode_66","hom_perfo_mode_66",
        "hom_qibla_66","hom_phone_66","hom_driv_info_66","hom_baidu_66",
        "hom_info_eu_66","hom_android_66","baidu_carlife","hom_mirrorlink_66",
        "hom_carplay_66","hom_data_66","hom_sxmdata_66","hom_hddata_66",
        "hom_dmb_66","hom_q_guide_66","hom_vc_66","hom_radio_66",
        "hom_music_66","hom_usb_vidio_66","home_map_66","hom_navi_66",
        "hom_climate_66","hom_set_66","hom_mymenu_66","hom_dvrs_66",
        "hom_qqmusic_66","hom_vm_66","hom_rear_a_66","hom_rear_b_66",
        "hom_other_iot_66","hom_nv_baidu_66"};
    public static void main(String[] args){
        try{new IconDump().run();}catch(Throwable t){Log.e(TAG,"collector failed",t);}
    }
    private void run() throws Exception {
        File dest=null;
        for(int attempt=0;attempt<120 && dest==null;attempt++){
            dest=usb();
            if(dest==null) Thread.sleep(5000);
        }
        if(dest==null){Log.e(TAG,"No USB after ten minutes; use service restart when USB inserted");return;}
        if(!dest.isDirectory()&&!dest.mkdirs()) throw new IOException("Cannot create "+dest);
        PrintWriter log=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dest,"results.txt")),"UTF-8"));
        try{
            log.println("HMG-IconDump 1.0.0-rc1 "+new Date());
            log.println("USB="+dest);
            Set<String> apks=new LinkedHashSet<String>();
            for(String p:new String[]{"/system/app","/system/priv-app"}){
                scan(new File(p),dest,apks,log,0);
            }
            for(String p:new String[]{"/system/build.prop","/system/etc/feature.xml"}){
                File f=new File(p);if(f.isFile()) copy(f,new File(dest,"firmware"+p),log);
            }
            int n=0;
            for(String p:apks){
                try{dump(new File(p),new File(dest,"icons/launcher-"+(++n)),log);}
                catch(Throwable t){log.println("APK FAILURE "+p+" "+t);}
            }
            log.println("COMPLETE launcherApks="+n);
        }finally{log.flush();log.close();}
    }
    private static void scan(File dir,File out,Set<String> apks,PrintWriter log,int depth){
        if(depth>7)return;
        File[] files=dir.listFiles();if(files==null)return;
        for(File f:files){
            if(f.isDirectory()){
                if(depth==0 && !f.getName().toLowerCase(Locale.US).contains("launcher"))continue;
                scan(f,out,apks,log,depth+1);
            }else{
                String name=f.getName().toLowerCase(Locale.US);
                if(depth==0&&!name.contains("launcher"))continue;
                if(!(name.endsWith(".apk")||name.endsWith(".odex")||name.endsWith(".vdex")
                    ||name.endsWith(".art")||name.endsWith(".oat")))continue;
                if(name.endsWith(".apk"))apks.add(f.getAbsolutePath());
                copy(f,new File(out,"firmware"+f.getAbsolutePath()),log);
            }
        }
    }
    private static void copy(File source,File target,PrintWriter log){
        if(!source.isFile()||!source.canRead()){log.println("SKIP "+source);return;}
        try{
            File parent=target.getParentFile();
            if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("mkdir "+parent);
            InputStream in=new FileInputStream(source);
            OutputStream out=new FileOutputStream(target);
            MessageDigest md=MessageDigest.getInstance("MD5");
            long size=0;
            try{
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){out.write(buf,0,n);md.update(buf,0,n);size+=n;}
                out.flush();
            }finally{try{in.close();}finally{out.close();}}
            log.println("FILE "+source+" size="+size+" md5="+hex(md.digest()));
        }catch(Throwable t){log.println("COPY FAILED "+source+" "+t);}
    }
    private static String hex(byte[] bytes){
        StringBuilder b=new StringBuilder();
        for(byte x:bytes)b.append(String.format(Locale.US,"%02x",x&255));
        return b.toString();
    }
    private static void dump(File apk,File dir,PrintWriter log)throws Exception{
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("mkdir "+dir);
        log.println("APK "+apk);
        AssetManager am=AssetManager.class.newInstance();
        Object cookie=AssetManager.class.getMethod("addAssetPath",String.class).invoke(am,apk.getAbsolutePath());
        if(!(cookie instanceof Integer)||((Integer)cookie)==0)throw new IOException("addAssetPath failed");
        Resources sys=Resources.getSystem();
        Resources res=new Resources(am,sys.getDisplayMetrics(),sys.getConfiguration());
        LinkedHashSet<String> names=new LinkedHashSet<String>(Arrays.asList(NAMES));
        ZipFile zip=new ZipFile(apk);
        try{
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();
                String p=e.getName();
                if(!p.startsWith("res/drawable")||p.endsWith("/"))continue;
                int slash=p.lastIndexOf('/'),dot=p.lastIndexOf('.');
                if(dot>slash)names.add(p.substring(slash+1,dot).replace(".9",""));
            }
        }finally{zip.close();}
        int success=0;
        for(String name:names){
            int id=0;
            try{
                // The package name is obtained from resource table ID 0x7f rather than guessed.
                // Android accepts a fully qualified resource lookup when the APK package is known.
                for(String pkg:new String[]{"com.android.launcher3","com.hkmc.launcher","com.android.launcher"}){
                    id=res.getIdentifier(name,"drawable",pkg);
                    if(id!=0)break;
                }
                if(id==0){log.println("MISSING "+name);continue;}
                Drawable drawable=res.getDrawable(id);
                int w=drawable.getIntrinsicWidth(),h=drawable.getIntrinsicHeight();
                if(w<=0||h<=0||w>2048||h>2048){w=128;h=128;}
                Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                try{
                    Canvas canvas=new Canvas(bitmap);
                    drawable.setBounds(0,0,w,h);drawable.draw(canvas);
                    File target=new File(dir,name+".png");
                    FileOutputStream stream=new FileOutputStream(target);
                    try{if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("PNG compress");}
                    finally{stream.close();}
                    log.println("ICON "+name+" id=0x"+Integer.toHexString(id)+" "+w+"x"+h);
                    success++;
                }finally{bitmap.recycle();}
            }catch(Throwable t){log.println("ICON FAILED "+name+" id=0x"+Integer.toHexString(id)+" "+t);}
        }
        log.println("APK ICON TOTAL "+success+"/"+names.size());
    }
    private static File usb(){
        try{
            for(String path:HmgApi.connect().usbFolders()){
                File f=new File(path);
                if(f.isDirectory()&&f.canWrite())return f;
            }
        }catch(Throwable t){Log.w(TAG,"HMG-API USB lookup unavailable",t);}
        for(String root:new String[]{"/storage/usb0","/storage/usb1","/storage/usb2",
            "/mnt/media_rw/usb0","/mnt/media_rw/usb1","/mnt/media_rw/usb2"}){
            File base=new File(root);
            if(base.isDirectory()&&base.canWrite()){
                File folder=new File(base,"HMGAPI/IconDump");
                if(folder.isDirectory()||folder.mkdirs())return folder;
            }
        }
        return null;
    }
}
