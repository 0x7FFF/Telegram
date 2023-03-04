package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class EllipsisDrawable extends AbstractEllipsisDrawable {
    private final CubicBezierInterpolator interpolator = new CubicBezierInterpolator(0.33, 0.00, 0.67, 1.00);
    private Paint paint;

    public EllipsisDrawable() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();
        Rect bounds = getBounds();
        canvas.translate(bounds.left, bounds.top);
        long time = SystemClock.uptimeMillis() % 250 + 500;
        for (int i = 0; i < 3; i++) {
            long pointTime = (time + i * 250L) % 750;
            float moveFraction = Math.min(1, pointTime / 667f);
            float scale;
            if (moveFraction <= 0.425f) {
                scale = interpolator.getInterpolation(moveFraction / 0.425f);
            } else {
                scale = 1f - interpolator.getInterpolation((moveFraction - 0.425f) / 0.575f);
            }
            moveFraction = interpolator.getInterpolation(moveFraction);
            canvas.drawCircle(AndroidUtilities.dpf2(1.667f + moveFraction * 16f), AndroidUtilities.dp(3), AndroidUtilities.dpf2(2 * scale), paint);
        }
        canvas.restore();
        invalidateSelf();
    }
}
