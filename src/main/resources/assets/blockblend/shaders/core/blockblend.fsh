#version 150

uniform sampler2D DiffuseSampler;    // block atlas
uniform int uMode;
uniform float uStrength;

in vec2 vBaseUV;
in vec2 vOverUV;
in vec4 vColor;
in vec2 vLight;

out vec4 fragColor;

vec3 toLinear(vec3 c){ return pow(c, vec3(2.2)); }
vec3 toSRGB(vec3 c){ return pow(c, vec3(1.0/2.2)); }

vec3 multiply(vec3 a, vec3 b){ return a*b; }
vec3 screen(vec3 a, vec3 b){ return 1.0 - (1.0 - a)*(1.0 - b); }
vec3 overlayF(vec3 a, vec3 b){
    vec3 lt = 2.0*a*b;
    vec3 gt = 1.0 - 2.0*(1.0 - a)*(1.0 - b);
    return mix(lt, gt, step(0.5, a));
}
vec3 softLight(vec3 a, vec3 b){
    vec3 result = vec3(0.0);
    for(int i = 0; i < 3; i++){
        if(b[i] <= 0.5){
            result[i] = a[i] - (1.0 - 2.0*b[i])*a[i]*(1.0 - a[i]);
        }else{
            float d = (a[i] <= 0.25) ? ((16.0*a[i] - 12.0)*a[i] + 4.0)*a[i] : sqrt(a[i]);
            result[i] = a[i] + (2.0*b[i] - 1.0)*(d - a[i]);
        }
    }
    return clamp(result, 0.0, 1.0);
}
vec3 hardLight(vec3 a, vec3 b){
    return mix(2.0*a*b, 1.0 - 2.0*(1.0 - a)*(1.0 - b), step(0.5, b));
}
vec3 colorDodge(vec3 a, vec3 b){
    vec3 result = vec3(0.0);
    for(int i = 0; i < 3; i++){
        result[i] = (b[i] == 1.0) ? 1.0 : min(a[i]/(1.0 - b[i]), 1.0);
    }
    return result;
}
vec3 colorBurn(vec3 a, vec3 b){
    vec3 result = vec3(0.0);
    for(int i = 0; i < 3; i++){
        result[i] = (b[i] == 0.0) ? 0.0 : 1.0 - min((1.0 - a[i])/b[i], 1.0);
    }
    return result;
}

void main(){
    vec4 base = texture(DiffuseSampler, vBaseUV) * vColor;
    vec4 over = texture(DiffuseSampler, vOverUV);

    vec3 A = toLinear(base.rgb);
    vec3 B = toLinear(over.rgb);

    vec3 blended = A;
    if(uMode == 0) blended = multiply(A, B);
    else if(uMode == 1) blended = screen(A, B);
    else if(uMode == 2) blended = overlayF(A, B);
    else if(uMode == 3) blended = softLight(A, B);
    else if(uMode == 4) blended = hardLight(A, B);
    else if(uMode == 5) blended = colorDodge(A, B);
    else if(uMode == 6) blended = colorBurn(A, B);
    else blended = A; // Default: no blending

    vec3 outRGB = mix(A, blended, clamp(uStrength, 0.0, 1.0));
    fragColor = vec4(toSRGB(outRGB), base.a);
}


