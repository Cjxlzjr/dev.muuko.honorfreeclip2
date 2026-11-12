package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Drawn vector symbols, not extracted Huawei assets or emoji/system-font glyphs. */
final class SmartAudioIcon extends View {
    static final int SOUND = 0, GESTURE = 1, HEAD = 2, VOICE = 3,
            WEAR = 4, CASE = 5, LAB = 6, DEVICE = 7, UPDATE = 8;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private final Drawable localDrawable;

    SmartAudioIcon(Activity a, int kind) {
        super(a);
        this.kind = kind;
        this.localDrawable = LocalIconBridge.icon(a, kind);
        switch (kind) {
            case SOUND: accent = Color.rgb(240, 88, 134); break;
            case GESTURE: accent = Color.rgb(19, 200, 159); break;
            case HEAD: accent = Color.rgb(24, 193, 162); break;
            case VOICE: accent = Color.rgb(41, 195, 165); break;
            case WEAR: accent = Color.rgb(36, 190, 155); break;
            case CASE: accent = Color.rgb(244, 107, 148); break;
            case LAB: accent = Color.rgb(88, 143, 247); break;
            case UPDATE: accent = Color.rgb(62, 139, 239); break;
            default: accent = Color.rgb(48, 125, 243); break;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (localDrawable != null) {
            localDrawable.setBounds(0, 0, getWidth(), getHeight());
            localDrawable.draw(canvas);
            return;
        }
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) * .48f;
        float cx = w / 2f, cy = h / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(accent);
        canvas.drawCircle(cx, cy, r, p);
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(r / 18f, r / 18f);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(2.25f);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStyle(Paint.Style.STROKE);
        switch (kind) {
            case SOUND:
                canvas.drawLine(-8, -7, -8, 6, p); canvas.drawLine(-8, -7, 4, -10, p);
                canvas.drawLine(4, -10, 4, 2, p);
                canvas.drawOval(new RectF(-12, 4, -5, 10), p);
                canvas.drawOval(new RectF(0, 0, 7, 6), p);
                break;
            case GESTURE:
                canvas.drawRoundRect(new RectF(-7, -3, 5, 10), 4, 4, p);
                canvas.drawLine(-4, -3, -4, -11, p);
                canvas.drawLine(0, -3, 0, -9, p);
                canvas.drawLine(4, -3, 4, -6, p);
                canvas.drawLine(-7, 1, -11, -2, p);
                break;
            case HEAD:
                canvas.drawCircle(0, -4, 6, p);
                canvas.drawArc(new RectF(-10, -2, 10, 14), 20, 140, false, p);
                canvas.drawArc(new RectF(-14, -13, 14, 11), 180, 65, false, p);
                break;
            case VOICE:
                canvas.drawRoundRect(new RectF(-3, -10, 3, 3), 3, 3, p);
                canvas.drawArc(new RectF(-8, -7, 8, 7), 0, 180, false, p);
                canvas.drawLine(0, 7, 0, 12, p);
                canvas.drawLine(-5, 12, 5, 12, p);
                break;
            case WEAR:
                canvas.drawArc(new RectF(-9, -10, 8, 10), 200, 255, false, p);
                canvas.drawArc(new RectF(-5, -4, 5, 7), 210, 240, false, p);
                canvas.drawCircle(1, 0, 1, p);
                break;
            case CASE:
                canvas.drawRoundRect(new RectF(-10, -7, 10, 9), 5, 5, p);
                canvas.drawLine(-9, 1, 9, 1, p); canvas.drawPoint(0, 4, p);
                break;
            case LAB:
                canvas.drawRoundRect(new RectF(-7, -10, 7, 10), 4, 4, p);
                canvas.drawLine(-4, -1, 4, -1, p);
                canvas.drawLine(-4, 4, 4, 4, p);
                break;
            case UPDATE:
                canvas.drawArc(new RectF(-9, -9, 9, 9), 50, 260, false, p);
                canvas.drawLine(-7, -6, -11, -4, p);
                canvas.drawLine(-7, -6, -7, -1, p);
                canvas.drawLine(0, -6, 0, 5, p);
                canvas.drawLine(-4, 1, 0, 5, p);
                canvas.drawLine(4, 1, 0, 5, p);
                break;
            default:
                canvas.drawRoundRect(new RectF(-9, -6, 9, 8), 4, 4, p);
                canvas.drawLine(-4, -1, 4, -1, p);
        }
        canvas.restore();
    }
}
