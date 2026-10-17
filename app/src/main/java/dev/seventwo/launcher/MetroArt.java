package dev.seventwo.launcher;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Small original vector marks and wallpaper treatment; no external art assets. */
final class MetroArt {
    static int alpha(int color, float amount) {
        return (Math.round(255 * amount) << 24) | (color & 0x00ffffff);
    }

    @android.annotation.SuppressLint("ViewConstructor") // created in code only
    static final class Icon extends View {
        private final UiIcons pack;
        private String name;
        private int color;
        private float glyphSize=22;
        private final boolean ring;
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context context, UiIcons pack, String name, int color, boolean ring) {
            super(context); this.pack=pack; this.name = name; this.color = color; this.ring = ring;
            setMinimumWidth(dp(48)); setMinimumHeight(dp(48)); setFocusable(true);
        }
        private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
        void setGlyphSize(float size){glyphSize=size;invalidate();}
        void setMark(String name, int color) { this.name=name; this.color=color; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(ring) {
                Paint p=ringPaint;p.setColor(alpha(color,.5f));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
                canvas.drawCircle(getWidth()/2f,getHeight()/2f,dp(19),p);
            }
            pack.draw(canvas,name,getWidth()/2f,getHeight()/2f,dp(ring?19:glyphSize),color);
        }
    }

    static final class Backdrop extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap image;
        private final int base, accent;
        private final float dim;
        private final boolean system;
        Backdrop(Bitmap image, int base, int accent, float dim, boolean system) {
            this.image=image;this.base=base;this.accent=accent;this.dim=dim;this.system=system;
        }
        @Override public void draw(Canvas c) {
            Rect r=getBounds();
            paint.setShader(null);
            if(image!=null&&!image.isRecycled()) {
                float scale=Math.max(r.width()/(float)image.getWidth(),r.height()/(float)image.getHeight());
                float w=image.getWidth()*scale,h=image.getHeight()*scale;
                c.drawBitmap(image,null,new RectF(r.centerX()-w/2,r.centerY()-h/2,r.centerX()+w/2,r.centerY()+h/2),paint);
            } else if(!system) c.drawColor(base);
            if(image!=null||system) {
                paint.setShader(new LinearGradient(0,0,0,r.height(),new int[]{alpha(base,dim*.75f),alpha(base,dim),alpha(base,Math.min(.98f,dim+.18f))},new float[]{0,.5f,1},Shader.TileMode.CLAMP));
                c.drawRect(r,paint);
            } else {
                paint.setShader(new RadialGradient(r.width()*.15f,r.height()*.08f,Math.max(1,r.width()*.95f),new int[]{alpha(accent,.09f),alpha(accent,0)},null,Shader.TileMode.CLAMP));
                c.drawRect(r,paint);
            }
            paint.setShader(null);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
