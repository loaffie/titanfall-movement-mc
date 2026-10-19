#version 150

// Titanfall Movement: the Phase Shift view. Out of phase, the world is drained to a dark, cold black and
// white - shadows deepened, the edges sunk into a blue-black vignette with a faint electric rim - with a
// little grain crawling over it. Warp ripples and splits the image as the pilot tears out and back in.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float Time;
uniform float Intensity;
uniform float Warp;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 centred = texCoord - 0.5;
    float aspect = InSize.x / InSize.y;
    float dist = length(centred * vec2(aspect, 1.0));

    // The tear: a radial ripple and a split of the channels, strongest at the edges.
    vec2 dir = dist > 0.0001 ? centred / dist : vec2(0.0);
    float ripple = sin(dist * 38.0 - Time * 60.0) * 0.012 * Warp;
    vec2 uv = texCoord + dir * ripple;
    vec2 split = dir * (0.004 + 0.012 * dist) * Warp;
    vec3 source = vec3(texture(DiffuseSampler, uv + split).r, texture(DiffuseSampler, uv).g, texture(DiffuseSampler, uv - split).b);

    // Drained to a cold, shadowy black and white: deeper shadows, lifted contrast, a faint blue cast.
    float luma = dot(source, vec3(0.299, 0.587, 0.114));
    float shade = pow(luma, 1.35) * 0.95;
    shade = clamp((shade - 0.5) * 1.12 + 0.5, 0.0, 1.0);
    vec3 phase = vec3(shade) * vec3(0.9, 0.96, 1.06);

    // Vignette into blue-black, with a dim electric rim that flickers.
    float vignette = smoothstep(0.38, 1.0, dist);
    phase *= 1.0 - vignette * 0.8;
    float flicker = 0.75 + 0.25 * sin(Time * 6.2831 * 3.0) * sin(Time * 6.2831 * 7.0 + 1.3);
    phase += vec3(0.1, 0.35, 0.9) * smoothstep(0.62, 1.05, dist) * 0.22 * flicker;

    // Grain.
    phase += (hash(floor(texCoord * InSize * 0.5) + Time * 97.0) - 0.5) * 0.045;

    // The flash of the tear itself.
    phase += vec3(0.55, 0.75, 1.0) * Warp * 0.35 * (1.0 - smoothstep(0.0, 0.9, dist));

    fragColor = vec4(mix(source, phase, Intensity), 1.0);
}
