package nl.eva.radio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.HashMap;
import java.util.Map;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Fotorealistische, landscape Walkman-skin. Knoppen en indicatoren zijn
 * echte aanraakzones en tekenen hun toestand op dezelfde bronfoto.
 * De audiomotor blijft volledig onafhankelijk van deze View.
 */
public final class WalkmanView extends View {
    private static final float W = 1672f;
    private static final float H = 941f;
    public interface Controller {
        void onPlayPause();
        void onStopPlayback();
        void onEject();
        void onNext();
        void onPrevious();
        void onBrowse(boolean favorites);
        void onSettings();
        void onVolume(float fraction);
    }

    private final Controller controller;
    private final Bitmap shell;
    private final Bitmap leftWheel;
    private final Bitmap rightWheel;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private String station = "Kies een zender";
    private String status = "Gereed";
    private boolean playing;
    private boolean buffering;
    private int favorites;
    private int batteryLevel = -1;
    private final Map<String, Bitmap> dotCache = new HashMap<>();
    private float volumeFraction = 0.65f;
    private float angle = 0f;
    private long lastAnimation = SystemClock.uptimeMillis();
    private boolean volumeDragging;

    public WalkmanView(Context context, Controller controller) {
        super(context);
        this.controller = controller;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inScaled = false;
        Bitmap photo = BitmapFactory.decodeResource(getResources(), R.drawable.walkman_shell, opt);
        if (photo == null) throw new IllegalStateException("Walkman background missing");
        if (photo.getWidth() != 1672 || photo.getHeight() != 941) {
            shell = Bitmap.createScaledBitmap(photo, 1672, 941, true);
            photo.recycle();
        } else {
            shell = photo;
        }
        // Alleen de werkelijk zichtbare spoelkernen draaien, niet het cassettelabel.
        leftWheel = Bitmap.createBitmap(shell, 616, 397, 136, 136);
        rightWheel = Bitmap.createBitmap(shell, 922, 397, 136, 136);
        setClickable(true);
    }

    public void setPlayback(String name, String state, boolean isPlaying, boolean isBuffering) {
        if (name != null && !name.isEmpty()) station = name;
        status = state == null ? "" : state;
        playing = isPlaying;
        buffering = isBuffering;
        lastAnimation = SystemClock.uptimeMillis();
        invalidate();
    }

    public void setFavoritesCount(int count) { favorites = count; invalidate(); }
    public void setBatteryLevel(int percent) {
        int level = Math.max(0, Math.min(100, percent));
        if (batteryLevel != level) { batteryLevel = level; invalidate(); }
    }
    public void setVolume(float fraction) {
        volumeFraction = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    private float scale() {
        return Math.min(getWidth() / W, getHeight() / H);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.rgb(7, 6, 5));
        float factor = scale();
        float dx = (getWidth() - W * factor) / 2f;
        float dy = (getHeight() - H * factor) / 2f;
        c.save();
        c.translate(dx, dy);
        c.scale(factor, factor);
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        c.drawBitmap(shell, 0, 0, paint);

        if (playing) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(80f, now - lastAnimation);
            angle = (angle + dt * 0.32f) % 360f;
            lastAnimation = now;
            reel(c, leftWheel, 684, 465, angle);
            reel(c, rightWheel, 990, 465, angle);
            // Alleen tijdens afspelen renderen we nieuwe frames.
            postInvalidateDelayed(32);
        } else {
            lastAnimation = SystemClock.uptimeMillis();
        }

        drawDisplay(c);
        drawCassetteLabel(c);
        drawVolume(c);
        drawFavoriteCount(c);
        drawEject(c);
        c.restore();
    }

    private void reel(Canvas c, Bitmap b, float cx, float cy, float rotation) {
        c.save();
        Path region = new Path();
        region.addCircle(cx, cy, 65, Path.Direction.CW);
        c.clipPath(region);
        c.rotate(rotation, cx, cy);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(b, cx - 68, cy - 68, paint);
        c.restore();
    }

    private String fitted(Canvas c, String text, float x, float y, float maxWidth, float maxSize, float minSize, Paint p) {
        String value = text == null || text.isEmpty() ? "Kies een zender" : text;
        p.setTextSize(maxSize);
        while (p.measureText(value) > maxWidth && p.getTextSize() > minSize) {
            p.setTextSize(p.getTextSize() - 1f);
        }
        if (p.measureText(value) > maxWidth) {
            while (value.length() > 2 && p.measureText(value + "…") > maxWidth)
                value = value.substring(0, value.length() - 1);
            value += "…";
        }
        c.drawText(value, x, y, p);
        return value;
    }


    // Lichtpuntjes zijn voorgemonteerd in een kleine bitmap; bij de draaiende
    // spoelen hoeft het font daardoor niet 30 keer per seconde opnieuw opgebouwd.
    private Bitmap dotRaster(String label, int tone) {
        String key = label + ":" + tone;
        Bitmap cached = dotCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        if (dotCache.size() > 45) dotCache.clear();
        Paint letters = new Paint();
        letters.setAntiAlias(false);
        letters.setColor(Color.WHITE);
        letters.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        letters.setTextSize(14f);
        int width = Math.max(1, (int) Math.ceil(letters.measureText(label)) + 3);
        Bitmap source = Bitmap.createBitmap(width, 18, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(source);
        sc.drawText(label, 0, 14, letters);
        int pitch = 3;
        Bitmap dots = Bitmap.createBitmap(width * pitch, 18 * pitch, Bitmap.Config.ARGB_8888);
        Canvas dc = new Canvas(dots);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setColor(tone);
        for (int yy = 0; yy < 18; yy++) {
            for (int xx = 0; xx < width; xx++) {
                if (Color.alpha(source.getPixel(xx, yy)) > 100) {
                    dc.drawCircle(xx * pitch + 1.5f, yy * pitch + 1.5f, 1.12f, glow);
                }
            }
        }
        source.recycle();
        dotCache.put(key, dots);
        return dots;
    }

    private void dotText(Canvas c, String label, float x, float top, float maxWidth, int tone) {
        Bitmap dots = dotRaster(label.toUpperCase(java.util.Locale.ROOT), tone);
        float scale = Math.min(1f, maxWidth / dots.getWidth());
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(dots, null, new RectF(x, top, x + dots.getWidth() * scale,
                top + dots.getHeight() * scale), p);
    }

    private void drawDisplay(Canvas c) {
        // Donkere LCD-inleg laat de bestaande metalen en glazen displayrand staan.
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(0xff1b1412);
        c.drawRoundRect(new RectF(682, 93, 1585, 170), 7, 7, paint);
        paint.setColor(0x22856843);
        c.drawLine(686, 167, 1582, 167, paint);
        String label = station == null ? "" : station;
        if (label.length() > 34) label = label.substring(0, 33) + "…";
        dotText(c, label, 701, 106, 379, 0xffefbe81);

        String flag = buffering ? "..." : (playing ? "LIVE" :
                ("Gestopt".equalsIgnoreCase(status) ? "STOP" : "PAUZE"));
        dotText(c, flag, 1103, 110, 108,
                playing ? 0xffff845a : 0xffd8ac84);
        dotText(c, "VOL " + Math.round(volumeFraction * 100f) + "%", 1212, 110, 184,
                0xffe1ca93);

        // Actuele Android-batterij: 4 horizontaal aaneengesloten lichtstaafjes.
        float left = 1413f, top = 118f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xffb1c88b);
        c.drawRoundRect(new RectF(left, top, left + 65, top + 29), 3, 3, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(left + 66, top + 8, left + 71, top + 22), 1, 1, paint);
        int bars = batteryLevel < 0 ? 0 : (int) Math.ceil(batteryLevel / 25f);
        for (int bar = 0; bar < 4; bar++) {
            paint.setColor(bar < bars ? 0xffb8d292 : 0xff39432d);
            float x = left + 5 + bar * 15;
            c.drawRoundRect(new RectF(x, top + 5, x + 11, top + 24), 1, 1, paint);
        }
        dotText(c, batteryLevel < 0 ? "--%" : batteryLevel + "%",
                1490, 109, 88, 0xffbbd59a);
    }

    private void drawCassetteLabel(Canvas c) {
        // Donkerblauwe 'met de hand geschreven' naam op papieren cassette-etiket.
        c.save();
        c.rotate(-1.1f, 840, 352);
        paint.reset(); paint.setAntiAlias(true);
        paint.setTypeface(Typeface.create("casual", Typeface.BOLD_ITALIC));
        paint.setTextSkewX(-.10f);
        paint.setColor(Color.rgb(25, 43, 88));
        paint.setShadowLayer(.65f, 0f, .3f, 0x66343546);
        fitted(c, station, 704, 366, 330, 38, 21, paint);
        paint.clearShadowLayer();
        c.restore();
    }

private void drawVolume(Canvas c) {
        // Mechanische volumeschuif met geribbeld aluminium en schaal 0-10.
        paint.reset(); paint.setAntiAlias(true);
        paint.setColor(0xff1d1a19);
        c.drawRoundRect(new RectF(1302, 523, 1592, 616), 14, 14, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xff5f5149);
        c.drawRoundRect(new RectF(1305, 526, 1589, 613), 14, 14, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(19f);
        paint.setColor(0xffc9b9a4);
        // Vuil, in de behuizing verzonken metalen sleuf.
        paint.setColor(0xff0b0909);
        c.drawRoundRect(new RectF(1327, 556, 1572, 579), 9, 9, paint);
        paint.setColor(0xff3a2b23);
        c.drawRoundRect(new RectF(1331, 560, 1568, 574), 6, 6, paint);
        paint.setColor(0xffe58332);
        c.drawRoundRect(new RectF(1331, 561, 1331 + 235 * volumeFraction, 573), 6, 6, paint);

        for (int i = 0; i <= 10; i++) {
            float mark = 1331f + 23.5f * i;
            paint.setColor(i == 0 || i == 5 || i == 10 ? 0xffc9b7a3 : 0xff7e7167);
            paint.setStrokeWidth(i % 5 == 0 ? 2.5f : 1.2f);
            c.drawLine(mark, 585, mark, i % 5 == 0 ? 598 : 593, paint);
        }
        // Analoge, geribbelde metalen duimknop.
        float knob = 1331 + 235 * volumeFraction;
        paint.setShader(new LinearGradient(knob - 24, 548, knob + 25, 585,
                new int[]{0xfff1e4d0, 0xff8a827b, 0xffdad2c7, 0xff544c47},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(knob - 25, 543, knob + 25, 586), 7, 7, paint);
        paint.setShader(null);
        paint.setColor(0xff39312c);
        paint.setStrokeWidth(1.7f);
        for (int i = -18; i <= 18; i += 5)
            c.drawLine(knob + i, 548, knob + i, 581, paint);
        paint.setColor(0xfff0e4d0);
        c.drawLine(knob, 545, knob, 583, paint);
    }


    private void drawEject(Canvas c) {
        // De vijfde knop sluit aan op de originele transportknoppen;
        // hetzelfde metallic oppervlak, dezelfde basishoogte en schaduw.
        paint.reset(); paint.setAntiAlias(true);
        paint.setColor(0xff0c0b0b);
        c.drawRoundRect(new RectF(1370, 699, 1613, 856), 18, 18, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xff4b4440);
        c.drawRoundRect(new RectF(1373, 702, 1610, 854), 18, 18, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff0a090a);
        c.drawRoundRect(new RectF(1383, 710, 1601, 845), 16, 16, paint);
        paint.setShader(new LinearGradient(1392, 717, 1392, 829,
                new int[]{0xffded5c9, 0xffb6aea4, 0xff938b82, 0xffc2b6a7, 0xff716b63},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(1391, 717, 1593, 832), 13, 13, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xffe3d9cb);
        c.drawLine(1403, 721, 1580, 721, paint);
        paint.setColor(0xff524a42);
        c.drawLine(1403, 830, 1580, 830, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff282521);
        Path icon = new Path();
        icon.moveTo(1492, 743); icon.lineTo(1518, 777);
        icon.lineTo(1466, 777); icon.close();
        c.drawPath(icon, paint);
        paint.setStrokeWidth(5f);
        c.drawLine(1463, 786, 1521, 786, paint);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(23f);
        c.drawText("EJECT", 1457, 815, paint);
    }


    private void drawFavoriteCount(Canvas c) {
        // Onder Favorites alleen het label; geen teller op de cassettebehuizing.
        paint.reset(); paint.setAntiAlias(true);
        paint.setColor(0xff211c1c);
        c.drawRoundRect(new RectF(1386, 280, 1551, 315), 5, 5, paint);
    }

    private boolean inside(float x, float y, float left, float top, float right, float bottom) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float factor = scale();
        float x = (event.getX() - (getWidth() - W * factor) / 2f) / factor;
        float y = (event.getY() - (getHeight() - H * factor) / 2f) / factor;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (inside(x, y, 1280, 500, 1590, 625)) {
                volumeDragging = true;
                updateVolume(x);
                return true;
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && volumeDragging) {
            updateVolume(x);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            volumeDragging = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (volumeDragging) {
                updateVolume(x);
                volumeDragging = false;
                return true;
            }
            performClick();
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (inside(x, y, 1384, 704, 1606, 852)) {
                controller.onEject(); return true;
            }
            if (inside(x, y, 427, 690, 654, 860) || inside(x, y, 1284, 345, 1365, 460)) {
                controller.onPrevious(); return true;
            }
            if (inside(x, y, 1139, 690, 1389, 860) || inside(x, y, 1518, 345, 1600, 460)) {
                controller.onNext(); return true;
            }
            if (inside(x, y, 652, 690, 925, 860)) {
                controller.onPlayPause(); return true;
            }
            if (inside(x, y, 927, 690, 1140, 860)) {
                controller.onStopPlayback(); return true;
            }
            if (inside(x, y, 1270, 214, 1608, 343)) {
                controller.onBrowse(true); return true;
            }
            if (inside(x, y, 1360, 345, 1518, 460)) {
                controller.onBrowse(false); return true;
            }
            if (inside(x, y, 120, 70, 480, 205)) {
                controller.onSettings(); return true;
            }
            if (inside(x, y, 578, 68, 1090, 195) || inside(x, y, 450, 232, 1254, 622)) {
                controller.onBrowse(false); return true;
            }
            return true;
        }
        return true;
    }

    private void updateVolume(float x) {
        float fraction = Math.max(0f, Math.min(1f, (x - 1329f) / 238f));
        setVolume(fraction);
        controller.onVolume(fraction);
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}
