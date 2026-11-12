package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Lightweight three-mode illustrations matching SmartAudio's spatial selector semantics. */
final class SpatialModeGlyph extends View {
    private final int mode;
    private int tint = SmartAudioStyle.SUBTLE;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable localNormal;
    private final Drawable localSelected;

    SpatialModeGlyph(Activity a, int mode) {
        super(a);
        this.mode = mode;
        String name = mode == 0 ? "off" : mode == 2 ? "fixed" : "follow";
        localNormal = LocalIconBridge.named(a,"icon_spatial_audio_" + name + "_normal");
        localSelected = LocalIconBridge.named(a,"icon_spatial_audio_" + name + "_selected");
    }

    void setTint(int color) { tint = color; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        Drawable drawable = tint == SmartAudioStyle.BLUE ? localSelected : localNormal;
        if (drawable != null) {
            int side = Math.round(getResources().getDisplayMetrics().density * 30f);
            side = Math.min(side,Math.min(getWidth(),getHeight()));
            int left=(getWidth()-side)/2, top=(getHeight()-side)/2;
            drawable.setBounds(left,top,left+side,top+side);
            drawable.draw(c);
            return;
        }
        float s = Math.min(getWidth(), getHeight()) / 40f;
        c.save();
        c.translate(getWidth() / 2f, getHeight() / 2f);
        c.scale(s, s);
        paint.setColor(tint);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        c.drawOval(new RectF(-4, -8, 4, 0), paint);
        c.drawArc(new RectF(-9, -1, 9, 14), 185, 170, false, paint);
        if (mode == 0) {
            c.drawArc(new RectF(-13, -14, 13, 14), 200, 44, false, paint);
            c.drawLine(-10, -12, 11, 12, paint);
        } else if (mode == 2) {
            c.drawArc(new RectF(-16, -12, 12, 13), 130, 80, false, paint);
            c.drawArc(new RectF(-12, -12, 16, 13), 315, 80, false, paint);
            c.drawArc(new RectF(-20, -17, 20, 17), 140, 58, false, paint);
            c.drawArc(new RectF(-20, -17, 20, 17), 340, 58, false, paint);
        } else {
            c.drawArc(new RectF(-14, -16, 14, 12), 210, 230, false, paint);
            c.drawLine(12, 0, 13, -6, paint);
            c.drawLine(12, 0, 17, -3, paint);
        }
        c.restore();
    }
}
