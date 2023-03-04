package org.telegram.ui;

public class VoIPColorScheme {
    public Vector4 vBlueVioletTL = new Vector4(0.125f, 0.643f, 0.843f);
    public Vector4 vBlueVioletTR = new Vector4(0.247f, 0.545f, 0.918f);
    public Vector4 vBlueVioletBL = new Vector4(0.506f, 0.282f, 0.925f);
    public Vector4 vBlueVioletBR = new Vector4(0.706f, 0.337f, 0.847f);
    public Vector4 vBlueGreenTL = new Vector4(0.031f, 0.69f, 0.639f);
    public Vector4 vBlueGreenTR = new Vector4(0.09f, 0.667f, 0.894f);
    public Vector4 vBlueGreenBL = new Vector4(0.231f, 0.478f, 0.945f);
    public Vector4 vBlueGreenBR = new Vector4(0.271f, 0.463f, 0.914f);
    public Vector4 vGreenTL = new Vector4(0.271f, 0.463f, 0.914f);
    public Vector4 vGreenTR = new Vector4(0.353f, 0.694f, 0.278f);
    public Vector4 vGreenBL = new Vector4(0.027f, 0.729f, 0.388f);
    public Vector4 vGreenBR = new Vector4(0.027f, 0.663f, 0.675f);
    public Vector4 vOrangeRedTL = new Vector4(0.859f, 0.565f, 0.298f);
    public Vector4 vOrangeRedTR = new Vector4(0.871f, 0.447f, 0.22f);
    public Vector4 vOrangeRedBL = new Vector4(0.906f, 0.38f, 0.561f);
    public Vector4 vOrangeRedBR = new Vector4(0.91f, 0.412f, 0.345f);

    public static VoIPColorScheme INSTANCE = null;

    public static VoIPColorScheme getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new VoIPColorScheme();
        }
        return INSTANCE;
    }

    public Vector4[] getBlueViolet() {
        return new Vector4[]{
            vBlueVioletTL, vBlueVioletTR, vBlueVioletBL, vBlueVioletBR
        };
    }

    public Vector4[] getBlueGreen() {
        return new Vector4[]{
            vBlueGreenTL, vBlueGreenTR, vBlueGreenBL, vBlueGreenBR
        };
    }

    public Vector4[] getGreen() {
        return new Vector4[]{
            vGreenTL, vGreenTR, vGreenBL, vGreenBR
        };
    }

    public Vector4[] getOrangeRed() {
        return new Vector4[]{
            vOrangeRedTL, vOrangeRedTR, vOrangeRedBL, vOrangeRedBR
        };
    }
}

