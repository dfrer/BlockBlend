#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec2 SceneSize;
uniform ivec2 LightUV;
uniform int BlendMode;
uniform float Opacity;
uniform float FogStart;
uniform float FogEnd;
in vec2 texCoord;
in vec4 vertexColor;
in float vertexDistance;
out vec4 fragColor;

float softCurve(float b) {
    return b <= 0.25 ? ((16.0*b-12.0)*b+4.0)*b : sqrt(b);
}
float blendChannel(float b, float s) {
    if (BlendMode == 0) return b*s;
    if (BlendMode == 1) return 1.0-(1.0-b)*(1.0-s);
    if (BlendMode == 2) return b <= 0.5 ? 2.0*b*s : 1.0-2.0*(1.0-b)*(1.0-s);
    if (BlendMode == 3) return s <= 0.5 ? b-(1.0-2.0*s)*b*(1.0-b) : b+(2.0*s-1.0)*(softCurve(b)-b);
    if (BlendMode == 4) return s <= 0.5 ? 2.0*b*s : 1.0-2.0*(1.0-b)*(1.0-s);
    if (BlendMode == 5) return b <= 0.0 ? 0.0 : s >= 1.0 ? 1.0 : min(1.0,b/(1.0-s));
    return b >= 1.0 ? 1.0 : s <= 0.0 ? 0.0 : 1.0-min(1.0,(1.0-b)/s);
}
void main() {
    vec4 scene = texture(Sampler1, gl_FragCoord.xy / SceneSize);
    vec3 surface = clamp(texture(Sampler0, texCoord).rgb * vertexColor.rgb * texelFetch(Sampler2, LightUV, 0).rgb, 0.0, 1.0);
    vec3 blended = vec3(blendChannel(scene.r,surface.r),blendChannel(scene.g,surface.g),blendChannel(scene.b,surface.b));
    // The backdrop is already fogged: fade only this surface's contribution.
    float coverage = Opacity * linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = vec4(mix(scene.rgb,clamp(blended,0.0,1.0),coverage),scene.a);
}
