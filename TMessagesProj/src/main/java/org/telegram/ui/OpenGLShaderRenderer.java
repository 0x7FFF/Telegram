package org.telegram.ui;

import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public interface OpenGLShaderRenderer extends GLSurfaceView.Renderer {
    @Override
    void onSurfaceCreated(GL10 gl, EGLConfig config);

    @Override
    void onDrawFrame(GL10 gl);

    @Override
    void onSurfaceChanged(GL10 gl, int width, int height);

    void setColorsIdle(float[] color0,
                       float[] color1,
                       float[] color2,
                       float[] color3);


    Vector4[] getColors();
}

interface MultiStateOpenGLShaderRenderer extends OpenGLShaderRenderer {
    void setColorsIdleAndTransition(float[] color0,
                                    float[] color1,
                                    float[] color2,
                                    float[] color3,
                                    float[] color4,
                                    float[] color5,
                                    float[] color6,
                                    float[] color7);

    void setColorsTransition(float[] color4,
                             float[] color5,
                             float[] color6,
                             float[] color7);

    void setColorsTransitionAndStartOverdraw(float[] color4,
                                             float[] color5,
                                             float[] color6,
                                             float[] color7);

    void startOverdraw();
    void stopOverdraw();

    void setOverdrawCoords(float[] xy);
}

