package org.telegram.ui;

public class Vector4 {
    private final float r;
    private final float g;
    private final float b;
    private final float a;


    public Vector4(float[] colors) {
        this(colors[0], colors[1], colors[2], colors[3]);
    }

    public Vector4(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public Vector4(float r, float g, float b) {
        this(r, g, b, 1.0f);
    }

    public float getR() {
        return r;
    }

    public float getG() {
        return g;
    }

    public float getB() {
        return b;
    }

    public float getA() {
        return a;
    }

    public float[] asArray() {
        return new float[]{r, g, b, a};
    }
}
