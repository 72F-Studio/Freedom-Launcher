package com.studio72f.eigen;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.ZipOutputStream;

/** Portable, bounded backups. Assets are staged before a single preference commit. */
final class BackupStore {
    static final String[] KEYS={"favorites","custom","disabled","assignments","merges","categories","header","left","media","icons","iconPack","wallpaperMode","wallpaperDim","theme","seeded","introduced","contexts","legibility","hidden","labels","order","dots"};
    /** The file was read but isn't a backup Eigen can restore: damaged, unsupported, or a value out of range. */
    static final class Invalid extends IOException { Invalid(String why){super(why);} }
    static final class Pending {
        final File directory;final JSONObject preferences;final ThemeEngine theme;
        Pending(File d,JSONObject p,ThemeEngine t){directory=d;preferences=p;theme=t;}
        String summary()throws JSONException{int f=new JSONArray(preferences.optString("favorites","[]")).length(),d=new JSONArray(preferences.optString("custom","[]")).length();return f+(f==1?" favourite":" favourites")+" and "+d+(d==1?" drawer of your own":" drawers of your own")+", with its look and wallpaper.\n\nThis replaces Eigen's settings on this phone. Your apps and Android permissions stay as they are.";}
    }
    static File wallpaperFile(Context c,SharedPreferences prefs) {
        String path=prefs.getString("wallpaperPath","wallpaper.jpg");
        try{return ThemeEngine.safeFile(c.getFilesDir(),path);}catch(IOException e){return new File(c.getFilesDir(),"wallpaper.jpg");}
    }
    static File themeDirectory(Context c,SharedPreferences prefs) {
        String path=prefs.getString("themeDir","");if(path.isEmpty())return null;
        try {File dir=ThemeEngine.safeFile(c.getFilesDir(),path);return dir.isDirectory()?dir:null;}catch(IOException e){return null;}
    }
    static JSONObject snapshot(SharedPreferences prefs)throws JSONException {
        JSONObject out=new JSONObject();Map<String,?> all=prefs.getAll();for(String key:KEYS)if(all.containsKey(key))out.put(key,all.get(key));return out;
    }
    static void export(Context context,SharedPreferences prefs,ThemeEngine theme,OutputStream out)throws Exception {
        JSONObject values=snapshot(prefs);values.put("theme",theme.json.toString());
        JSONObject manifest=new JSONObject().put("kind","72f-backup").put("version",1).put("preferences",values);
        File wallpaper=wallpaperFile(context,prefs);
        if(wallpaper.isFile())manifest.put("wallpaper","wallpaper.jpg");
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            ThemeEngine.zipJson(zip,"backup.json",manifest);
            ThemeEngine.zipJson(zip,"theme/theme.json",theme.json);
            for(String path:theme.assetPaths())ThemeEngine.zipFile(zip,"theme/"+path,theme.asset(path));
            if(wallpaper.isFile())ThemeEngine.zipFile(zip,"wallpaper.jpg",wallpaper);
        }
    }
    static Pending prepare(Context context,InputStream in)throws Exception {
        File stage=new File(context.getFilesDir(),"imports/"+UUID.randomUUID());
        try {
            ThemeEngine.unpack(in,stage);JSONObject manifest=ThemeEngine.readJson(new File(stage,"backup.json"));
            if(!manifest.optString("kind").equals("72f-backup")||manifest.optInt("version")!=1)throw new Invalid("Unsupported launcher backup");
            JSONObject values=manifest.getJSONObject("preferences");validate(values);
            ThemeEngine theme=new ThemeEngine(new JSONObject(values.optString("theme","{}")),new File(stage,"theme"));
            values.put("theme",theme.json.toString());
            String wallpaper=manifest.optString("wallpaper","");
            if(!wallpaper.isEmpty()&&!wallpaper.equals("wallpaper.jpg"))throw new Invalid("Invalid wallpaper reference");
            if(!wallpaper.isEmpty()){
                File file=ThemeEngine.safeFile(stage,wallpaper);
                android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeFile(file.getPath(),bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>8192||bounds.outHeight>8192)throw new Invalid("Invalid backup wallpaper");
            }else if(values.optString("wallpaperMode").equals("photo"))throw new Invalid("Backup photo is missing");
            return new Pending(stage,values,theme);
        }catch(Exception e){ThemeEngine.deleteTree(stage);throw e;}
    }
    static void validate(JSONObject p)throws Exception {
        Set<String> allowed=new HashSet<>(Arrays.asList(KEYS));Iterator<String> keys=p.keys();
        while(keys.hasNext()){
            String key=keys.next();if(!allowed.contains(key))throw new Invalid("Unknown backup setting: "+key);
            Object value=p.get(key);
            if(Arrays.asList("categories","left","media","seeded","introduced","dots").contains(key)) {if(!(value instanceof Boolean))throw new Invalid("Invalid toggle: "+key);}
            else if(Arrays.asList("header","wallpaperDim").contains(key)){if(!(value instanceof Number))throw new Invalid("Invalid number: "+key);}
            else if(!(value instanceof String))throw new Invalid("Invalid setting: "+key);
        }
        if(!Arrays.asList(0,10,20,30,40).contains(p.optInt("header",20)))throw new Invalid("Invalid header height");
        if(p.optInt("wallpaperDim",60)<0||p.optInt("wallpaperDim",60)>90)throw new Invalid("Invalid wallpaper dimming");
        if(!Arrays.asList("solid","photo","system").contains(p.optString("wallpaperMode","solid")))throw new Invalid("Invalid wallpaper mode");
        if(!Arrays.asList("mono","color","none").contains(p.optString("icons","mono")))throw new Invalid("Invalid icon style");
        String pack=p.optString("iconPack","");if(pack.length()>200||(!pack.isEmpty()&&!pack.matches("[A-Za-z0-9_.]+")))throw new Invalid("Invalid icon pack");
        Set<String> groups=new HashSet<>(Arrays.asList("social","music","video","photos","games","travel","tools"));
        JSONArray custom=new JSONArray(p.optString("custom","[]"));
        if(custom.length()>100)throw new Invalid("Too many categories");
        for(int i=0;i<custom.length();i++){String group=custom.getString(i);if(group.trim().isEmpty()||group.length()>24||group.equals("favorites")||group.equals("all apps")||!groups.add(group))throw new Invalid("Invalid category");}
        JSONArray favorites=new JSONArray(p.optString("favorites","[]"));Set<String> ids=new HashSet<>();
        if(favorites.length()>20)throw new Invalid("Too many favorites");
        for(int i=0;i<favorites.length();i++){String id=favorites.getString(i);if(id.length()>400||!id.contains(":")||!ids.add(id))throw new Invalid("Invalid favorite");}
        JSONArray disabled=new JSONArray(p.optString("disabled","[]"));for(int i=0;i<disabled.length();i++)if(!groups.contains(disabled.getString(i)))throw new Invalid("Invalid hidden category");
        JSONObject assignments=new JSONObject(p.optString("assignments","{}"));if(assignments.length()>5000)throw new Invalid("Too many app assignments");
        Iterator<String> apps=assignments.keys();while(apps.hasNext()){String id=apps.next();if(id.length()>400||!groups.contains(assignments.getString(id)))throw new Invalid("Invalid app assignment");}
        JSONObject merges=new JSONObject(p.optString("merges","{}"));Iterator<String> from=merges.keys();
        while(from.hasNext()){
            String source=from.next();if(!groups.contains(source)||!groups.contains(merges.getString(source)))throw new Invalid("Invalid category merge");
            Set<String> seen=new HashSet<>();String node=source;
            while(merges.has(node)){if(!seen.add(node))throw new Invalid("Category merge cycle");node=merges.getString(node);}
        }
        new JSONObject(p.optString("theme","{}"));
        if(p.has("contexts"))ContextStore.parse(p.getString("contexts"));
        if(!Arrays.asList(Legibility.MODES).contains(p.optString("legibility","auto")))throw new Invalid("Invalid text legibility");
        JSONArray hiddenApps=new JSONArray(p.optString("hidden","[]"));if(hiddenApps.length()>5000)throw new Invalid("Too many hidden apps");
        for(int i=0;i<hiddenApps.length();i++){String id=hiddenApps.getString(i);if(id.length()>400||!id.contains(":"))throw new Invalid("Invalid hidden app");}
        JSONObject names=new JSONObject(p.optString("labels","{}"));if(names.length()>5000)throw new Invalid("Too many app names");
        Iterator<String> named=names.keys();while(named.hasNext()){String id=named.next();String name=names.getString(id);if(id.length()>400||name.trim().isEmpty()||name.length()>40)throw new Invalid("Invalid app name");}
        JSONArray order=new JSONArray(p.optString("order","[]"));if(order.length()>200)throw new Invalid("Invalid category order");
        for(int i=0;i<order.length();i++)if(!groups.contains(order.getString(i)))throw new Invalid("Invalid category order");
    }
    // Restore must know whether the write landed (and roll back if not), so commit() is intentional.
    @android.annotation.SuppressLint("ApplySharedPref")
    static void apply(Context context,SharedPreferences prefs,Pending pending)throws Exception {
        // Files already reside in a unique immutable directory; readers switch via one commit.
        String relative="imports/"+pending.directory.getName();
        Map<String,?> previous=prefs.getAll();
        SharedPreferences.Editor edit=prefs.edit().clear();JSONObject values=pending.preferences;
        // Plus belongs to this install and its Play account, not to the backup: keep it across the clear.
        for(String key:new String[]{"plus_checked",EigenBilling.PREF_OWNED,EigenBilling.PREF_GRANDFATHERED})if(previous.get(key) instanceof Boolean)edit.putBoolean(key,(Boolean)previous.get(key));
        Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();Object v=values.get(key);if(v instanceof Boolean)edit.putBoolean(key,(Boolean)v);else if(v instanceof Number)edit.putInt(key,((Number)v).intValue());else edit.putString(key,(String)v);}
        edit.putString("themeDir",relative+"/theme");
        edit.putString("wallpaperPath",relative+"/wallpaper.jpg");
        edit.putBoolean("introduced",true).putBoolean("seeded",true);
        if(!edit.commit()){
            SharedPreferences.Editor rollback=prefs.edit().clear();
            for(Map.Entry<String,?> entry:previous.entrySet()){
                Object value=entry.getValue();String key=entry.getKey();
                if(value instanceof String)rollback.putString(key,(String)value);
                else if(value instanceof Boolean)rollback.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)rollback.putInt(key,(Integer)value);
                else if(value instanceof Long)rollback.putLong(key,(Long)value);
                else if(value instanceof Float)rollback.putFloat(key,(Float)value);
            }
            rollback.commit();throw new IOException("Couldn't persist restored settings");
        }
    }
}
