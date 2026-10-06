#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;   // base
in vec2 UV1;   // overlay
in vec2 Light;

out vec4 vColor;
out vec2 vBaseUV;
out vec2 vOverUV;
out vec2 vLight;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main(){
  vColor = Color;
  vBaseUV = UV0;
  vOverUV = UV1;
  vLight = Light;
  gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}


