package org.telegram.ui;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.SurfaceHolder;

public class BackgroundGLView extends GLSurfaceView {
    private final MultiStateOpenGLShaderRenderer renderer;
    private int viewWidth;
    private int viewHeight;

    public BackgroundGLView(Context context, Renderer renderer) {
        super(context);
        this.renderer = (GradientCircleOverdrawRenderer) renderer;
        setEGLContextClientVersion(2);
        setRenderer(renderer);
    }


    public void setColors(Vector4[] colors) {
        this.renderer.setColorsIdle(
            colors[0].asArray(),
            colors[1].asArray(),
            colors[2].asArray(),
            colors[3].asArray()
        );
    }

    public void setOverdrawColors(Vector4[] colors) {
        this.renderer.setColorsTransition(
            colors[0].asArray(),
            colors[1].asArray(),
            colors[2].asArray(),
            colors[3].asArray()
        );
    }

    public void interpolateColors(Vector4[] oldColors, Vector4[] newColors, int durationMs) {
        OpenGLAnimationInterpolator.animateUniforms(this.renderer, oldColors, newColors, durationMs);
    }

    public void interpolateColors(Vector4[] newColors, int durationMs) {
        OpenGLAnimationInterpolator.animateUniforms(this.renderer, this.renderer.getColors(), newColors, durationMs);
    }

    public void startOverdraw(float[] startXY) {
        renderer.setOverdrawCoords(startXY);
        renderer.startOverdraw();
        postDelayed(renderer::stopOverdraw, 3000);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);
        viewHeight = h;
        viewWidth = w;
    }
}

