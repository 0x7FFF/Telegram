precision highp float;
varying vec2 v_TextCoord;

uniform sampler2D u_Texture;
uniform vec2 u_TexelOffset;
uniform float u_Radius;

void main() {
    vec4 centerPixel = texture2D(u_Texture, v_TextCoord);

    // If pixel is fully transparent, don't blur
    if (centerPixel.a < 0.01) {
        gl_FragColor = vec4(0.0);
        return;
    }

    vec4 color = centerPixel;
    float totalWeight = 1.0;
    float radius = min(u_Radius, 8.0); // Limit maximum radius

    for (float i = 1.0; i <= radius; i++) {
        float weight = (radius - i + 1.0) / radius;

        vec2 posOffset = v_TextCoord + i * u_TexelOffset;
        vec2 negOffset = v_TextCoord - i * u_TexelOffset;

        vec4 posColor = texture2D(u_Texture, posOffset);
        vec4 negColor = texture2D(u_Texture, negOffset);

        // Only blend if alpha difference is small
        float posAlphaDiff = abs(centerPixel.a - posColor.a);
        float negAlphaDiff = abs(centerPixel.a - negColor.a);

        // Sharper alpha threshold
        if (posAlphaDiff < 0.2) {
            color += posColor * weight;
            totalWeight += weight;
        }

        if (negAlphaDiff < 0.2) {
            color += negColor * weight;
            totalWeight += weight;
        }
    }

    gl_FragColor = color / totalWeight;
}