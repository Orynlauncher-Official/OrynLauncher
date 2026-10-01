package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class OrynCosmeticPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap skin, cape;
    private boolean slim;
    private float rotation = -18f, lastX;

    public OrynCosmeticPreviewView(Context c) { super(c); paint.setFilterBitmap(true); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

    public void setSkin(Bitmap b, boolean isSlim) { skin = b; slim = isSlim; invalidate(); }
    public void setCape(Bitmap b) { cape = b; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx=getWidth()/2f, cy=getHeight()/2f;
        float scale=Math.min(getWidth(), getHeight())/250f;
        float yaw=(float)Math.sin(Math.toRadians(rotation));
        float depth=Math.max(0.25f, Math.abs(yaw));
        paint.setColor(0xFF101216); c.drawRoundRect(new RectF(cx-92*scale, cy+82*scale, cx+92*scale, cy+100*scale), 9*scale,9*scale,paint);
        // cape behind the body
        if(cape != null) drawCape(c,cx,cy,scale,depth);
        drawPart(c,cx-24*scale,cy-78*scale,48*scale,48*scale,8,0,8,8,16,16,scale,yaw);
        drawPart(c,cx-23*scale,cy-27*scale,46*scale,52*scale,20,16,4,12,8,20,scale,yaw);
        float arm=slim?9:10;
        drawPart(c,cx-33*scale,cy-25*scale,arm*scale,50*scale,44,16,4,12,8,20,scale,yaw);
        drawPart(c,cx+23*scale,cy-25*scale,arm*scale,50*scale,36,48,4,12,8,20,scale,-yaw);
        drawPart(c,cx-21*scale,cy+25*scale,19*scale,55*scale,4,16,4,12,8,12,scale,yaw);
        drawPart(c,cx+2*scale,cy+25*scale,19*scale,55*scale,20,48,4,12,8,12,scale,-yaw);
    }

    private void drawCape(Canvas c,float cx,float cy,float s,float d) {
        RectF r=new RectF(cx-40*s,cy-12*s,cx+40*s,cy+72*s);
        paint.setAlpha(235); c.drawBitmap(cape,null,r,paint); paint.setAlpha(255);
    }

    private void drawPart(Canvas c,float x,float y,float w,float h,int u,int v,int tw,int th,int sideW,int sideH,float s,float yaw) {
        if(skin==null){paint.setColor(0xFF8A8F99);c.drawRect(x,y,x+w,y+h,paint);return;}
        float frontW=w*(0.82f+0.18f*Math.abs(yaw));
        RectF front=new RectF(x,y,x+frontW,y+h);
        int sw=Math.max(1,Math.round(sideW*skin.getWidth()/64f));
        int sh=Math.max(1,Math.round(sideH*skin.getHeight()/64f));
        int su=Math.min(skin.getWidth()-sw,Math.round((u+tw)*skin.getWidth()/64f));
        int sv=Math.min(skin.getHeight()-sh,Math.round(v*skin.getHeight()/64f));
        int left=Math.min(skin.getWidth()-1,Math.round(u*skin.getWidth()/64f));
        int top=Math.min(skin.getHeight()-1,Math.round(v*skin.getHeight()/64f));
        int right=Math.min(skin.getWidth(),Math.max(left+1,su));
        int bottom=Math.min(skin.getHeight(),Math.max(top+1,Math.round((v+th)*skin.getHeight()/64f)));
        c.drawBitmap(skin,new android.graphics.Rect(left,top,right,bottom),front,paint);
        float side=Math.max(4*s,w*(0.15f+0.2f*Math.abs(yaw)));
        RectF sr=new RectF(x+frontW,y,x+frontW+side,y+h);
        c.drawBitmap(skin,new android.graphics.Rect(su,top,Math.min(skin.getWidth(),su+sw),bottom),sr,paint);
        paint.setColor(0x33000000); c.drawRect(sr,paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX; rotation+=dx*0.7f; lastX=e.getX(); invalidate(); return true;}
        return true;
    }
}