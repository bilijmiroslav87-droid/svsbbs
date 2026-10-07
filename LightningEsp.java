package net.lightningesp.vanilla;

import java.util.function.Supplier;
import net.lightningesp.core.LightningBoltGeometry;
import net.lightningesp.core.LightningEspColor;
import net.lightningesp.core.LightningEspState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

/**
 * Ready-to-use "Target ESP" module with the Lightning render mode.
 *
 * This is the glue layer: it owns the settings, resolves the target entity,
 * runs the occlusion raycast and drives {@link LightningEspState}, then hands
 * geometry to {@link LightningBoltGeometry} and submits it through
 * {@link LightningLinesPipeline}.
 *
 * It is intentionally framework free — no module base class, no event bus, no
 * settings framework. Wire it up like this:
 *
 * <pre>{@code
 * LightningEsp esp = new LightningEsp();
 *
 * // once, during client setup (pipelines need the shader loader to be up)
 * LightningLinesPipeline.init();
 *
 * // every rendered frame, from your 3D render hook
 * esp.render(matrices, camera);
 * }</pre>
 *
 * There are no textures, no custom shaders and no mixins required.
 */
public final class LightningEsp {

    private final LightningEspState state = new LightningEspState();

    private boolean enabled;
    private boolean rayTrace = true;
    private LightningEspColor color = new LightningEspColor(140.0F, 200.0F, 255.0F, 255.0F);

    /**
     * Supplies the host client's cached target entity. When {@link #setRayTrace}
     * is off this is the entity the ESP locks onto; when it is on, the live
     * crosshair target wins. Leave null to fall back to the crosshair target.
     */
    private Supplier<Entity> targetSupplier;

    // ---------------------------------------------------------------- settings

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.state.clearTarget();
        }
    }

    public boolean isRayTrace() {
        return this.rayTrace;
    }

    /**
     * When true the ESP follows the crosshair target live (the original
     * {@code Ray Trace} setting). When false it holds whatever the host client
     * supplies via {@link #setTargetSupplier}.
     */
    public void setRayTrace(boolean rayTrace) {
        this.rayTrace = rayTrace;
    }

    public LightningEspColor getColor() {
        return this.color;
    }

    public void setColor(LightningEspColor color) {
        this.color = color;
    }

    /** Convenience setter taking a packed 0xAARRGGBB int. */
    public void setColorArgb(int argb) {
        this.color = LightningEspColor.fromArgb(argb);
    }

    public void setTargetSupplier(Supplier<Entity> targetSupplier) {
        this.targetSupplier = targetSupplier;
    }

    /** Exposes the animation state if the host wants to drive it manually. */
    public LightningEspState getState() {
        return this.state;
    }

    // ------------------------------------------------------------------ render

    /**
     * Renders the Lightning ESP for the currently targeted entity.
     *
     * <p>Safe to call every frame: it self-guards when disabled, when there is
     * no world, or when there is no target.
     *
     * @param matrices the world-space matrix stack for 3D rendering
     * @param camera   the active camera, used for the occlusion raycast
     */
    public void render(MatrixStack matrices, Camera camera) {
        if (!this.enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.gameRenderer == null) {
            return;
        }

        LivingEntity target = resolveTarget();
        this.state.updateTarget(target);
        this.state.tick(target != null);

        LivingEntity drawTarget = this.state.getLastTarget();
        float progress = this.state.getProgress();
        if (drawTarget == null || progress == 0.0F) {
            return;
        }

        this.state.setOccluded(isOccluded(client, drawTarget, camera));

        float time = this.state.getTime();
        LightningEspColor base = this.color;

        // Pass 1 — LEQUAL depth test: the visible parts of the bolts.
        drawPass(matrices, drawTarget, base, progress, time, 1.0F, false);

        // Pass 2 — GREATER depth test: the parts hidden behind geometry. Only
        // runs when the target is occluded, which is what gives the ESP its
        // "glowing through walls" look without permanently drawing the
        // hidden silhouette.
        float xray = this.state.getOcclusion();
        if (xray > 0.01F) {
            drawPass(matrices, drawTarget, base, progress, time, xray, true);
        }
    }

    /**
     * One geometry build + submit. All GL/pipeline state lives in the pipeline,
     * so there is no RenderSystem state juggling here — unlike the original
     * Rockstar code, which had to call depthFunc(515/516) around each pass.
     */
    private void drawPass(
        MatrixStack matrices,
        LivingEntity entity,
        LightningEspColor base,
        float progress,
        float time,
        float alphaScale,
        boolean xray
    ) {
        BufferBuilder buffer = LightningLinesPipeline.beginLines();
        LightningBoltGeometry.build(matrices, buffer, entity, base, progress, time, alphaScale);

        BuiltBuffer built = buffer.endNullable();
        if (built == null) {
            return;
        }
        if (xray) {
            LightningLinesPipeline.drawXrayLines(built);
        } else {
            LightningLinesPipeline.drawLines(built);
        }
    }

    private LivingEntity resolveTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity cached = this.targetSupplier == null ? null : this.targetSupplier.get();

        if (this.rayTrace && client.crosshairTarget instanceof LivingEntity crosshair) {
            return crosshair;
        }
        if (cached instanceof LivingEntity cachedLiving) {
            return cachedLiving;
        }
        if (client.crosshairTarget instanceof LivingEntity crosshair) {
            return crosshair;
        }
        return null;
    }

    private static boolean isOccluded(MinecraftClient client, LivingEntity entity, Camera camera) {
        if (entity.isTouchingWater()) {
            return true;
        }
        return client.world.raycast(
            new RaycastContext(
                camera.getCameraPos(),
                entity.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
            )
        ).getType() != HitResult.Type.MISS;
    }
}