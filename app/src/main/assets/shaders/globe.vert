#version 300 es

// Draws one triangle that covers the whole screen. No vertex buffers needed:
// the three corners are generated from gl_VertexID (0, 1, 2).
void main() {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
