package com.studio72f.freedom;

import android.graphics.*;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** Original procedural XMB-style waves, with no Sony artwork or logos. */
final class Ps3Backdrop extends Drawable implements Animatable,Runnable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbon=new Path();
    private final int base,accent;
    private boolean running;
    private float phase;
    private Shader gradient;
    Ps3Backdrop(int base,int accent){this.base=base;this.accent=accent;}
    @Override protected void onBoundsChange(Rect r){gradient=new LinearGradient(0,0,r.width(),r.height(),new int[]{base,Color.rgb(16,40,63),Color.rgb(3,8,15)},null,Shader.TileMode.CLAMP);}
    @Override public void draw(Canvas canvas){
        Rect r=getBounds();paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setShader(gradient);canvas.drawRect(r,paint);paint.setShader(null);
        for(int wave=0;wave<4;wave++){
            ribbon.reset();
            for(int i=0;i<=48;i++){
                float x=r.width()*i/48f;
                float y=r.height()*(.38f+wave*.035f)+(float)Math.sin(i/48f*5.5f+phase+wave*.48f)*r.height()*.055f;
                if(i==0)ribbon.moveTo(x,y);else ribbon.lineTo(x,y);
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r.height()*.035f);paint.setColor(MetroArt.alpha(accent,.025f));canvas.drawPath(ribbon,paint);
            paint.setStrokeWidth(r.height()*.009f);paint.setColor(MetroArt.alpha(accent,.05f));canvas.drawPath(ribbon,paint);
            paint.setStrokeWidth(Math.max(1,r.width()/800f));paint.setColor(MetroArt.alpha(accent,.16f));canvas.drawPath(ribbon,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<18;i++){
            float x=(i*.173f%1)*r.width(),y=(i*.271f%1)*r.height();
            float opacity=.05f+.09f*(float)((Math.sin(phase*.8+i)+1)/2);
            paint.setColor(MetroArt.alpha(accent,opacity));canvas.drawCircle(x,y,Math.max(1,r.width()/700f),paint);
        }
    }
    @Override public void start(){if(running)return;running=true;scheduleSelf(this,SystemClock.uptimeMillis()+50);}
    @Override public void stop(){running=false;unscheduleSelf(this);}
    @Override public boolean isRunning(){return running;}
    @Override public void run(){if(!running)return;phase+=.012f;invalidateSelf();scheduleSelf(this,SystemClock.uptimeMillis()+50);}
    @Override public void setAlpha(int value){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
