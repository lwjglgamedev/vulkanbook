#version 450

void main()
{
    gl_FragDepth = gl_FragCoord.z;
}
