package org.telegram.ui;

import android.os.Handler;

public class OpenGLAnimationInterpolator {
    public static void animateUniforms(final OpenGLShaderRenderer renderer,
                                       final Vector4[] startColors,
                                       final Vector4[] endColors,
                                       final int durationMs) {
        final int numColors = startColors.length;

        final Handler handler = new Handler();
        final long startTime = System.currentTimeMillis();
        final Vector4[] currentColors = new Vector4[numColors];

        Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                float t = (float)(currentTime - startTime) / durationMs;
                if (t > 1.0f) {
                    t = 1.0f;
                }

                for (int i = 0; i < numColors; i++) {
                    currentColors[i] = lerp(startColors[i], endColors[i], t);
                }

                renderer.setColorsIdle(
                    currentColors[0].asArray(),
                    currentColors[1].asArray(),
                    currentColors[2].asArray(),
                    currentColors[3].asArray()
                );

                if (t < 1.0f) {
                    handler.postDelayed(this, 16); // Update every 16ms (60fps)
                }
            }
        };

        handler.post(updateRunnable);
    }

    private static Vector4 lerp(Vector4 a, Vector4 b, float t) {
        float red = a.getR() + (b.getR() - a.getR()) * t;
        float green = a.getG() + (b.getG() - a.getG()) * t;
        float blue = a.getB() + (b.getB() - a.getB()) * t;

        return new Vector4(red,green,blue);
    }

}

