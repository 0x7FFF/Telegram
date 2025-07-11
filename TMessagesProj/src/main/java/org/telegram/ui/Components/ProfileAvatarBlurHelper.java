package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.util.Arrays;

public class ProfileAvatarBlurHelper {
    private Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ProfileBlur profileBlur;
    private float progress = 0f;
    private boolean enabled;

    private View mView;
    private Paint colorPaint = new Paint();

    public ProfileAvatarBlurHelper(View v) {
        mView = v;

        ColorMatrix colorMatrix = new ColorMatrix();
        colorPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            profileBlur = new GPUProfileBlur(SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 1f : 2f);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) {
            profileBlur = new CPUProfileBlur(false);
        } else {
            profileBlur = new CPUProfileBlur(true);
        }
        profileBlur.setBlurRadius(40);
    }

    public void setBlurEnabled(boolean enabled) {
        this.enabled = enabled;
        mView.invalidate();
    }

    public void setProgress(float progress) {
        this.progress = progress;
        onSizeChanged(mView.getWidth(), mView.getHeight());
        mView.invalidate();
    }

    public void draw(DrawHelper drawHelper, @NonNull Canvas canvas) {
        if (!enabled) {
            drawHelper.draw(canvas);
        } else {
            profileBlur.draw(drawHelper, canvas);
        }
    }

    public void invalidate() {
        profileBlur.invalidateBlur();
    }

    public void onDetached() {
        profileBlur.release();
    }

    private int getBottomExtra() {
        return (int) (AndroidUtilities.dp(32 + 64 + 12 * 2) / getScaleY());
    }

    protected float getBottomOffset() {
        return mView.getHeight() - getBottomExtra() * progress;
    }

    protected float getScaleY() {
        return 1f;
    }

    public void onSizeChanged(int w, int h) {
        profileBlur.onSizeChanged(w, h);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadePaint.setShader(new LinearGradient(w / 2f, AndroidUtilities.dp(32) / getScaleY(), w / 2f, 0, new int[] {0x00FFFFFF, 0xFFFFFFFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        mView.invalidate();
    }

    private final class CPUProfileBlur implements ProfileBlur {
        private Bitmap[] bitmaps = new Bitmap[1];
        private Canvas[] bitmapCanvas = new Canvas[bitmaps.length];
        private Paint bitmapPaint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
        private float scaleFactor;
        private float blurRadius;
        private boolean oneShot;
        private boolean drawn;
        private Rect visibleRect = new Rect();

        CPUProfileBlur(boolean oneShot) {
            this.oneShot = oneShot;
        }

        @Override
        public void setBlurRadius(float radius) {
            blurRadius = radius;
        }

        @Override
        public void onSizeChanged(int w, int h) {
            float scaleY = Math.max(0.2f, getScaleY());
            scaleFactor = 20f / scaleY;
            drawn = false;

            int dw = (int) Math.ceil(w / scaleFactor), dh = (int) Math.ceil(getBottomExtra() / scaleFactor);
            if (bitmaps[0] != null && bitmaps[0].getWidth() >= dw && bitmaps[0].getHeight() >= dh) {
                return;
            }
            if (w == 0 || h == 0) return;
            for (Bitmap bm : bitmaps) {
                if (bm != null) {
                    bm.recycle();
                }
            }

            bitmaps[0] = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
            bitmapCanvas[0] = new Canvas(bitmaps[0]);
        }

        @Override
        public void draw(DrawHelper drawHelper, Canvas canvas) {
            if (bitmaps[0] == null) {
                onSizeChanged(mView.getWidth(), mView.getHeight());
            }
            if (!oneShot || !drawn) {
                for (Bitmap bitmap : bitmaps) {
                    bitmap.eraseColor(0);
                }
            }
            float y = getBottomOffset();
            if (!oneShot || !drawn) {
                bitmapCanvas[0].save();
                bitmapCanvas[0].scale(1f / scaleFactor, 1f / scaleFactor, 0, 0);
                bitmapCanvas[0].translate(0, getBottomExtra() * progress - y);
                drawHelper.draw(bitmapCanvas[0]);
                bitmapCanvas[0].restore();
                Utilities.stackBlurBitmap(bitmaps[0], (int) (blurRadius / 8));

                DisplayMetrics metrics = mView.getResources().getDisplayMetrics();
                mView.getLocalVisibleRect(visibleRect);
                if (visibleRect.right > 0 && visibleRect.left < metrics.widthPixels) {
                    drawn = true;
                }
            }
            AndroidUtilities.rectTmp.set(0, y, mView.getWidth(), mView.getHeight());
            canvas.saveLayer(AndroidUtilities.rectTmp, colorPaint, Canvas.ALL_SAVE_FLAG);
            canvas.translate(0, y);
            canvas.scale(scaleFactor, scaleFactor, 0, 0);
            canvas.drawBitmap(bitmaps[0], 0, 0, bitmapPaint);
            canvas.restore();
            AndroidUtilities.rectTmp.set(0, 0, mView.getWidth(), AndroidUtilities.lerp(y + AndroidUtilities.dp(32) / getScaleY() - 1, mView.getHeight(), 1f - progress));
            canvas.saveLayer(AndroidUtilities.rectTmp, null, Canvas.ALL_SAVE_FLAG);
            drawHelper.draw(canvas);
            float off = AndroidUtilities.dp(32) / getScaleY() * (1f - progress);
            canvas.translate(0, y + off);
            canvas.drawRect(0, 0, mView.getWidth(), AndroidUtilities.dp(32) / getScaleY() + 1, fadePaint);
            canvas.restore();
        }

        @Override
        public void invalidateBlur() {
            drawn = false;
        }

        @Override
        public void release() {
            for (Bitmap bm : bitmaps) {
                if (bm != null) {
                    bm.recycle();
                }
            }
            Arrays.fill(bitmaps, null);
            Arrays.fill(bitmapCanvas, null);
            drawn = false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private final class GPUProfileBlur implements ProfileBlur {
        private RenderNode node = new RenderNode("render");
        private RenderNode blur = new RenderNode("blur");
        private float factorMult;
        private float scaleFactor;

        private GPUProfileBlur(float factorMult) {
            this.factorMult = factorMult;
        }

        @Override
        public void setBlurRadius(float radius) {
            radius /= factorMult;
            blur.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }

        @Override
        public void onSizeChanged(int w, int h) {
            scaleFactor = 2f * factorMult / getScaleY();
            node.setPosition(0, 0, w, h);
            blur.setPosition(0, 0, (int) Math.ceil(w / scaleFactor), (int) Math.ceil(getBottomExtra() / scaleFactor));
        }

        @Override
        public void draw(DrawHelper drawHelper, Canvas canvas) {
            drawHelper.draw(node.beginRecording());
            node.endRecording();
            float y = getBottomOffset();
            Canvas c = blur.beginRecording();
            c.scale(1f / scaleFactor, 1f / scaleFactor, 0, 0);
            c.translate(0, getBottomExtra() * progress - y);
            c.drawRenderNode(node);
            blur.endRecording();
            canvas.saveLayer(0, 0, mView.getWidth(), mView.getHeight(), colorPaint);
            canvas.translate(0, y);
            canvas.scale(scaleFactor, scaleFactor, 0, 0);
            canvas.drawRenderNode(blur);
            canvas.restore();
            canvas.saveLayer(0, 0, mView.getWidth(), AndroidUtilities.lerp(y + AndroidUtilities.dp(32) / getScaleY() - 1, mView.getHeight(), 1f - progress), null);
            canvas.drawRenderNode(node);
            float off = AndroidUtilities.dp(32) / getScaleY() * (1f - progress);
            canvas.translate(0, y + off);
            canvas.drawRect(0, 0, mView.getWidth(), AndroidUtilities.dp(32) / getScaleY() + 1, fadePaint);
            canvas.restore();
        }
    }

    private interface ProfileBlur {
        void setBlurRadius(float radius);
        void onSizeChanged(int w, int h);
        void draw(DrawHelper drawHelper, Canvas canvas);
        default void invalidateBlur() {}
        default void release() {}
    }
}
