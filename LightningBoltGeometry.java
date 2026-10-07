package net.lightningesp.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Pure geometry generator for the "Lightning" Target ESP mode.
 *
 * This class is a faithful 1:1 port of the Lightning render path from
 * Rockstar Client's {@code TargetEspModule} and has ZERO client-specific
 * dependencies: it only needs vanilla Minecraft classes and JOML. It does not
 * touch RenderSystem, RenderPipeline, the tessellator state or any Rockstar
 * class. The caller supplies the already-open {@link BufferBuilder} and the
 * already-chosen colour, which keeps this file portable between clients.
 *
 * The bolts are 3 lightning arcs per entity, each drawn as 3 parallel
 * polyline strokes (a wide 22px halo at 0.4 alpha plus two ~10px cores at
 * full alpha). Every polyline is recursively subdivided 4 times with a
 * hashed perpendicular jitter, which produces the jagged electrical look, and
 * a single side branch is spawned per subdivision level.
 *
 * No textures, no custom shaders, no mixins are involved.
 */
public final class LightningBoltGeometry {

    /** Recursion depth for the polyline subdivision. */
    private static final int SUBDIVISION_DEPTH = 4;

    /** Number of independent arcs drawn per entity. */
    private static final int BOLT_COUNT = 3;

    /** Points per arc, including the root point (so segments = POINTS - 1). */
    private static final int POINTS_PER_BOLT = 4;

    /** Noise cell rate for the subdivision jitter, in cells per second. */
    private static final float JITTER_CELL_RATE = 6.0F;

    private LightningBoltGeometry() {
    }

    /**
     * Builds the Lightning geometry for one entity into the caller's buffer.
     *
     * @param matrices   stack to push the entity-relative transform onto; must be
     *                   the world-space stack used for 3D entity rendering
     * @param buffer     open {@link BufferBuilder} already begun with
     *                   {@code DrawMode.LINES} + {@code POSITION_COLOR_NORMAL_LINE_WIDTH}
     * @param entity     target entity
     * @param baseColor  colour before the hurt-flash mix
     * @param progress   0..1 fade-in progress of the whole ESP
     * @param time       monotonic seconds accumulator (see LightningEspState)
     * @param alphaScale extra multiplier applied to the alpha; the caller passes a
     *                   damped value for the occluded ("x-ray") pass
     */
    public static void build(
        MatrixStack matrices,
        BufferBuilder buffer,
        LivingEntity entity,
        LightningEspColor baseColor,
        float progress,
        float time,
        float alphaScale
    ) {
        if (progress <= 0.01F) {
            return;
        }

        float hurt = Math.max(0.0F, entity.hurtTime - MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false));
        float flash = (float) Math.sin(hurt * (float) (Math.PI / 10.0)) * 0.5F;
        LightningEspColor color = baseColor.mix(LightningEspColor.RED, flash).mulAlpha(progress * alphaScale);

        float radius = entity.getWidth() * 0.75F + 0.16F;
        float height = entity.getHeight();

        matrices.push();
        translateToEntity(matrices, entity);
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        int cell = (int) (time * JITTER_CELL_RATE);
        float cellFrac = time * JITTER_CELL_RATE - (float) cell;

        for (int bolt = 0; bolt < BOLT_COUNT; bolt++) {
            float phase = time * 1.3F + (float) bolt / 3.0F;
            int flashCell = (int) Math.floor(phase);
            float flashFrac = phase - (float) flashCell;

            // A bolt is only visible during a short window of its own phase, which
            // produces the intermittent flicker. Each bolt gets its own phase offset
            // so they never flash in lockstep.
            float fadeIn = MathHelper.clamp(flashFrac / 0.08F, 0.0F, 1.0F);
            float fadeOut = 1.0F - MathHelper.clamp((flashFrac - 0.42F) / 0.3F, 0.0F, 1.0F);
            float intensity = fadeIn * fadeOut * MathHelper.clamp(0.55F + 0.45F * noise(bolt, 900, cell * 4), 0.0F, 1.0F);
            if (intensity <= 0.01F) {
                continue;
            }

            float bob = height * 0.16F * (float) Math.sin(time * 1.7F + bolt * 2.1F);
            float baseAngle = noise(bolt, 11, flashCell) * (float) Math.PI;
            float arc = 0.6F + 0.5F * Math.abs(noise(bolt, 12, flashCell));
            float arcDir = noise(bolt, 13, flashCell) >= 0.0F ? 1.0F : -1.0F;
            float startY = height * (0.85F + 0.05F * noise(bolt, 14, flashCell));
            float drop = height * 0.67F;
            float boltRadius = radius * (1.0F + 0.12F * noise(bolt, 15, flashCell));
            float scroll = time * 0.9F;

            float previousX = 0.0F;
            float previousY = 0.0F;
            float previousZ = 0.0F;

            for (int point = 0; point <= POINTS_PER_BOLT - 1; point++) {
                float t = (float) point / (POINTS_PER_BOLT - 1);
                float angle = baseAngle + scroll + arcDir * arc * (float) (Math.PI * 2.0) * t;
                float pointX = (float) (Math.cos(angle) * boltRadius);
                float pointZ = (float) (Math.sin(angle) * boltRadius);
                float pointY = startY - drop * t + bob;

                if (point > 0) {
                    int seed = ((bolt * 131 + point) * 17 + flashCell * 101) | 1;
                    // Wide dim halo first, then the two bright cores.
                    stroke(positionMatrix, buffer, previousX, previousY, previousZ, pointX, pointY, pointZ,
                        22.0F, 0.4F, seed, cell, cellFrac, intensity, progress, color);
                    stroke(positionMatrix, buffer, previousX, previousY, previousZ, pointX, pointY, pointZ,
                        10.0F, 1.0F, seed, cell, cellFrac, intensity, progress, color);
                    stroke(positionMatrix, buffer, previousX, previousY, previousZ, pointX, pointY, pointZ,
                        9.0F, 1.0F, seed, cell, cellFrac, intensity, progress, color);
                }

                previousX = pointX;
                previousY = pointY;
                previousZ = pointZ;
            }
        }

        matrices.pop();
    }

    /**
     * Applies one polyline stroke: expands the requested line width by the current
     * intensity/progress, then hands the segment to the recursive subdivision.
     */
    private static void stroke(
        Matrix4f positionMatrix,
        BufferBuilder buffer,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float width,
        float alphaMultiplier,
        int seed,
        int cell,
        float cellFrac,
        float intensity,
        float progress,
        LightningEspColor color
    ) {
        float lineWidth = width * intensity * progress;
        float alpha = Math.min(1.0F, intensity * alphaMultiplier) * color.getAlpha();
        LightningEspColor strokeColor = color.withAlpha(alpha);
        subdivide(positionMatrix, buffer, x1, y1, z1, x2, y2, z2,
            SUBDIVISION_DEPTH, lineWidth, strokeColor, strokeColor, seed, 0, cell, cellFrac);
    }

    /**
     * Recursively halves a segment, displacing the midpoint along the two
     * perpendicular axes of the segment direction by hashed noise. That jitter is
     * what turns a smooth arc into a jagged bolt.
     *
     * @param depth  remaining subdivision levels; 0 emits the final line
     * @param level  branch nesting level, used to limit side branches
     * @param seed   per-segment hash seed, re-derived for each child
     */
    private static void subdivide(
        Matrix4f positionMatrix,
        BufferBuilder buffer,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        int depth,
        float lineWidth,
        LightningEspColor from,
        LightningEspColor to,
        int seed,
        int level,
        int cell,
        float cellFrac
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (depth <= 0 || length < 1.0E-4F) {
            emitSegment(positionMatrix, buffer, x1, y1, z1, x2, y2, z2, lineWidth, from, to);
            return;
        }

        float dirX = dx / length;
        float dirY = dy / length;
        float dirZ = dz / length;

        // Pick the axis least aligned with the segment so the perpendicular basis
        // never degenerates for vertical segments.
        boolean vertical = Math.abs(dirY) >= 0.9F;
        float u = vertical ? 1.0F : 0.0F;
        float v = vertical ? 0.0F : 1.0F;

        float p1x = -dirZ * v;
        float p1y = dirZ * u;
        float p1z = dirX * v - dirY * u;
        float p1Length = (float) Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        if (p1Length < 1.0E-5F) {
            p1Length = 1.0F;
        }

        p1x /= p1Length;
        p1y /= p1Length;
        p1z /= p1Length;

        float p2x = dirY * p1z - dirZ * p1y;
        float p2y = dirZ * p1x - dirX * p1z;
        float p2z = dirX * p1y - dirY * p1x;

        float h1 = smoothNoise(seed, 0, cell, cellFrac);
        float h2 = smoothNoise(seed, 1, cell, cellFrac);

        float midScale = length * 0.3F;
        float midX = (x1 + x2) * 0.5F + (p1x * h1 + p2x * h2) * midScale;
        float midY = (y1 + y2) * 0.5F + (p1y * h1 + p2y * h2) * midScale;
        float midZ = (z1 + z2) * 0.5F + (p1z * h1 + p2z * h2) * midScale;
        float midAlpha = (from.getAlpha() + to.getAlpha()) * 0.5F;

        subdivide(positionMatrix, buffer, x1, y1, z1, midX, midY, midZ,
            depth - 1, lineWidth, from, from.withAlpha(midAlpha), seed * 2 + 7, level, cell, cellFrac);
        subdivide(positionMatrix, buffer, midX, midY, midZ, x2, y2, z2,
            depth - 1, lineWidth, from.withAlpha(midAlpha), to, seed * 2 + 9, level, cell, cellFrac);

        // One side branch per subdivision, tapering out to fully transparent.
        if (level < 2 && depth >= 2 && smoothNoise(seed, 2, cell, cellFrac) > 0.45F) {
            float b1 = smoothNoise(seed, 3, cell, cellFrac);
            float b2 = smoothNoise(seed, 4, cell, cellFrac);
            float bdx = dirX * 0.4F + p1x * b1 + p2x * b2;
            float bdy = dirY * 0.4F + p1y * b1 + p2y * b2;
            float bdz = dirZ * 0.4F + p1z * b1 + p2z * b2;
            float bLength = (float) Math.sqrt(bdx * bdx + bdy * bdy + bdz * bdz);
            if (bLength < 1.0E-5F) {
                bLength = 1.0F;
            }

            float reach = length * 0.75F;
            float endX = midX + bdx / bLength * reach;
            float endY = midY + bdy / bLength * reach;
            float endZ = midZ + bdz / bLength * reach;

            subdivide(positionMatrix, buffer, midX, midY, midZ, endX, endY, endZ,
                Math.min(depth - 1, 3), lineWidth * 0.55F,
                from.withAlpha(midAlpha * 0.7F), from.withAlpha(0.0F),
                seed * 3 + 13, level + 1, cell, cellFrac);
        }
    }

    /** Writes the final two LINE vertices with the segment normal and line width. */
    private static void emitSegment(
        Matrix4f positionMatrix,
        BufferBuilder buffer,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float lineWidth,
        LightningEspColor from,
        LightningEspColor to
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-5F) {
            return;
        }

        float inv = 1.0F / length;
        float normalX = dx * inv;
        float normalY = dy * inv;
        float normalZ = dz * inv;

        buffer.vertex(positionMatrix, x1, y1, z1).color(from.getRGB()).normal(normalX, normalY, normalZ).lineWidth(lineWidth);
        buffer.vertex(positionMatrix, x2, y2, z2).color(to.getRGB()).normal(normalX, normalY, normalZ).lineWidth(lineWidth);
    }

    /**
     * Deterministic hash noise in the [-1, 1] range. Same signature and constants
     * as Rockstar's noise function, so bolts look identical frame to frame.
     */
    public static float noise(int a, int b, int c) {
        int hash = a * 374761393 + b * 668265263 + c * 1274126177;
        hash = (hash ^ hash >>> 13) * 1274126177;
        hash ^= hash >>> 16;
        return (hash & 65535) / 32768.0F - 1.0F;
    }

    /**
     * Smoothly interpolates between two adjacent noise cells so the jitter does
     * not pop every cell boundary. This is what makes the bolt crawl rather than
     * teleport.
     */
    public static float smoothNoise(int a, int b, int cell, float t) {
        float from = noise(a, b, cell * 4);
        float to = noise(a, b, (cell + 1) * 4);
        float smooth = t * t * (3.0F - 2.0F * t);
        return from + (to - from) * smooth;
    }

    /** World-space interpolated position of an entity (render-tick interpolated). */
    public static Vec3d interpolatedPos(LivingEntity entity) {
        float tickProgress = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        return new Vec3d(
            MathHelper.lerp(tickProgress, entity.lastX, entity.getX()),
            MathHelper.lerp(tickProgress, entity.lastY, entity.getY()),
            MathHelper.lerp(tickProgress, entity.lastZ, entity.getZ())
        );
    }

    /** Translates the matrix stack from world space into camera-relative space. */
    public static void translateToEntity(MatrixStack matrices, LivingEntity entity) {
        translateToEntity(matrices, entity, MinecraftClient.getInstance().gameRenderer.getCamera());
    }

    /** Translates the matrix stack from world space into camera-relative space. */
    public static void translateToEntity(MatrixStack matrices, LivingEntity entity, Camera camera) {
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d relative = interpolatedPos(entity).subtract(cameraPos);
        matrices.translate(relative.getX(), relative.getY(), relative.getZ());
    }
}