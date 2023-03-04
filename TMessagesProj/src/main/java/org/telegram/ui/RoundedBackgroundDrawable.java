package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RoundedBackgroundDrawable extends Drawable {

    private final Paint paint;
    private final float radius;

    public RoundedBackgroundDrawable(int color, float radius, int alpha) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setAlpha(alpha);
        this.radius = radius;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        RectF rect = new RectF(getBounds());
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
