package org.telegram.ui;

import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public abstract class AbstractEllipsisDrawable extends Drawable {
    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(20);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(7);
    }
}
