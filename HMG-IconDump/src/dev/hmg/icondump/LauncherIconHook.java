package dev.hmg.icondump;
import android.content.res.Resources;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;
/** In-memory launcher menu experiment; never modifies system files or BuildInfo.VENDOR. */
public final class LauncherIconHook extends ModEntry {
    private volatile String mode="stock";
    private volatile boolean enabled=false;
    private volatile Resources resources;
    private int observed=0, changed=0, missing=0;
    @Override public void appLoaded(final ModContext mod, LoadedApp app) {
        if (!"com.android.launcher3".equals(app.packageName) || app.context==null) return;
        resources=app.context.getResources();
        reload(mod);
        try {
            mod.hook(app.classLoader,"launcher.menu_entry.new",new MethodHook(){
                @Override protected void beforeHookedMethod(Param p) {
                    if(p.args.length!=7 || !(p.args[4] instanceof Integer) || !(p.args[5] instanceof Integer)) return;
                    if(++observed<=30) mod.log("menu "+p.args[2]+"/"+p.args[3]+" icons="+p.args[4]+","+p.args[5]);
                    if(!enabled || "stock".equals(mode)) return;
                    for(int i=4;i<=5;i++){
                        int original=(Integer)p.args[i];
                        int alternative=alternate(original,mode);
                        if(alternative!=0 && alternative!=original){
                            p.args[i]=Integer.valueOf(alternative);
                            if(++changed<=50) mod.log("swapped "+Integer.toHexString(original)+" to "+Integer.toHexString(alternative));
                        } else if(++missing<=30) mod.log("no distinct "+mode+" drawable for "+Integer.toHexString(original));
                    }
                }
            });
            mod.log("menu-entry hook installed; mode="+mode+" enabled="+enabled);
        } catch(Throwable t){mod.error("launcher hook unavailable; stock icons retained",t);}
    }
    @Override public void onSettingsChanged(ModContext mod){reload(mod);}
    private void reload(ModContext mod){
        String requested=mod.settingString("icon_mode","stock").toLowerCase(java.util.Locale.US);
        if(!"kia".equals(requested)&&!"hyundai".equals(requested)) requested="stock";
        mode=requested; enabled=mod.settingBoolean("enable_icon_experiment",false);
        mod.log("icon mode="+mode+" enabled="+enabled);
    }
    private int alternate(int id,String brand){
        if(id==0 || resources==null) return 0;
        try {
            String type=resources.getResourceTypeName(id);
            if(!"drawable".equals(type)&&!"mipmap".equals(type)) return 0;
            String name=resources.getResourceEntryName(id);
            String pkg=resources.getResourcePackageName(id);
            String[] candidates={name+"_"+brand,brand+"_"+name,
                name.replace("_kia","_"+brand).replace("_hyundai","_"+brand)};
            for(String candidate:candidates){
                if(candidate.equals(name)) continue;
                int found=resources.getIdentifier(candidate,type,pkg);
                if(found!=0 && found!=id) return found;
            }
        } catch(Resources.NotFoundException ignored){}
        return 0;
    }
}
