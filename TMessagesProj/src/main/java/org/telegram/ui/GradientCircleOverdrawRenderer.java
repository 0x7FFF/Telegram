package org.telegram.ui;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GradientCircleOverdrawRenderer implements MultiStateOpenGLShaderRenderer {

    private static int state = 0;
    //0 - Idle
    //1 - Transitioning
    private static final String VERTEX_SHADER =
        "attribute vec4 a_position;\n" +
            "void main() {\n" +
            "  gl_Position = a_position;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n" +
            "uniform vec2 u_resolution;\n" +
            "uniform vec4 u_color0;\n" +
            "uniform vec4 u_color1;\n" +
            "uniform vec4 u_color2;\n" +
            "uniform vec4 u_color3;\n" +
            "uniform vec4 u_color4;\n" +
            "uniform vec4 u_color5;\n" +
            "uniform vec4 u_color6;\n" +
            "uniform vec4 u_color7;\n" +
            "uniform vec2 u_overdraw;\n" +
            "uniform int iState; //uniform doesn't support boolean passing\n" +
            "uniform float fTime;\n\n" +
            "void mainImage(out vec4 fragColor, in vec2 fragCoord) {\n" +
            "   float currentAngle = fTime * 0.5;\n" +
            "   vec2 uv = fragCoord / u_resolution.xy;\n" +
            "   vec2 p = fragCoord / u_resolution.y;\n" +
            "   float normalizedMouseX = u_overdraw.x / u_resolution.y;\n" +
            "   float normalizedMouseY = (u_resolution.y - u_overdraw.y)/u_resolution.y;\n" +
            "   vec2 center = vec2(normalizedMouseX, normalizedMouseY);\n" +
            "   float radius = 0.0; //start radius\n" +
            "   float time = 0.0;\n" +
            "   if (iState == 1) {\n" +
            "       time = mod(fTime,2.0);\n" +
            "   }\n" +
            "   float position = distance(uv, center);\n" +
            "   if (iState == 1) {\n" +
            "       position = distance(p, center);\n" +
            "   }\n" +
            "   float dist = position-time*2.5;\n" +
            "   float alpha = smoothstep(radius, radius + 0.0001, dist);\n" +
            "   vec2 transformed = (uv - center) * mat2(cos(currentAngle), sin(currentAngle), -sin(currentAngle), cos(currentAngle)) + center;\n" +
            "   vec4 tltr = mix(u_color4, u_color6, smoothstep(0.0, 1.0, transformed.x));\n" +
            "   vec4 blbr = mix(u_color5, u_color7, smoothstep(0.0, 1.0, transformed.x));\n" +
            "   vec4 color1 = mix(tltr, blbr, smoothstep(0.0, 1.0, transformed.y));\n" +
            "   tltr = mix(u_color0, u_color1, smoothstep(0.0, 1.0, transformed.x));\n" +
            "   blbr = mix(u_color2, u_color3, smoothstep(0.0, 1.0, transformed.x));\n" +
            "   vec4 color2 = mix(tltr, blbr, smoothstep(0.0, 1.0, transformed.y));\n" +
            "   if (iState != 1) {\n" +
            "       fragColor = mix(tltr,blbr,transformed.y);\n" +
            "   } else {\n" +
            "       fragColor = mix(color1,color2,alpha);\n" +
            "   }\n" +
            "}\n\n" +
            "void main() {\n" +
            "  mainImage(gl_FragColor, gl_FragCoord.xy);\n" +
            "}\n";

    private int program;
    private int timeLocation;
    private int resolutionLocation;

    private int mouseLocation;
    private static final float[] VERTEX_COORDS = {
        -1.0f, -1.0f, // bottom left
        1.0f, -1.0f, // bottom right
        -1.0f, 1.0f, // top left
        1.0f, 1.0f, // top right
    };

    private final FloatBuffer vertexBuffer;

    private int stateLocation;

    private int uColor0Location;
    private int uColor1Location;
    private int uColor2Location;
    private int uColor3Location;
    private int uColor4Location;
    private int uColor5Location;
    private int uColor6Location;
    private int uColor7Location;

    //Idle
    private static float[] color0 = new float[4];
    private static float[] color1 = new float[4];
    private static float[] color2 = new float[4];
    private static float[] color3 = new float[4];

    //Transition
    private static float[] color4 = new float[4];
    private static float[] color5 = new float[4];
    private static float[] color6 = new float[4];
    private static float[] color7 = new float[4];

    private float[] mouseCoords = {0.5f, 0.5f}; //Center
    private static float counter = 0.0f;

    private int viewWidth;

    private int viewHeight;


    public GradientCircleOverdrawRenderer(
        float[] color0,
        float[] color1,
        float[] color2,
        float[] color3
    ) {
        this(
            color0,
            color1,
            color2,
            color3,
            color0,
            color1,
            color2,
            color3,
            ByteBuffer
                .allocateDirect(VERTEX_COORDS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        );
    }

    public GradientCircleOverdrawRenderer(
        float[] color0,
        float[] color1,
        float[] color2,
        float[] color3,
        float[] color4,
        float[] color5,
        float[] color6,
        float[] color7
    ) {
        this(
            color0,
            color1,
            color2,
            color3,
            color4,
            color5,
            color6,
            color7,
            ByteBuffer
                .allocateDirect(VERTEX_COORDS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        );
    }

    public GradientCircleOverdrawRenderer(
        float[] color0,
        float[] color1,
        float[] color2,
        float[] color3,
        float[] color4,
        float[] color5,
        float[] color6,
        float[] color7,
        FloatBuffer vertexBuffer
    ) {
        this.color0 = color0;
        this.color1 = color1;
        this.color2 = color2;
        this.color3 = color3;
        this.color4 = color4;
        this.color5 = color5;
        this.color6 = color6;
        this.color7 = color7;
        this.vertexBuffer = vertexBuffer;
        this.vertexBuffer.put(VERTEX_COORDS);
        this.vertexBuffer.position(0);
    }

    int err = Integer.MAX_VALUE;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Compile the vertex and fragment shaders
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        err = GLES20.glGetError();

        // Create the OpenGL program and link the shaders
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        err = GLES20.glGetError();

        // Retrieve the locations of the uniform variables
        uColor0Location = GLES20.glGetUniformLocation(program, "u_color0");
        uColor1Location = GLES20.glGetUniformLocation(program, "u_color1");
        uColor2Location = GLES20.glGetUniformLocation(program, "u_color2");
        uColor3Location = GLES20.glGetUniformLocation(program, "u_color3");
        uColor4Location = GLES20.glGetUniformLocation(program, "u_color4");
        uColor5Location = GLES20.glGetUniformLocation(program, "u_color5");
        uColor6Location = GLES20.glGetUniformLocation(program, "u_color6");
        uColor7Location = GLES20.glGetUniformLocation(program, "u_color7");
        timeLocation = GLES20.glGetUniformLocation(program, "fTime");
        resolutionLocation = GLES20.glGetUniformLocation(program, "u_resolution");
        mouseLocation = GLES20.glGetUniformLocation(program, "u_overdraw");
        stateLocation = GLES20.glGetUniformLocation(program, "iState");
        err = GLES20.glGetError();
        if (err != 0) {
            Log.e("onSurfaceCreated", "code " + err);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Set the clear color and clear the screen
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // Use the shader program
        GLES20.glUseProgram(program);

        // Set the uniform variables
        float fTime = counter % 360.0f;
        // First set of colors is always drawn
        GLES20.glUniform4f(uColor0Location, color0[0], color0[1], color0[2], color0[3]);
        GLES20.glUniform4f(uColor1Location, color1[0], color1[1], color1[2], color1[3]);
        GLES20.glUniform4f(uColor2Location, color2[0], color2[1], color2[2], color2[3]);
        GLES20.glUniform4f(uColor3Location, color3[0], color3[1], color3[2], color3[3]);
        GLES20.glUniform4f(uColor4Location, color4[0], color4[1], color4[2], color4[3]);
        GLES20.glUniform4f(uColor5Location, color5[0], color5[1], color5[2], color5[3]);
        GLES20.glUniform4f(uColor6Location, color6[0], color6[1], color6[2], color6[3]);
        GLES20.glUniform4f(uColor7Location, color7[0], color7[1], color7[2], color7[3]);
        GLES20.glUniform2f(mouseLocation, mouseCoords[0], mouseCoords[1]);
        GLES20.glUniform2f(resolutionLocation, viewWidth, viewHeight);
        GLES20.glUniform1f(timeLocation, fTime);
        GLES20.glUniform1i(stateLocation, state);

        // Enable the vertex buffer and set the position attribute
        int positionLocation = GLES20.glGetAttribLocation(program, "a_position");
        GLES20.glEnableVertexAttribArray(positionLocation);
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // Draw the shape using a triangle strip
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Disable the vertex buffer
        GLES20.glDisableVertexAttribArray(positionLocation);
        counter += 0.007f;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // Update the view size
        viewWidth = width;
        viewHeight = height;

        // Set the viewport
        GLES20.glViewport(0, 0, width, height);
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void setColorsIdle(float[] color0,
                              float[] color1,
                              float[] color2,
                              float[] color3) {
        this.color0 = color0;
        this.color1 = color1;
        this.color2 = color2;
        this.color3 = color3;
    }

    @Override
    public void setColorsIdleAndTransition(float[] color0,
                                           float[] color1,
                                           float[] color2,
                                           float[] color3,
                                           float[] color4,
                                           float[] color5,
                                           float[] color6,
                                           float[] color7) {
        this.color0 = color0;
        this.color1 = color1;
        this.color2 = color2;
        this.color3 = color3;
        this.color4 = color4;
        this.color5 = color5;
        this.color6 = color6;
        this.color7 = color7;
    }

    @Override
    public void setColorsTransition(float[] color4,
                                    float[] color5,
                                    float[] color6,
                                    float[] color7) {
        this.color4 = color4;
        this.color5 = color5;
        this.color6 = color6;
        this.color7 = color7;
    }

    @Override
    public void setColorsTransitionAndStartOverdraw(float[] color4,
                                                    float[] color5,
                                                    float[] color6,
                                                    float[] color7) {
        setColorsTransition(color4, color5, color6, color7);
        state = 1;
    }

    @Override
    public void setOverdrawCoords(float[] xy) {
        mouseCoords = xy;
    }

    @Override
    public void startOverdraw() {
        state = 1;
        counter = 0;
    }

    @Override
    public void stopOverdraw() {
        state = 0;
        color0 = color4;
        color1 = color5;
        color2 = color6;
        color3 = color7;
    }

    @Override
    public Vector4[] getColors() {
        return new Vector4[]{
            new Vector4(color0),
            new Vector4(color1),
            new Vector4(color2),
            new Vector4(color3)
        };
    }
}

