# Milestone 2: The OpenGL globe

**Goal:** draw a 3D Earth with real imagery, centered on a chosen place, using OpenGL ES 3.0.

## What was built

- **A GL thread for the wallpaper.** The engine creates a `GLSurfaceView` that is never added
  to a window. Instead, it overrides `getHolder()` to draw into the wallpaper's own surface.
  This gives a ready-made GL thread, context handling and render-on-demand. It is released
  by calling `onDetachedFromWindow()` when the engine is destroyed.
- **One full-screen triangle and a ray-traced sphere.** There is no 3D mesh.
  - [`globe.vert`](../app/src/main/assets/shaders/globe.vert) makes a single triangle that
    covers the screen, from `gl_VertexID`, with no vertex buffers.
  - [`globe.frag`](../app/src/main/assets/shaders/globe.frag) does the rest. For each pixel it
    shoots a ray from a camera 4 Earth radii away, intersects it with a unit sphere, and turns
    the hit point into a latitude/longitude to read the texture.
  - The field of view is chosen so the globe spans 90% of the screen's shorter side.
- **[`GeoPoint`](../app/src/main/java/io/github/evertoncustodio/overhead/render/GeoPoint.kt)**
  builds the rotation that brings a place to the center with north up, `rotateY(lon) ·
  rotateX(-lat)`, as a column-major matrix for `glUniformMatrix3fv`.
- **[`GlUtils`](../app/src/main/java/io/github/evertoncustodio/overhead/render/GlUtils.kt)**
  compiles shaders from `assets/` and loads mipmapped textures that wrap east–west.
- **Render on demand.** The wallpaper only draws when something changes
  (`RENDERMODE_WHEN_DIRTY`), and pauses its GL thread while hidden.

## Data

- **Day image:** NASA **Blue Marble Next Generation**, 4096×2048. The old Visible Earth
  download links had moved, so the image came from NASA's **GIBS** WMS service
  (`BlueMarble_NextGeneration` layer). It was fetched as PNG and re-encoded as a quality-90
  JPEG (1.2 MB), because GIBS's own JPEG output is heavily compressed.

## Problems found

- **A seam at longitude 180°.** `atan()` jumps from +π to −π there, so the GPU sees a huge
  texture-coordinate change between neighboring pixels and picks the blurriest mipmap, which
  draws a visible line. It was first fixed with `textureGrad()` and a second, shifted
  coordinate. Milestone 7 replaced that with geometric mipmap levels, which is much faster.

## Result

A correctly oriented globe on the emulator: north up, Africa to the east of the Atlantic,
no seam, and a smooth edge.
