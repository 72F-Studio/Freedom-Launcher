package com.studio72f.eigen;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;

/** Native chrome plus a bounded software blur for Android 9–11. */
final class ThemeEffects {
    static GradientDrawable glass(int background,int foreground,int strength,boolean liquid,float density) {
        float amount=strength/100f;
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{
            MetroArt.alpha(foreground,liquid?.025f+amount*.035f:amount*.08f),
            MetroArt.alpha(background,liquid?.025f+amount*.055f:.15f+amount*.45f),
            MetroArt.alpha(foreground,amount*.025f)});
        d.setCornerRadius((liquid?22:12)*density);
        // Kelvin design system: No drawn hairlines or strokes around glass
        d.setStroke(0, 0);return d;
    }
    static Bitmap blur(Bitmap source,int amount) {
        return blur(source,amount,640);
    }
    static Bitmap blur(Bitmap source,int amount,int maxDimension) {
        if(source==null||amount==0)return source;
        float scale=Math.min(1f,maxDimension/(float)Math.max(source.getWidth(),source.getHeight()));
        int w=Math.max(1,Math.round(source.getWidth()*scale)),h=Math.max(1,Math.round(source.getHeight()*scale));
        Bitmap small=Bitmap.createScaledBitmap(source,w,h,true);int[] pixels=new int[w*h],scratch=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
        if(small!=source)small.recycle();int radius=Math.max(1,Math.round(amount*scale));
        for(int pass=0;pass<3;pass++){box(pixels,scratch,w,h,radius,true);box(scratch,pixels,w,h,radius,false);}
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    /** Three-pass box blur (close to Gaussian) into the same bitmap, using caller-owned buffers. */
    static void blurInPlace(Bitmap target,int radius,int[] pixels,int[] scratch) {
        int w=target.getWidth(),h=target.getHeight();target.getPixels(pixels,0,w,0,0,w,h);
        for(int pass=0;pass<3;pass++){box(pixels,scratch,w,h,radius,true);box(scratch,pixels,w,h,radius,false);}
        target.setPixels(pixels,0,w,0,0,w,h);
    }
    private static void box(int[] src,int[] dst,int w,int h,int radius,boolean horizontal) {
        int lines=horizontal?h:w,length=horizontal?w:h,diameter=radius*2+1;
        for(int line=0;line<lines;line++){
            long r=0,g=0,b=0;for(int k=-radius;k<=radius;k++){int position=Math.max(0,Math.min(length-1,k));int c=src[horizontal?line*w+position:position*w+line];r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}
            for(int x=0;x<length;x++){
                dst[horizontal?line*w+x:x*w+line]=0xff000000|((int)(r/diameter)<<16)|((int)(g/diameter)<<8)|(int)(b/diameter);
                int remove=Math.max(0,x-radius),add=Math.min(length-1,x+radius+1);
                int a=src[horizontal?line*w+remove:remove*w+line],z=src[horizontal?line*w+add:add*w+line];
                r+=((z>>16)&255)-((a>>16)&255);g+=((z>>8)&255)-((a>>8)&255);b+=(z&255)-(a&255);
            }
        }
    }
}
