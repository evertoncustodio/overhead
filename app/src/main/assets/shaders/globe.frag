#version 300 es
precision highp float;

// Earth is a unit sphere at the origin; the camera sits on the +Z axis looking at it.
// For every pixel we shoot a ray from the camera, find where it hits the sphere,
// and look up that spot in the equirectangular Earth textures.
// Lighting happens in Earth-fixed coordinates, where the sun direction is known.

uniform vec2 uResolution;          // surface size in pixels
uniform float uCameraDistance;     // camera distance from Earth's center, in Earth radii
uniform float uGlobeScreenRadius;  // globe radius as a fraction of half the screen's shorter side
uniform mat3 uViewToEarth;         // rotates view space into Earth-fixed coordinates
uniform vec3 uSunDirection;        // unit vector toward the sun, Earth-fixed coordinates
uniform sampler2D uDayTexture;
uniform sampler2D uNightTexture;
uniform sampler2D uOceanMask;      // 1 over water, 0 over land
uniform sampler2D uClouds;         // greyscale cloud map: black is clear sky, white thick cloud
uniform float uHasClouds;          // 0 until the first cloud map has been downloaded
uniform vec4 uMarker;              // the centered place's dot: screen x, y, radius, strength (0 = hidden)

out vec4 fragColor;

const float PI = 3.14159265359;
const vec3 SPACE_COLOR = vec3(0.02, 0.03, 0.08);

// Atmosphere: drawn as a shell slightly bigger than the planet. The real one is much
// thinner (about 1% of Earth's radius) but would be invisible at this distance.
const float ATMOSPHERE_RADIUS = 1.035;
const vec3 ATMOSPHERE_COLOR = vec3(0.35, 0.6, 1.0);
const vec3 TWILIGHT_COLOR = vec3(1.0, 0.45, 0.2);

const vec3 CITY_LIGHT_TINT = vec3(1.0, 0.85, 0.65);
const vec3 MOONLIGHT = vec3(0.02, 0.03, 0.06);
const vec3 SUN_GLINT_COLOR = vec3(1.0, 0.93, 0.8);

const vec3 MARKER_COLOR = vec3(0.45, 0.9, 1.0);

const vec3 SUN_COLOR = vec3(1.0, 0.95, 0.85);
const float SUN_ANGULAR_RADIUS = 0.0047;  // radians: the real sun is about half a degree across

// Clouds float about 10 km up; exaggerated here so their shadows are visible from space.
const float CLOUD_SHADOW_OFFSET = 0.004;

// A pseudo-random number in 0..1 that is always the same for the same input. Uses only
// multiplications ("hash without sine" by Dave Hoskins), which is cheaper than sin() per pixel.
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// A sparse field of single-pixel stars, most of them faint, with slightly varied colors.
vec3 stars(vec2 pixel) {
    float r = hash(floor(pixel));
    float brightness = smoothstep(0.9992, 1.0, r);
    brightness = brightness * brightness * 0.8;
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.9, 0.75), hash(floor(pixel) + 17.0));
    return tint * brightness;
}

// How much light reaches the ground, from the angle between the surface and the sun.
// Fades smoothly through twilight instead of switching off at the terminator.
float daylight(float sunCos) {
    return smoothstep(-0.12, 0.25, sunCos);
}

// How much cloud covers a point, 0..1. Thin haze is eased in so the ground shows through it.
// Weather satellites don't see the poles (the map fills them in by mirroring), so clouds
// fade out between 60 and 75 degrees of latitude; the ice caps below are white anyway.
float cloudCover(vec2 uv, float lod) {
    float value = textureLod(uClouds, uv, lod).r;
    float latitude = abs(0.5 - uv.y) * 180.0;
    float trusted = 1.0 - smoothstep(60.0, 75.0, latitude);
    return uHasClouds * trusted * pow(smoothstep(0.1, 0.9, value), 1.4);
}

// The sun seen from space: a small bright disk with a glow around it, in its real direction.
// sunView is the direction to the sun in view space (the camera looks down -Z).
vec3 sunDisk(vec3 rayDir, vec3 sunView) {
    // For small angles the distance between the two unit vectors is the angle itself,
    // and more precise than acos() of their dot product.
    float angle = length(rayDir - sunView);
    float disk = 1.0 - smoothstep(SUN_ANGULAR_RADIUS * 0.8, SUN_ANGULAR_RADIUS * 1.2, angle);
    float halo = exp(-angle * 40.0) * 0.8 + exp(-angle * 8.0) * 0.25;
    return SUN_COLOR * (disk * 4.0 + halo) * step(0.0, dot(rayDir, sunView));
}

// The sun is usually off-screen. Hint at where it is with a soft glow centered just past the
// screen edge on its side, strongest when it is off to the side (sunrise or sunset below).
// halfSize is half the screen in the same units as `screen` (half the shorter side is 1).
vec3 sunSpill(vec2 screen, vec2 halfSize, vec3 sunView) {
    float sideways = length(sunView.xy);
    if (sideways < 1e-3) return vec3(0.0);
    vec2 direction = sunView.xy / sideways;
    // Where a line from the center toward the sun leaves the screen, then a bit further out.
    float toEdge = min(halfSize.x / max(abs(direction.x), 1e-3), halfSize.y / max(abs(direction.y), 1e-3));
    vec2 source = direction * (toEdge + 0.35);
    return SUN_COLOR * exp(-length(screen - source) * 1.8) * sideways * 0.5;
}

// Which mipmap level to read for a texture, from how much of the planet one pixel covers
// (`footprint`, as an angle in radians). Working this out from the geometry, instead of
// letting the GPU compare neighboring pixels, means pixels in space can skip the map math
// entirely, and there's no seam where longitude jumps from 180 to -180.
float mipLevel(sampler2D tex, float footprint, float cosLatitude) {
    float texelsPerRadian = float(textureSize(tex, 0).y) / PI;
    // Toward the poles the map stretches east-west by 1/cos(latitude); meet it halfway.
    return log2(footprint * texelsPerRadian / sqrt(max(cosLatitude, 0.05)));
}

vec2 equirectangularUv(vec3 p) {
    // Earth-fixed coordinates: +Y is the north pole, +Z is latitude 0 / longitude 0.
    return vec2(atan(p.x, p.z) / (2.0 * PI) + 0.5, 0.5 - asin(clamp(p.y, -1.0, 1.0)) / PI);
}

// The planet's surface at one point: day and night sides, city lights, clouds and their
// shadows, the ocean glint and the haze near the edge.
vec3 shadeSurface(vec3 normal, vec3 earthNormal, vec3 rayDir, float footprint) {
    vec2 uv = equirectangularUv(earthNormal);
    float cosLatitude = length(earthNormal.xz);

    // The day and night maps share a size, so they share a mipmap level.
    float surfaceLod = mipLevel(uDayTexture, footprint, cosLatitude);
    float cloudLod = mipLevel(uClouds, footprint, cosLatitude);
    vec3 dayColor = textureLod(uDayTexture, uv, surfaceLod).rgb;
    vec3 cityLights = textureLod(uNightTexture, uv, surfaceLod).rgb;
    float ocean = textureLod(uOceanMask, uv, mipLevel(uOceanMask, footprint, cosLatitude)).r;
    float cloud = cloudCover(uv, cloudLod);

    float sunCos = dot(earthNormal, uSunDirection);
    float light = daylight(sunCos);

    // Cloud shadows: the cloud that blocks the sun from this point sits a little toward the sun,
    // further away the lower the sun is (like the length of a shadow: height * tan(sun angle)).
    // Shift the map coordinates in that direction directly (much cheaper than converting a
    // shifted 3D point back with atan/asin), and read a blurrier mipmap: shadows are soft.
    vec3 east = vec3(earthNormal.z, 0.0, -earthNormal.x);  // points east; length is cos(latitude)
    float cosLatitudeSq = max(dot(east, east), 1e-3);
    vec3 north = vec3(-earthNormal.y * earthNormal.x, 1.0 - earthNormal.y * earthNormal.y,
                      -earthNormal.y * earthNormal.z) / sqrt(cosLatitudeSq);  // unit length
    float shift = CLOUD_SHADOW_OFFSET / max(sunCos, 0.2);
    vec2 shadowUv = uv + shift * vec2(dot(uSunDirection, east) / (2.0 * PI * cosLatitudeSq),
                                      -dot(uSunDirection, north) / PI);
    float shadow = cloudCover(shadowUv, cloudLod + 2.0);  // two levels blurrier

    // Day side: the texture lit by the sun, a little warmer and dimmer near the terminator.
    float twilight = light * (1.0 - light) * 4.0;  // peaks halfway through twilight
    vec3 sunTint = mix(vec3(1.0), TWILIGHT_COLOR, 0.35 * twilight);
    float sunlight = 0.15 + 0.85 * max(sunCos, 0.0);
    vec3 sunlit = dayColor * sunlight * sunTint * (1.0 - 0.45 * shadow);

    // Night side: faint moonlit ground plus city lights, which fade in as the sun sets
    // and glow dimly through clouds.
    float darkness = smoothstep(0.05, -0.15, sunCos);
    vec3 night = dayColor * MOONLIGHT * 2.5 + cityLights * CITY_LIGHT_TINT * darkness * (1.0 - 0.7 * cloud);

    vec3 surface = mix(night, sunlit, light);

    // Sunlight reflecting off the water, toward the camera: a bright core and a soft halo,
    // stronger at grazing angles like real water (the Fresnel effect).
    vec3 toCamera = uViewToEarth * -rayDir;
    vec3 halfway = normalize(uSunDirection + toCamera);
    float facing = max(dot(earthNormal, halfway), 0.0);
    float fresnel = 0.3 + 0.7 * pow(1.0 - max(dot(earthNormal, toCamera), 0.0), 5.0);
    // Fades out right at the edge, where the water is seen almost edge-on.
    float visible = smoothstep(0.0, 0.35, dot(earthNormal, toCamera));
    float glint = (pow(facing, 120.0) * 1.2 + pow(facing, 14.0) * 0.12) * fresnel * visible;
    surface += SUN_GLINT_COLOR * glint * ocean * smoothstep(0.0, 0.15, sunCos) * (1.0 - cloud);

    // Clouds on top: bright white in sunlight, warm at twilight, barely visible by moonlight.
    vec3 cloudLit = vec3(0.95) * sunlight * sunTint;
    vec3 cloudColor = mix(vec3(0.04, 0.05, 0.07), cloudLit, light);
    surface = mix(surface, cloudColor, cloud);

    // Air seen at a grazing angle near the edge of the disk turns the day side hazy blue.
    float grazing = pow(1.0 - max(normal.z, 0.0), 3.0);
    return mix(surface, ATMOSPHERE_COLOR * light, 0.6 * grazing * light);
}

// Everything around the planet: stars, the atmosphere glow and the sun.
vec3 shadeSpace(vec3 rayOrigin, vec3 rayDir, float b, float closestSq, float h, vec3 sunView) {
    // How much air the ray passes through above the surface, as a 0..1 strength.
    float shellHalf = sqrt(max(ATMOSPHERE_RADIUS * ATMOSPHERE_RADIUS - closestSq, 0.0));
    float groundHalf = sqrt(h);
    float airPath = (shellHalf - groundHalf) / sqrt(ATMOSPHERE_RADIUS * ATMOSPHERE_RADIUS - 1.0);
    // Light the air by the sun at the ray's closest point to the planet.
    vec3 airPoint = uViewToEarth * normalize(rayOrigin - b * rayDir);
    float airSunCos = dot(airPoint, uSunDirection);
    float airLight = smoothstep(-0.3, 0.3, airSunCos);
    float airTwilight = airLight * (1.0 - airLight) * 4.0;
    vec3 airColor = mix(ATMOSPHERE_COLOR, TWILIGHT_COLOR, 0.5 * airTwilight) * airLight;
    // Looking toward the sun through the air, sunlight scatters forward into the camera: with
    // the sun behind the planet the whole rim glows orange, like every sunset on Earth at once.
    float towardSunlight = pow(max(dot(rayDir, sunView), 0.0), 8.0);
    airColor += mix(TWILIGHT_COLOR, SUN_COLOR, 0.3) * towardSunlight * 1.5;
    vec3 glow = airColor * clamp(airPath, 0.0, 1.0) * 0.9;

    // The planet hides the sun when it's behind it (this is mixed under the planet's edge).
    return SPACE_COLOR + stars(gl_FragCoord.xy) + glow + sunDisk(rayDir, sunView);
}

void main() {
    // Pixel position, scaled so the shorter screen side spans -1..1.
    vec2 screen = (gl_FragCoord.xy - 0.5 * uResolution) / (0.5 * min(uResolution.x, uResolution.y));

    // Pick the field of view so the globe's edge lands at uGlobeScreenRadius.
    float tanEdge = 1.0 / sqrt(uCameraDistance * uCameraDistance - 1.0);
    float tanHalfFov = tanEdge / uGlobeScreenRadius;

    vec3 rayOrigin = vec3(0.0, 0.0, uCameraDistance);
    vec3 rayDir = normalize(vec3(screen * tanHalfFov, -1.0));

    // Ray-sphere intersection.
    float b = dot(rayOrigin, rayDir);
    float closestSq = dot(rayOrigin, rayOrigin) - b * b;  // squared distance from ray to center
    float closest = sqrt(closestSq);
    float h = max(1.0 - closestSq, 0.0);
    vec3 normal = normalize(rayOrigin + (-b - sqrt(h)) * rayDir);
    vec3 earthNormal = uViewToEarth * normal;

    // Direction to the sun in view space (a rotation's inverse is its transpose).
    vec3 sunView = transpose(uViewToEarth) * uSunDirection;

    // How far apart neighboring pixels' rays are (in the same units as rayDir's x and y).
    float pixelSpread = tanHalfFov / (0.5 * min(uResolution.x, uResolution.y));
    // The angle of the planet one pixel covers: more where the surface tilts away from us.
    float hitDistance = -b - sqrt(h);
    float footprint = hitDistance * pixelSpread / max(dot(normal, -rayDir), 0.05);

    // Smooth the planet's edge over about one pixel.
    float edge = 1.0 - closest;
    float coverage = clamp(edge / (uCameraDistance * pixelSpread) + 0.5, 0.0, 1.0);

    // Most of the screen is either all planet or all space, so only shade what's visible.
    vec3 surface = vec3(0.0);
    if (coverage > 0.0) {
        surface = shadeSurface(normal, earthNormal, rayDir, footprint);
    }
    vec3 space = SPACE_COLOR;
    if (coverage < 1.0) {
        space = shadeSpace(rayOrigin, rayDir, b, closestSq, h, sunView);
    }

    // The spilled sunlight lies over everything, planet included.
    vec2 halfSize = uResolution / min(uResolution.x, uResolution.y);
    vec3 color = mix(space, surface, coverage) + sunSpill(screen, halfSize, sunView) * mix(1.0, 0.35, coverage);

    // The centered place: a round dot of the same size wherever it is on the globe, over
    // everything, with a soft dark rim so it still shows over white clouds.
    float pixelSize = 2.0 / min(uResolution.x, uResolution.y);  // one pixel in `screen` units
    float markerDistance = length(screen - uMarker.xy);
    float rim = 1.0 - smoothstep(uMarker.z, uMarker.z * 1.8, markerDistance);
    float core = clamp((uMarker.z - markerDistance) / pixelSize + 0.5, 0.0, 1.0);
    color = mix(color, color * 0.35, rim * uMarker.w);
    color = mix(color, MARKER_COLOR, core * uMarker.w);
    fragColor = vec4(color, 1.0);
}
