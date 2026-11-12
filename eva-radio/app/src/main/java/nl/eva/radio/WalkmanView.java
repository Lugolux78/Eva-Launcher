package nl.eva.radio;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;

/** Photorealistic Walkman skin; audio engine is unaffected. */
public final class WalkmanView extends View {
    private static final float W=1672,H=941;
    public interface Controller {
        void onPlayPause(); void onStopPlayback(); void onNext(); void onPrevious();
        void onBrowse(boolean favorites); void onSettings(); void onVolume(float fraction);
    }
    private final Controller controller;
    private final Bitmap shell,leftWheel,rightWheel;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private String name="Kies een zender", state="Gereed";
    private boolean playing,buffering,dragging;
    private int favoriteCount;
    private float volume=0.65f,angle=0;
    private long lastTick=SystemClock.uptimeMillis();

    public WalkmanView(Context context,Controller callback) {
        super(context);
        controller=callback;
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inScaled=false;
        shell=BitmapFactory.decodeResource(getResources(),R.drawable.walkman_shell,opts);
        leftWheel=Bitmap.createBitmap(shell,616,397,136,136);
        rightWheel=Bitmap.createBitmap(shell,922,397,136,136);
        setClickable(true);
    }
    public void setPlayback(String station,String status,boolean active,boolean waiting){
        if(station!=null&&!station.isEmpty())name=station;
        state=status==null?"":status;
        playing=active;buffering=waiting;lastTick=SystemClock.uptimeMillis();invalidate();
    }
    public void setFavoritesCount(int count){favoriteCount=count;invalidate();}
    public void setVolume(float fraction){volume=Math.max(0,Math.min(1,fraction));invalidate();}
    private float factor(){return Math.min(getWidth()/W,getHeight()/H);}
    private void textToFit(Canvas c,String text,float x,float y,float width,float max,float min,Paint paint){
        String s=text==null?"":text;
        paint.setTextSize(max);
        while(paint.measureText(s)>width&&paint.getTextSize()>min)
            paint.setTextSize(paint.getTextSize()-1);
        while(s.length()>1&&paint.measureText(s)>width)s=s.substring(0,s.length()-2)+"…";
        c.drawText(s,x,y,paint);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        c.drawColor(Color.rgb(8,7,7));
        float s=factor();
        c.save();
        c.translate((getWidth()-W*s)/2,(getHeight()-H*s)/2);
        c.scale(s,s);
        p.reset();p.setFilterBitmap(true);p.setAntiAlias(true);
        c.drawBitmap(shell,0,0,p);
        if(playing){
            long now=SystemClock.uptimeMillis();
            angle=(angle+Math.min(70,now-lastTick)*0.32f)%360;
            lastTick=now;
            drawReel(c,leftWheel,684,465,angle);
            drawReel(c,rightWheel,990,465,-angle);
            postInvalidateDelayed(32);
        }else lastTick=SystemClock.uptimeMillis();
        drawLcd(c);drawLabel(c);drawVolume(c);drawFavorites(c);
        c.restore();
    }
    private void drawReel(Canvas c,Bitmap b,float cx,float cy,float rotation){
        c.save();
        Path path=new Path();
        path.addCircle(cx,cy,65,Path.Direction.CW);
        c.clipPath(path);
        c.rotate(rotation,cx,cy);
        p.setColor(Color.WHITE);
        c.drawBitmap(b,cx-68,cy-68,p);
        c.restore();
    }
    private void drawLcd(Canvas c){
        p.reset();p.setAntiAlias(true);
        p.setColor(0xff1b1412);
        c.drawRoundRect(new RectF(697,103,1045,165),6,6,p);
        p.setTypeface(Typeface.create("monospace",Typeface.BOLD));
        p.setColor(0xfff5c182);
        p.setShadowLayer(3,0,0,0xff54250f);
        textToFit(c,name,711,150,322,37,17,p);
        p.clearShadowLayer();
        p.setColor(0xff21100f);
        c.drawRoundRect(new RectF(1109,103,1218,162),6,6,p);
        p.setColor(playing?0xffff5b37:0xfff1b476);
        p.setTypeface(Typeface.create("monospace",Typeface.BOLD));
        p.setTextSize(25);
        c.drawText(buffering?"...":playing?"LIVE":"PAUZE",1116,140,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        p.setColor(playing?0xffd03628:0xff715841);
        c.drawRoundRect(new RectF(1111,103,1212,159),7,7,p);
        p.setStyle(Paint.Style.FILL);
    }
    private void drawLabel(Canvas c){
        c.save();c.rotate(-1.1f,830,352);
        p.reset();p.setAntiAlias(true);
        p.setTypeface(Typeface.create("casual",Typeface.BOLD_ITALIC));
        p.setTextSkewX(-.10f);p.setColor(0xff192b58);
        p.setShadowLayer(.65f,0,.3f,0x66343546);
        textToFit(c,name,704,366,330,38,21,p);
        p.clearShadowLayer();c.restore();
    }
    private void drawVolume(Canvas c){
        p.reset();p.setAntiAlias(true);
        p.setColor(0xff211b1a);
        c.drawRoundRect(new RectF(1312,528,1583,607),16,16,p);
        p.setColor(0xff0c0b0b);
        c.drawRoundRect(new RectF(1325,546,1569,570),10,10,p);
        p.setColor(0xffed7f26);
        c.drawRoundRect(new RectF(1328,550,1328+239*volume,566),8,8,p);
        float knob=1329+238*volume;
        p.setShader(new LinearGradient(knob-28,520,knob+25,594,
          new int[]{0xffded2c9,0xff736763,0xffb6a7a0,0xff3b3635},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(knob-29,528,knob+29,590),12,12,p);
        p.setShader(null);
        p.setColor(0x9b211c1b);
        for(int i=-17;i<=17;i+=6)c.drawLine(knob+i,536,knob+i,581,p);
        p.setColor(0xffd3c6b9);p.setTextSize(20);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        c.drawText(Math.round(volume*100)+"%",1517,607,p);
    }
    private void drawFavorites(Canvas c){
        p.reset();p.setAntiAlias(true);p.setColor(0xff211c1c);
        c.drawRoundRect(new RectF(1390,282,1510,308),4,4,p);
        p.setColor(0xffd5c7b8);p.setTextSize(19);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        c.drawText(favoriteCount+" bewaard",1395,303,p);
    }
    private boolean hit(float x,float y,float a,float b,float e,float f){
        return x>=a&&x<=e&&y>=b&&y<=f;
    }
    private void volumeAt(float x){
        float f=Math.max(0,Math.min(1,(x-1329)/238));
        setVolume(f);controller.onVolume(f);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        float s=factor();
        float x=(e.getX()-(getWidth()-W*s)/2)/s;
        float y=(e.getY()-(getHeight()-H*s)/2)/s;
        int act=e.getActionMasked();
        if(act==MotionEvent.ACTION_DOWN){
            if(hit(x,y,1280,500,1590,625)){dragging=true;volumeAt(x);}
            return true;
        }
        if(act==MotionEvent.ACTION_MOVE&&dragging){volumeAt(x);return true;}
        if(act==MotionEvent.ACTION_CANCEL){dragging=false;return true;}
        if(act==MotionEvent.ACTION_UP){
            if(dragging){volumeAt(x);dragging=false;return true;}
            performClick();performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if(hit(x,y,427,690,654,860)||hit(x,y,1284,345,1365,460)){controller.onPrevious();return true;}
            if(hit(x,y,1139,690,1389,860)||hit(x,y,1518,345,1600,460)){controller.onNext();return true;}
            if(hit(x,y,652,690,925,860)){controller.onPlayPause();return true;}
            if(hit(x,y,927,690,1140,860)){controller.onStopPlayback();return true;}
            if(hit(x,y,1270,214,1608,343)){controller.onBrowse(true);return true;}
            if(hit(x,y,1360,345,1518,460)){controller.onBrowse(false);return true;}
            if(hit(x,y,120,70,480,205)){controller.onSettings();return true;}
            if(hit(x,y,578,68,1090,195)||hit(x,y,450,232,1254,622)){controller.onBrowse(false);return true;}
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
