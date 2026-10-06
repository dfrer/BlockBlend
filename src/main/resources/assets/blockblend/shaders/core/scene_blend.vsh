#version 150
#moj_import <fog.glsl>
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
out vec2 texCoord;
out vec4 vertexColor;
out float vertexDistance;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    vertexDistance = fog_distance(Position, FogShape);
}
