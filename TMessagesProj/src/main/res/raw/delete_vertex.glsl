#version 300 es

precision highp float;

layout(location = 0) in vec2 inPosition;
layout(location = 1) in vec2 inTexCoord;
layout(location = 2) in vec2 inVelocity;
layout(location = 3) in float inLifetime;
layout(location = 4) in float inSeed;
layout(location = 5) in float inX;

out vec2 outPosition;
out vec2 outTexCoord;
out vec2 outVelocity;
out float outLifetime;
out float outSeed;
out float outX;

out vec2 vTexCoord;
out float alpha;

uniform float deltaTime;
uniform vec2 maxSpeed;
uniform float acceleration;
uniform float easeInDuration;
uniform float minLifetime;
uniform float maxLifetime;
uniform float time;
uniform float pointSize;
uniform float visibleSize;

float rand(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.233))) * 4375.5453);
}

void main() {
    float frac = max(0.0, min(easeInDuration, time)) / easeInDuration;
    float r = min(1.0, frac / inX);
    float phase = pow(r,5.0);
    if (inLifetime < 0.0) {
        outTexCoord = vec2(inPosition.x / 2.0 + 0.5, -inPosition.y / 2.0 + 0.5);
        float direction = rand(vec2(inSeed * 2.31, inSeed + 14.145)) * (3.14159 * 2.0);
        float velocityValue = (0.1 + rand(vec2(inSeed / 61.2, inSeed - 1.22)) * (0.2 - 0.15));
        vec2 velocity = vec2(velocityValue * maxSpeed.x, velocityValue * maxSpeed.y);
        outVelocity = vec2(cos(direction) * velocity.x, sin(direction) * velocity.y);
        outLifetime = minLifetime + rand(vec2(inSeed - 1.3, inSeed * 157.511)) * (maxLifetime - minLifetime);
    } else {
        outTexCoord = inTexCoord;
        outVelocity = inVelocity + vec2(0.0, deltaTime * acceleration * phase);
        outLifetime = max(0.0, inLifetime - deltaTime * phase);
    }
    outPosition = inPosition + inVelocity * deltaTime * phase;
    outSeed = inSeed;
    outX = inX;

    vTexCoord = outTexCoord;
    alpha = max(0.0, min(0.2, outLifetime) / 0.2);
    float sizeDiff = pointSize - visibleSize;
    gl_PointSize = pointSize - (sizeDiff * phase);
    gl_Position = vec4(inPosition, 0.0, 1.0);
}