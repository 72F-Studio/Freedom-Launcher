package com.studio72f.eigen;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import java.io.File;
import java.util.*;

/** One resource/asset pack for all launcher chrome; never character or emoji icons. */
final class UiIcons {
    static final Set<String> NAMES=new HashSet<>(Arrays.asList("favorites","social","music","video","photos","games","travel","tools","all apps","search","close","more","arrow","chevron","back","play","pause","previous","next","folder","settings"));
    private static final Map<String,int[]> RESOURCES=new HashMap<>();
    static {
        RESOURCES.put("favorites",new int[]{R.drawable.ui_retro_favorites,R.drawable.ui_wave_favorites,R.drawable.ui_material_favorites});
        RESOURCES.put("social",new int[]{R.drawable.ui_retro_social,R.drawable.ui_wave_social,R.drawable.ui_material_social});
        RESOURCES.put("music",new int[]{R.drawable.ui_retro_music,R.drawable.ui_wave_music,R.drawable.ui_material_music});
        RESOURCES.put("video",new int[]{R.drawable.ui_retro_video,R.drawable.ui_wave_video,R.drawable.ui_material_video});
        RESOURCES.put("photos",new int[]{R.drawable.ui_retro_photos,R.drawable.ui_wave_photos,R.drawable.ui_material_photos});
        RESOURCES.put("games",new int[]{R.drawable.ui_retro_games,R.drawable.ui_wave_games,R.drawable.ui_material_games});
        RESOURCES.put("travel",new int[]{R.drawable.ui_retro_travel,R.drawable.ui_wave_travel,R.drawable.ui_material_travel});
        RESOURCES.put("tools",new int[]{R.drawable.ui_retro_tools,R.drawable.ui_wave_tools,R.drawable.ui_material_tools});
        RESOURCES.put("all apps",new int[]{R.drawable.ui_retro_all_apps,R.drawable.ui_wave_all_apps,R.drawable.ui_material_all_apps});
        RESOURCES.put("search",new int[]{R.drawable.ui_retro_search,R.drawable.ui_wave_search,R.drawable.ui_material_search});
        RESOURCES.put("close",new int[]{R.drawable.ui_retro_close,R.drawable.ui_wave_close,R.drawable.ui_material_close});
        RESOURCES.put("more",new int[]{R.drawable.ui_retro_more,R.drawable.ui_wave_more,R.drawable.ui_material_more});
        RESOURCES.put("arrow",new int[]{R.drawable.ui_retro_arrow,R.drawable.ui_wave_arrow,R.drawable.ui_material_arrow});
        RESOURCES.put("chevron",new int[]{R.drawable.ui_retro_chevron,R.drawable.ui_wave_chevron,R.drawable.ui_material_chevron});
        RESOURCES.put("back",new int[]{R.drawable.ui_retro_back,R.drawable.ui_wave_back,R.drawable.ui_material_back});
        RESOURCES.put("play",new int[]{R.drawable.ui_retro_play,R.drawable.ui_wave_play,R.drawable.ui_material_play});
        RESOURCES.put("pause",new int[]{R.drawable.ui_retro_pause,R.drawable.ui_wave_pause,R.drawable.ui_material_pause});
        RESOURCES.put("previous",new int[]{R.drawable.ui_retro_previous,R.drawable.ui_wave_previous,R.drawable.ui_material_previous});
        RESOURCES.put("next",new int[]{R.drawable.ui_retro_next,R.drawable.ui_wave_next,R.drawable.ui_material_next});
        RESOURCES.put("folder",new int[]{R.drawable.ui_retro_folder,R.drawable.ui_wave_folder,R.drawable.ui_material_folder});
        RESOURCES.put("settings",new int[]{R.drawable.ui_retro_settings,R.drawable.ui_wave_settings,R.drawable.ui_material_settings});
    }
    private final Context context;private final ThemeEngine theme;
    private final Map<String,Drawable> cache=new HashMap<>();
    UiIcons(Context context,ThemeEngine theme){this.context=context;this.theme=theme;}
    Drawable drawable(String name){
        String key=NAMES.contains(name)?name:"folder";
        if(cache.containsKey(key))return cache.get(key);
        Drawable drawable=null;
        try {
            File custom=theme.uiIcon(key);
            if(custom!=null){Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(custom),(decoder,info,source)->{float scale=Math.min(1f,256f/Math.max(info.getSize().getWidth(),info.getSize().getHeight()));decoder.setTargetSize(Math.max(1,Math.round(info.getSize().getWidth()*scale)),Math.max(1,Math.round(info.getSize().getHeight()*scale)));});drawable=new BitmapDrawable(context.getResources(),bitmap);}
        }catch(Exception ignored){}
        if(drawable==null){int resource=RESOURCES.get(key)[theme.uiIconSet.equals("wave")?1:theme.uiIconSet.equals("material")?2:0];drawable=java.util.Objects.requireNonNull(androidx.core.content.ContextCompat.getDrawable(context,resource)).mutate();}
        cache.put(key,drawable);return drawable;
    }
    void draw(Canvas canvas,String name,float x,float y,float size,int color){
        Drawable drawable=drawable(name);drawable.setTint(color);
        drawable.setBounds(Math.round(x-size/2),Math.round(y-size/2),Math.round(x+size/2),Math.round(y+size/2));drawable.draw(canvas);
    }
}
