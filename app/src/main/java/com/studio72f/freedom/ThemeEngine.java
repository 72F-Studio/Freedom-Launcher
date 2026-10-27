package com.studio72f.freedom;

import android.graphics.Color;
import android.graphics.Typeface;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Versioned, data-only themes. Packages contain JSON and explicitly referenced local assets. */
final class ThemeEngine {
    static final long MAX_PACKAGE = 48L * 1024 * 1024;
    static final int MAX_MANIFEST = 256 * 1024;
    final JSONObject json;
    final int background, text, muted, accent, titleSize, appSize, rowHeight, iconSize, motionMs, blur, glass;
    /** Glass tuning: blur in dp (-1 = automatic), tint -100 (darker) … 100 (lighter), refraction 0 … 100 (50 = default). */
    final int glassBlur, glassTint, refraction;
    final String layout, backdrop, font, iconStyle, uiIconSet, name;
    final boolean animated, liquid, expressive, dynamic;
    final Typeface typeface, titleTypeface;
    final File assets;

    ThemeEngine(JSONObject input, File assets) throws Exception {
        json = new JSONObject(input.toString());
        int version = json.optInt("version", 1);
        if (version < 1 || version > 2) throw new IOException("Unsupported theme version");
        this.assets = assets;
        name = json.optString("name", "custom theme");
        if (name.length() > 80) throw new IOException("Theme name is too long");
        background = color("background", "#000000"); text = color("text", "#F1F2F3");
        muted = color("muted", "#97989B"); accent = color("accent", "#FFEA00");
        if (contrast(background,text)<4.5 || contrast(background,muted)<3 || contrast(background,accent)<3)
            throw new IOException("Theme colors need more contrast");
        layout = choice("layout", "grid", "grid", "metro");
        uiIconSet = choice("uiIconSet","material","retro","wave","material");
        backdrop = choice("backdrop", "solid", "solid", "glow", "frosted");
        font = choice("font", "sans-serif", "sans-serif", "sans-serif-light", "sans-serif-thin", "sans-serif-condensed");
        choice("wallpaperMode","solid","solid","photo","system");
        String pack=json.optString("iconPack","");if(pack.length()>200||(!pack.isEmpty()&&!pack.matches("[A-Za-z0-9_.]+")))throw new IOException("Invalid icon pack reference");
        iconStyle = choice("iconStyle", "mono", "mono", "color", "none");
        titleSize = number("titleSize", 36, 24, 80);
        appSize = number("appSize", 20, 14, 36);
        rowHeight = number("rowHeight", 56, 48, 96);
        iconSize = number("iconSize", 40, 20, 48);
        motionMs = number("motionMs", 230, 0, 600);
        animated = json.optBoolean("animated", true);
        blur = number("blur",20,0,40); glass = number("glass",30,0,90);
        glassBlur = number("glassBlur",-1,-1,40); glassTint = number("glassTint",0,-100,100); refraction = number("refraction",50,0,100);
        liquid = json.optBoolean("liquid",false);
        // Material 3 Expressive: tonal containers, pill indicators, springy motion and emphasized type.
        expressive = json.optBoolean("expressive",false); dynamic = json.optBoolean("dynamic",false);
        String fontAsset = json.optString("fontAsset", "");
        typeface = fontAsset.isEmpty() ? Typeface.create(font,Typeface.NORMAL) : Typeface.createFromFile(asset(fontAsset));
        titleTypeface = expressive ? Typeface.create(typeface, 800, false) : typeface;
        String wallpaper = json.optString("wallpaper", "");
        if (!wallpaper.isEmpty()) validateImage(asset(wallpaper),8192);
        JSONObject icons = json.optJSONObject("icons");
        if (json.has("icons") && icons == null) throw new IOException("icons must be an object");
        if (icons != null) {
            if (icons.length() > 1000) throw new IOException("Too many theme icons");
            Iterator<String> keys = icons.keys();
            while (keys.hasNext()) { String key=keys.next(); if(key.length()>300)throw new IOException("Invalid icon key");validateImage(asset(icons.getString(key)),2048); }
        }
        JSONObject ui=json.optJSONObject("uiIcons");
        if(json.has("uiIcons")&&ui==null)throw new IOException("uiIcons must be an object");
        if(ui!=null){Iterator<String> keys=ui.keys();while(keys.hasNext()){String key=keys.next();if(!UiIcons.NAMES.contains(key))throw new IOException("Unknown launcher icon: "+key);validateImage(asset(ui.getString(key)),2048);}}
        json.put("uiIconSet",uiIconSet);
        json.put("version",2);json.put("name",name);json.put("layout",layout);json.put("backdrop",backdrop);
        json.put("titleSize",titleSize);json.put("appSize",appSize);json.put("rowHeight",rowHeight);
        json.put("iconSize",iconSize);json.put("motionMs",motionMs);json.put("iconStyle",iconStyle);json.put("animated",animated);json.put("blur",blur);json.put("glass",glass);json.put("glassBlur",glassBlur);json.put("glassTint",glassTint);json.put("refraction",refraction);json.put("liquid",liquid);json.put("expressive",expressive);json.put("dynamic",dynamic);
    }
    private int color(String key,String fallback)throws IOException {
        try {int c=Color.parseColor(json.optString(key,fallback));if(Color.alpha(c)!=255)throw new IOException("Use opaque colors");return c;}
        catch(IllegalArgumentException e){throw new IOException("Invalid color: "+key);}
    }
    private String choice(String key,String fallback,String... choices)throws IOException {
        String value=json.optString(key,fallback);if(!Arrays.asList(choices).contains(value))throw new IOException("Unsupported "+key);return value;
    }
    private int number(String key,int fallback,int min,int max)throws IOException {
        Object raw=json.opt(key);if(raw!=null&&!(raw instanceof Number))throw new IOException(key+" must be a number");
        int value=json.optInt(key,fallback);if(value<min||value>max)throw new IOException(key+" must be "+min+"–"+max);return value;
    }
    private static double contrast(int a,int b){double x=Color.luminance(a),y=Color.luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    File asset(String path)throws IOException {
        if(assets==null)throw new IOException("Import an asset theme as a .72ftheme package");
        File file=safeFile(assets,path);
        if(!file.isFile())throw new IOException("Missing theme asset: "+path);
        String lower=path.toLowerCase(Locale.ROOT);
        if(!lower.endsWith(".png")&&!lower.endsWith(".jpg")&&!lower.endsWith(".jpeg")&&!lower.endsWith(".webp")&&!lower.endsWith(".ttf")&&!lower.endsWith(".otf"))throw new IOException("Unsupported theme asset");
        return file;
    }
    static void validateImage(File file,int max)throws IOException {
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        android.graphics.BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>max||bounds.outHeight>max)throw new IOException("Invalid or oversized image: "+file.getName());
    }
    File icon(String component,String packageName)throws IOException {
        JSONObject icons=json.optJSONObject("icons");if(icons==null)return null;
        String path=icons.optString(component,icons.optString(packageName,""));return path.isEmpty()?null:asset(path);
    }
    File uiIcon(String name)throws IOException {
        JSONObject icons=json.optJSONObject("uiIcons");if(icons==null)return null;
        String path=icons.optString(name,"");return path.isEmpty()?null:asset(path);
    }
    static JSONObject preset(String name)throws JSONException {
        JSONObject t=new JSONObject();t.put("version",2);t.put("name",name);
        if(name.equals("Expressive")) {
            // M3 baseline dark scheme; replaced by the wallpaper's Material You palette on Android 12+.
            t.put("expressive",true);t.put("dynamic",true);t.put("uiIconSet","material");t.put("backdrop","solid");
            t.put("background","#141218");t.put("text","#E6E0E9");t.put("muted","#CAC4D0");t.put("accent","#D0BCFF");
            t.put("font","sans-serif");t.put("titleSize",44);t.put("appSize",20);t.put("rowHeight",56);t.put("iconSize",40);
            t.put("iconStyle","color");t.put("motionMs",350);
        } else if(name.equals("Ratio Frosted")||name.equals("Kelvin Dark")) {
            t.put("name","Ratio Frosted");t.put("uiIconSet","material");t.put("layout","grid");t.put("backdrop","solid");t.put("background","#000000");
            t.put("text","#F1F2F3");t.put("muted","#97989B");t.put("accent","#FFEA00");t.put("glass",30);t.put("blur",20);
            t.put("font","sans-serif");t.put("titleSize",24);t.put("appSize",14);t.put("iconSize",38);t.put("rowHeight",64);t.put("iconStyle","mono");
        } else if(name.equals("Kelvin Ember")) {
            t.put("name","Kelvin Ember");t.put("uiIconSet","material");t.put("layout","grid");t.put("backdrop","solid");t.put("background","#000000");
            t.put("text","#F1F2F3");t.put("muted","#97989B");t.put("accent","#FE6208");t.put("glass",30);t.put("blur",20);
            t.put("font","sans-serif");t.put("titleSize",24);t.put("appSize",14);t.put("iconSize",38);t.put("rowHeight",64);t.put("iconStyle","mono");
        } else if(name.equals("Minimaa Pure")) {
            t.put("name","Minimaa Pure");t.put("uiIconSet","material");t.put("layout","grid");t.put("backdrop","solid");t.put("background","#000000");
            t.put("text","#FFFFFF");t.put("muted","#666666");t.put("accent","#39B9D4");t.put("glass",10);t.put("blur",0);
            t.put("font","sans-serif-light");t.put("titleSize",24);t.put("appSize",14);t.put("iconSize",38);t.put("rowHeight",64);t.put("iconStyle","mono");
        } else {
            if(name.equals("Liquid Retro")||name.equals("Liquid Metro")){t.put("name","Liquid Retro");t.put("blur",18);t.put("glass",50);t.put("liquid",true);t.put("background","#06151D");}
            boolean paper=name.equals("paper · light");
            if(!t.has("background"))t.put("background",paper?"#F5F2EA":"#000000");t.put("text",paper?"#171717":"#FFFFFF");
            t.put("muted",paper?"#686868":"#898989");
            t.put("accent",paper?"#006C80":name.contains("cobalt")?"#6C8CFF":name.contains("magenta")?"#EF70BA":"#39B9D4");
        }
        return t;
    }
    static byte[] readBounded(InputStream in,long limit)throws IOException {
        if(in==null)throw new IOException("File unavailable");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("File exceeds size limit");out.write(buffer,0,n);}return out.toByteArray();
    }
    static JSONObject readJson(File file)throws Exception {
        try(InputStream in=new FileInputStream(file)){return new JSONObject(new String(readBounded(in,MAX_MANIFEST),StandardCharsets.UTF_8));}
    }
    static File safeFile(File directory,String path)throws IOException {
        if(path.isEmpty()||path.startsWith("/")||path.contains("\\")||path.contains(":")||path.length()>300)throw new IOException("Invalid asset path");
        for(String segment:path.split("/"))if(segment.equals("..")||segment.equals(".")||segment.isEmpty())throw new IOException("Invalid asset path");
        File file=new File(directory,path);
        if(!file.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator))throw new IOException("Asset escapes package");return file;
    }
    static void unpack(InputStream source,File directory)throws IOException {
        if(!directory.mkdirs()&&!directory.isDirectory())throw new IOException("Cannot prepare package");
        Set<String> seen=new HashSet<>();long total=0;int count=0;
        try(ZipInputStream in=new ZipInputStream(source)) {
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=in.getNextEntry())!=null){
                if(++count>1200||!seen.add(entry.getName()))throw new IOException("Invalid package entries");
                String path=entry.getName();if(entry.isDirectory()){safeFile(directory,path.endsWith("/")?path.substring(0,path.length()-1):path);continue;}
                File file=safeFile(directory,path);if(!file.getParentFile().mkdirs()&&!file.getParentFile().isDirectory())throw new IOException("Cannot create asset folder");
                long size=0;try(OutputStream out=new FileOutputStream(file)){int n;while((n=in.read(buffer))!=-1){size+=n;total+=n;if(total>MAX_PACKAGE||size>32L*1024*1024)throw new IOException("Package too large");out.write(buffer,0,n);}}
                in.closeEntry();
            }
        }
        if(count==0)throw new IOException("Empty or invalid ZIP package");
    }
    static void zipFile(ZipOutputStream zip,String path,File file)throws IOException {
        zip.putNextEntry(new ZipEntry(path));try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();
    }
    static void zipJson(ZipOutputStream zip,String path,JSONObject json)throws Exception {
        zip.putNextEntry(new ZipEntry(path));zip.write(json.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    void exportSnapshot(android.content.Context context,android.content.SharedPreferences prefs,OutputStream out)throws Exception {
        JSONObject definition=new JSONObject(json.toString());
        definition.put("iconStyle",prefs.getString("icons",iconStyle));definition.put("iconPack",prefs.getString("iconPack",""));
        String mode=prefs.getString("wallpaperMode","solid");definition.put("wallpaperMode",mode);
        File photo=BackupStore.wallpaperFile(context,prefs);String embedded="assets/wallpaper-"+UUID.randomUUID()+".jpg";
        boolean includePhoto=mode.equals("photo")&&photo.isFile();
        if(includePhoto)definition.put("wallpaper",embedded);else definition.remove("wallpaper");
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            zipJson(zip,"theme.json",definition);
            for(String path:assetPaths())if(!path.equals(embedded))zipFile(zip,path,asset(path));
            if(includePhoto)zipFile(zip,embedded,photo);
        }
    }
    void exportTo(OutputStream out)throws Exception {
        try(ZipOutputStream zip=new ZipOutputStream(out)){zipJson(zip,"theme.json",json);for(String path:assetPaths())zipFile(zip,path,asset(path));}
    }
    Set<String> assetPaths() {
        Set<String> paths=new LinkedHashSet<>();for(String key:new String[]{"wallpaper","fontAsset"}){String path=json.optString(key,"");if(!path.isEmpty())paths.add(path);}
        for(String field:new String[]{"icons","uiIcons"}){JSONObject icons=json.optJSONObject(field);if(icons!=null){Iterator<String> keys=icons.keys();while(keys.hasNext())paths.add(icons.optString(keys.next()));}}return paths;
    }
    static void deleteTree(File directory) {
        File[] files=directory.listFiles();if(files!=null)for(File file:files)deleteTree(file);directory.delete();
    }
}
