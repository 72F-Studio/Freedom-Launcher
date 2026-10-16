package dev.seventwo.launcher;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Dependency-free integration tests on Android's real resources, JSON, bitmap and preference APIs. */
public final class EngineChecks extends Instrumentation {
    int count;final ArrayList<File> clean=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private interface Checked{void run()throws Exception;}
    void reject(Checked action)throws Exception {boolean rejected=false;try{action.run();}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Invalid data accepted");count++;}
    void yes(boolean value){if(!value)throw new AssertionError("Check failed");count++;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();File root=new File(c.getFilesDir(),"engine-test-"+UUID.randomUUID());root.mkdirs();clean.add(root);
        ThemeEngine metro=new ThemeEngine(new JSONObject(),null);yes(metro.layout.equals("metro"));
        ThemeEngine ps3=new ThemeEngine(ThemeEngine.preset("Wave"),null);yes(ps3.layout.equals("xmb")&&ps3.backdrop.equals("waves"));
        ThemeEngine expressive=new ThemeEngine(ThemeEngine.preset("Expressive"),null);yes(expressive.expressive&&expressive.dynamic&&expressive.uiIconSet.equals("material"));
        ThemeEngine glass=new ThemeEngine(ThemeEngine.preset("Liquid Retro"),null);yes(glass.liquid&&glass.blur==18&&glass.glass==50);
        reject(()->new ThemeEngine(new JSONObject().put("version",999),null));
        reject(()->new ThemeEngine(new JSONObject().put("text","#000000"),null));
        reject(()->new ThemeEngine(new JSONObject().put("blur",99),null));
        reject(()->new ThemeEngine(new JSONObject().put("layout","run-script"),null));
        reject(()->new ThemeEngine(new JSONObject().put("uiIconSet","unknown"),null));
        for(String set:new String[]{"retro","wave","material"}){
            UiIcons pack=new UiIcons(c,new ThemeEngine(new JSONObject().put("uiIconSet",set),null));
            for(String name:UiIcons.NAMES)yes(pack.drawable(name)!=null);
        }
        if(android.os.Build.VERSION.SDK_INT>=33){new GlassSurface.Gpu();yes(true);}
        // The wallpaper is shared, retains its resolution, and refreshes on source/size changes.
        GlassBackdrop backdrop=new GlassBackdrop();
        android.graphics.drawable.GradientDrawable gradient=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.RED,Color.BLUE});
        gradient.setBounds(0,0,1000,500);
        Bitmap texture=backdrop.obtain(gradient,1000,500,1,false);
        yes(texture.getWidth()==1000&&texture.getHeight()==500);
        yes(backdrop.obtain(gradient,1000,500,1,false)==texture);
        yes(Color.red(texture.getPixel(50,250))>Color.red(texture.getPixel(950,250)));
        yes(backdrop.obtain(gradient,500,500,1,false)!=texture);
        backdrop.clear();yes(backdrop.obtain(null,500,500,1,false)==null);
        reject(()->ThemeEngine.safeFile(root,"../outside"));reject(()->ThemeEngine.safeFile(root,"/absolute"));
        reject(()->ThemeEngine.unpack(new ByteArrayInputStream(zip("../escape",new byte[]{1})),new File(root,"bad")));
        File icon=new File(root,"icon.png");Bitmap b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);b.eraseColor(Color.CYAN);try(OutputStream out=new FileOutputStream(icon)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
        JSONObject definition=new JSONObject().put("version",2).put("name","portable test").put("icons",new JSONObject().put("com.example.app","icon.png")).put("uiIcons",new JSONObject().put("search","icon.png"));
        ThemeEngine packed=new ThemeEngine(definition,root);ByteArrayOutputStream bytes=new ByteArrayOutputStream();packed.exportTo(bytes);
        File imported=new File(root,"unpacked");ThemeEngine.unpack(new ByteArrayInputStream(bytes.toByteArray()),imported);
        ThemeEngine reloaded=new ThemeEngine(ThemeEngine.readJson(new File(imported,"theme.json")),imported);yes(reloaded.icon("ignored","com.example.app").isFile());yes(reloaded.uiIcon("search").isFile());
        reject(()->new ThemeEngine(new JSONObject().put("icons",new JSONObject().put("com.example","missing.png")),root));
        reject(()->BackupStore.validate(new JSONObject().put("favorites","[\"0:a/b\",\"0:a/b\"]")));
        reject(()->BackupStore.validate(new JSONObject().put("merges","{\"music\":\"video\",\"video\":\"music\"}")));
        reject(()->BackupStore.validate(new JSONObject().put("unknown","x")));
        reject(()->BackupStore.validate(new JSONObject().put("contexts","[{\"id\":\"a\",\"name\":\"x\",\"widgets\":[\"script:1\"]}]")));
        reject(()->BackupStore.validate(new JSONObject().put("contexts","[{\"id\":\"a\",\"name\":\"x\"},{\"id\":\"a\",\"name\":\"y\"}]")));
        reject(()->BackupStore.validate(new JSONObject().put("contexts","[{\"id\":\"a\",\"name\":\"x\",\"start\":5000}]")));
        reject(()->BackupStore.validate(new JSONObject().put("contexts","[{\"id\":\"../a\",\"name\":\"x\"}]")));
        // Upgrade: old header/media settings become the everyday context.
        SharedPreferences legacy=c.getSharedPreferences("engine-test-legacy",0);legacy.edit().clear().putInt("header",30).putBoolean("media",true).commit();
        ArrayList<ContextRules.Rule> migrated=ContextStore.load(legacy);yes(migrated.size()==1&&migrated.get(0).fallback&&migrated.get(0).widgets.contains("clock")&&migrated.get(0).widgets.contains("media"));
        legacy.edit().clear().commit();
        SharedPreferences prefs=c.getSharedPreferences("engine-test",0);prefs.edit().clear().putString("wallpaperPath","engine-test-missing.jpg").putString("theme",packed.json.toString()).putString("favorites","[\"0:com.example/com.example.Main\"]").putBoolean("left",true).putInt("wallpaperDim",72).putString("custom","[\"work\"]").putString("assignments","{\"0:com.example/com.example.Main\":\"work\"}")
            .putString("contexts",ContextStore.write(Arrays.asList(ContextRules.template("morning","c1"),ContextRules.everyday(true,true))).toString()).commit();
        ByteArrayOutputStream backup=new ByteArrayOutputStream();BackupStore.export(c,prefs,packed,backup);
        BackupStore.Pending pending=BackupStore.prepare(c,new ByteArrayInputStream(backup.toByteArray()));clean.add(pending.directory);
        prefs.edit().putBoolean("left",false).putString("favorites","[]").remove("contexts").commit();BackupStore.apply(c,prefs,pending);
        ArrayList<ContextRules.Rule> restored=ContextStore.load(prefs);yes(restored.size()==2&&restored.get(0).name.equals("morning")&&restored.get(0).days==ContextRules.WEEKDAYS&&restored.get(1).fallback);
        yes(prefs.getBoolean("left",false)&&prefs.getInt("wallpaperDim",0)==72&&prefs.getString("favorites","").contains("com.example"));
        yes(new ThemeEngine(new JSONObject(prefs.getString("theme","{}")),BackupStore.themeDirectory(c,prefs)).icon("ignored","com.example.app").isFile());
        // Snapshot includes the chosen photo and retains an asset also used as wallpaper.
        prefs.edit().putString("wallpaperPath","engine-test-"+root.getName().substring("engine-test-".length())+"/icon.png").putString("wallpaperMode","photo").commit();
        ByteArrayOutputStream snapshot=new ByteArrayOutputStream();packed.exportSnapshot(c,prefs,snapshot);
        File snapshotRoot=new File(root,"snapshot");ThemeEngine.unpack(new ByteArrayInputStream(snapshot.toByteArray()),snapshotRoot);
        ThemeEngine snapshotTheme=new ThemeEngine(ThemeEngine.readJson(new File(snapshotRoot,"theme.json")),snapshotRoot);
        yes(snapshotTheme.asset(snapshotTheme.json.getString("wallpaper")).isFile());yes(snapshotTheme.icon("ignored","com.example.app").isFile());
        int before=prefs.getAll().size();reject(()->BackupStore.prepare(c,new ByteArrayInputStream(zip("backup.json","{\"kind\":\"72f-backup\",\"version\":99}".getBytes()))));yes(prefs.getAll().size()==before);
        Bitmap sample=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);sample.eraseColor(Color.RED);Bitmap blurred=ThemeEffects.blur(sample,18);yes(Color.red(blurred.getPixel(10,10))==255);blurred.recycle();sample.recycle();
        boolean foundPack=false;for(IconPacks.Pack pack:IconPacks.installed(c))if(pack.packageName.equals("dev.seventwo.iconfixture")){foundPack=true;IconPacks icons=new IconPacks(c,pack.packageName);yes(icons.icon(new ComponentName("com.android.settings","com.android.settings.Settings"))!=null);}
        yes(foundPack);
        prefs.edit().clear().commit();result.putString("stream",count+" engine checks passed\n");finish(-1,result);
    }catch(Throwable error){result.putString("stream","FAILED: "+error+"\n");finish(0,result);}finally{for(File f:clean)ThemeEngine.deleteTree(f);}}
    static byte[] zip(String path,byte[] contents)throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry(path));zip.write(contents);zip.closeEntry();}return out.toByteArray();}
}
