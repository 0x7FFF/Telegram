package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class AvatarWavesDrawable extends AbstractAvatarWavesDrawable {

    float amplitude;
    float animateToAmplitude;
    float animateAmplitudeDiff;
    float wavesEnter = 0f;
    boolean showWaves;

    private BlobDrawable blobDrawable;
    private BlobDrawable blobDrawable2;

    private boolean hasCustomColor;
    private int isMuted;
    private float progressToMuted = 0;

    boolean isCallEstablished = false;

    public AvatarWavesDrawable(int minRadiusInner, int maxRadiusInner, int minRadiusOuter, int maxRadiusOuter) {
        blobDrawable = new BlobDrawable(9);
        blobDrawable2 = new BlobDrawable(11);
        blobDrawable.minRadius = minRadiusInner;
        blobDrawable.maxRadius = maxRadiusInner;
        blobDrawable2.minRadius = minRadiusOuter;
        blobDrawable2.maxRadius = maxRadiusOuter;
        blobDrawable.generateBlob();
        blobDrawable2.generateBlob();
        blobDrawable.paint.setColor(0x24FFFFFF);
        blobDrawable2.paint.setColor(0x14FFFFFF);
    }

    public void update() {
        if (animateToAmplitude != amplitude) {
            amplitude += animateAmplitudeDiff * 16;
            if (animateAmplitudeDiff > 0) {
                if (amplitude > animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            } else {
                if (amplitude < animateToAmplitude) {
                    amplitude = animateToAmplitude;
                }
            }
        }

        if (showWaves && wavesEnter != 1f) {
            wavesEnter += 16 / 350f;
            if (wavesEnter > 1f) {
                wavesEnter = 1f;
            }
        } else if (!showWaves && wavesEnter != 0) {
            wavesEnter -= 16 / 350f;
            if (wavesEnter < 0f) {
                wavesEnter = 0f;
            }
        }
    }

    public void draw(Canvas canvas, float cx, float cy) {
        float scaleBlob = 0.8f + 0.4f * amplitude;
        if (showWaves || wavesEnter != 0) {
            canvas.save();
            float wavesEnter = CubicBezierInterpolator.DEFAULT.getInterpolation(this.wavesEnter);

            canvas.scale(scaleBlob * wavesEnter, scaleBlob * wavesEnter, cx, cy);

            if (!hasCustomColor) {
                if (isMuted != 1 && progressToMuted != 1f) {
                    progressToMuted += 16 / 150f;
                    if (progressToMuted > 1f) {
                        progressToMuted = 1f;
                    }
                } else if (isMuted == 1 && progressToMuted != 0f) {
                    progressToMuted -= 16 / 150f;
                    if (progressToMuted < 0f) {
                        progressToMuted = 0f;
                    }
                }

            }
            blobDrawable.update(amplitude, 1f);
            blobDrawable.draw(cx, cy, canvas, blobDrawable.paint);

            blobDrawable2.update(amplitude, 1f);
            blobDrawable2.draw(cx, cy, canvas, blobDrawable2.paint);

            canvas.restore();
        }

        if (wavesEnter != 0) {
            invalidateSelf();
        }
    }

    public float getAvatarScale() {
        float scaleAvatar = 0.9f + 0.2f * amplitude;
        float wavesEnter = CubicBezierInterpolator.EASE_OUT.getInterpolation(this.wavesEnter);
        return scaleAvatar * wavesEnter + 1f * (1f - wavesEnter);
    }

    public void setMuted(int status, boolean animated) {
        this.isMuted = status;
        if (!animated) {
            progressToMuted = isMuted != 1 ? 1f : 0f;
        }
    }

    public void setShowWaves(boolean show) {
        if (showWaves != show) {
            invalidateSelf();
        }
        showWaves = show;
    }

    public void setAmplitude(double value) {
        float amplitude = (float) value / 80f;
        if (!showWaves) {
            amplitude = 0;
        }
        if (amplitude > 1f) {
            amplitude = 1f;
        } else if (amplitude < 0) {
            amplitude = 0;
        }
        animateToAmplitude = amplitude;
        animateAmplitudeDiff = (animateToAmplitude - this.amplitude) / 200;
    }

    public void setColor(int color) {
        hasCustomColor = true;
        blobDrawable.paint.setColor(color);
    }

    public float getRandomFloat() {
        float min = 0.1f;
        float max = 80f;
        return (float) (Math.random() * (max - min) + min);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!isCallEstablished) {
            setAmplitude(getRandomFloat());
        }
        update();
        Rect bounds = getBounds();
        draw(canvas, bounds.centerX(), bounds.centerY());
    }

}
