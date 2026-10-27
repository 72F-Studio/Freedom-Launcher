package com.studio72f.freedom;

import android.content.*;
import android.content.pm.*;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;

/** Reads standard appfilter resource mappings without loading third-party code. */
final class IconPacks {
    static final String[] ACTIONS={"org.adw.launcher.THEMES","com.novalauncher.THEME","com.anddoes.launcher.THEME"};
    static final class Pack {final String packageName,label;Pack(String p,String l){packageName=p;label=l;}}
    static List<Pack> installed(Context context) {
        Map<String,Pack> found=new TreeMap<>();PackageManager pm=context.getPackageManager();
        for(String action:ACTIONS)for(ResolveInfo info:pm.queryIntentActivities(new Intent(action),0)){
            String p=info.activityInfo.packageName;found.put(p,new Pack(p,info.loadLabel(pm).toString()));
        }
        ArrayList<Pack> packs=new ArrayList<>(found.values());packs.sort(Comparator.comparing(p->p.label.toLowerCase(Locale.ROOT)));return packs;
    }
    private final Resources resources;
    private final String packageName;
    private final Map<String,String> mappings=new HashMap<>();
    // Icon packs are other apps; their resources can only be found by name (ADW appfilter convention).
    @android.annotation.SuppressLint("DiscouragedApi")
    IconPacks(Context context,String packageName)throws Exception {
        this.packageName=packageName;resources=context.getPackageManager().getResourcesForApplication(packageName);
        int id=resources.getIdentifier("appfilter","xml",packageName);
        if(id!=0){android.content.res.XmlResourceParser parser=resources.getXml(id);try{parse(parser);}finally{parser.close();}}
        else try(InputStream in=context.getPackageManager().getResourcesForApplication(packageName).getAssets().open("appfilter.xml")){
            XmlPullParser parser=Xml.newPullParser();parser.setInput(in,"UTF-8");parse(parser);
        }
        if(mappings.isEmpty())throw new IOException("This pack has no supported appfilter mappings");
    }
    private void parse(XmlPullParser parser)throws Exception {
        int count=0;
        while(parser.next()!=XmlPullParser.END_DOCUMENT){if(++count>200000)throw new IOException("Icon pack mapping too large");
            if(parser.getEventType()==XmlPullParser.START_TAG&&parser.getName().equals("item")){
                String component=parser.getAttributeValue(null,"component"),drawable=parser.getAttributeValue(null,"drawable");
                if(component!=null&&drawable!=null&&component.startsWith("ComponentInfo{")&&component.endsWith("}")){
                    ComponentName c=ComponentName.unflattenFromString(component.substring(14,component.length()-1));
                    if(c!=null)mappings.put(c.flattenToString(),drawable);
                }
            }
        }
    }
    @android.annotation.SuppressLint("DiscouragedApi")
    Drawable icon(ComponentName component) {
        String name=mappings.get(component.flattenToString());if(name==null)return null;
        int id=resources.getIdentifier(name,"drawable",packageName);
        if(id==0)return null;try{return androidx.core.content.res.ResourcesCompat.getDrawable(resources,id,null);}catch(Resources.NotFoundException e){return null;}
    }
}
