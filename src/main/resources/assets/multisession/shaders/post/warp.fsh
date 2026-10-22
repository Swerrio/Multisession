#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform WarpConfig {
    vec2 OutSize;
    float Travel;
    float Warp;
    float Flash;
    float Distortion;
    float Arrival;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const float TAU = 6.2831853;
const int BLUR_SAMPLES = 12;

float hash(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 streaks(float radius, float angle, float cells, float seed, float speed, float pixel) {
    float cellPos = (angle / TAU + 0.5) * cells;
    float cell = floor(cellPos);
    float shape = hash(cell + seed * 17.0);
    float phase = hash(cell * 1.37 + seed * 31.0 + 5.0);
    float pace = hash(cell * 2.71 + seed * 53.0 + 11.0);
    float presence = hash(cell * 3.97 + seed * 71.0 + 19.0);
    if (presence > 0.2 + 0.7 * Warp) {
        return vec3(0.0);
    }

    float across = (fract(cellPos) - (0.2 + 0.6 * shape)) / cells * TAU * radius;
    float head = fract(phase + Travel * speed * (0.6 + 0.8 * pace));
    float headRadius = 0.04 + head * head * 1.3;
    float tail = headRadius * mix(0.06, 0.8, Warp) + pixel * 2.0;
    float behind = headRadius - radius;
    float body = behind >= 0.0 ? 1.0 - behind / tail : exp(-behind * behind / (pixel * pixel * 6.0));
    body = pow(clamp(body, 0.0, 1.0), 1.6);

    float width = pixel * (0.5 + 1.4 * pace) * (0.6 + radius * 1.4);
    float core = exp(-across * across / (width * width));
    float halo = exp(-across * across / (width * width * 16.0)) * 0.45;
    vec3 tint = mix(vec3(0.35, 0.72, 1.0), vec3(0.78, 0.5, 1.0), presence * 1.5);
    float fade = smoothstep(0.03, 0.22, radius) * (1.0 - smoothstep(0.85, 1.0, head));
    return (vec3(core) + tint * halo) * body * fade * (0.55 + 0.6 * shape);
}

void main() {
    float aspect = OutSize.x / OutSize.y;
    vec2 centered = texCoord - 0.5;
    vec2 p = centered * vec2(aspect, 1.0);
    float radius = length(p);
    float angle = atan(p.y, p.x);
    float pixel = 1.0 / OutSize.y;

    float distortion = Warp * Distortion;
    vec2 shake = (vec2(hash(Travel * 61.0), hash(Travel * 97.0 + 3.0)) - 0.5) * 0.008 * distortion * distortion;
    vec2 lens = centered * (1.0 - 0.12 * distortion * (0.25 + dot(centered, centered) * 2.5));
    float blur = 0.24 * distortion;
    float aberration = 0.03 * distortion;
    float jitter = hash(gl_FragCoord.xy);

    vec3 scene = vec3(0.0);
    for (int i = 0; i < BLUR_SAMPLES; i++) {
        float scale = 1.0 - blur * (float(i) + jitter) / float(BLUR_SAMPLES);
        vec2 uv = 0.5 + shake + lens * scale;
        scene.r += texture(InSampler, 0.5 + shake + lens * scale * (1.0 + aberration)).r;
        scene.g += texture(InSampler, uv).g;
        scene.b += texture(InSampler, 0.5 + shake + lens * scale * (1.0 - aberration)).b;
    }
    scene /= float(BLUR_SAMPLES);

    float edge = smoothstep(0.2, 1.0, radius);
    scene *= (1.0 - 0.35 * Warp) * (1.0 - 0.6 * Warp * edge);
    scene = mix(scene, scene * vec3(0.55, 0.72, 1.2), 0.65 * Warp * edge);

    vec3 light = streaks(radius, angle, 96.0, 1.0, 1.0, pixel)
            + streaks(radius, angle, 167.0, 2.0, 0.8, pixel) * 0.75
            + streaks(radius, angle, 263.0, 3.0, 1.3, pixel) * 0.5;
    light *= smoothstep(0.0, 0.3, Warp) * 1.6;

    float glowSize = 0.004 + 0.07 * Warp * Warp;
    vec3 core = vec3(0.75, 0.88, 1.0) * exp(-radius * radius / glowSize) * Warp * Warp * 1.4;

    vec3 color = scene + light + core;

    float reach = mix(radius, 1.0 - radius, Arrival);
    float flash = clamp(Flash * 1.75 - reach * 0.7, 0.0, 1.0);
    flash = flash * flash * (3.0 - 2.0 * flash);
    color = mix(color, vec3(0.93, 0.97, 1.0), flash);

    fragColor = vec4(color, 1.0);
}
