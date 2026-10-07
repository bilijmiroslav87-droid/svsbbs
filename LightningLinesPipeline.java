package net.lightningesp.vanilla;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Vanilla Minecraft 1.21.11 submit path for the Lightning ESP lines.
 *
 * WHY THIS FILE EXISTS
 * 1.21.11 deleted {@code BufferRenderer}, {@code RenderSystem.setShader} and the
 * whole global GL state API (depthMask / blendFunc / enableCull / lineWidth).
 * Every mesh must now be submitted through an explicit {@link RenderPipeline}
 * inside a {@link RenderPass}. This class replaces Rockstar's
 * {@code rockstar.client.compat.RenderSystem} + {@code compat.BufferRenderer}
 * shim pair and depends on vanilla classes only.
 *
 * Two pipelines are used because the ESP draws twice:
 * <ul>
 *   <li>{@link #LINE_PIPELINE} — LEQUAL depth test, the normal "visible parts" pass</li>
 *   <li>{@link #XRAY_PIPELINE} — GREATER depth test, the "through walls" pass that
 *       only shows up when the target is occluded</li>
 * </ul>
 * Both use additive blending (SRC_ALPHA, ONE), no depth write and no culling,
 * matching Rockstar's {@code blendFunc(SRC_ALPHA, ONE)} + {@code depthMask(false)}
 * + {@code disableCull()} state.
 *
 * Note: glLineWidth is capped at 1.0 in the 1.21.11 forward-compatible context.
 * That is fine — the line width is expanded into quads inside the vanilla
 * {@code rendertype_lines} vertex shader, which is why the per-vertex
 * {@code lineWidth} attribute is still meaningful.
 */
public final class LightningLinesPipeline {

    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f NO_MODEL_OFFSET = new Vector3f(0.0F, 0.0F, 0.0F);
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static RenderPipeline linePipeline;
    private static RenderPipeline xrayPipeline;

    private LightningLinesPipeline() {
    }

    /**
     * Builds both pipelines. Registering a pipeline requires the shader loader
     * to be up, so call this from client setup rather than lazily from a render
     * callback. Safe to call more than once.
     */
    public static synchronized void init() {
        if (linePipeline != null && xrayPipeline != null) {
            return;
        }
        linePipeline = build(Identifier.of("lightningesp", "pipeline/lightning_lines"),
            DepthTestFunction.LEQUAL_DEPTH_TEST);
        xrayPipeline = build(Identifier.of("lightningesp", "pipeline/lightning_xray"),
            DepthTestFunction.GREATER_DEPTH_TEST);
    }

    private static RenderPipeline build(Identifier location, DepthTestFunction depthTest) {
        return RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                .withLocation(location)
                .withBlend(BlendFunction.ADDITIVE)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(depthTest)
                .build()
        );
    }

    /** Normal pass: only the parts of the bolts that are not behind geometry. */
    public static void drawLines(BuiltBuffer buffer) {
        draw(buffer, linePipeline);
    }

    /** Through-walls pass: only the parts of the bolts hidden behind geometry. */
    public static void drawXrayLines(BuiltBuffer buffer) {
        draw(buffer, xrayPipeline);
    }

    /**
     * Submits a LINE-mode {@link BuiltBuffer} using the given pipeline.
     *
     * <p>Takes ownership of {@code buffer} and closes it, mirroring the original
     * {@code try (BuiltBuffer buffer = ...)} usage.
     */
    public static void draw(BuiltBuffer buffer, RenderPipeline pipeline) {
        if (buffer == null) {
            return;
        }
        if (pipeline == null) {
            init();
            pipeline = linePipeline;
        }

        try (buffer) {
            BuiltBuffer.DrawParameters parameters = buffer.getDrawParameters();
            VertexFormat format = parameters.format();

            // DynamicTransforms is a ring buffer, so the slice must be allocated
            // immediately before the pass that uses it.
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
                RenderSystem.getModelViewMatrix(), WHITE, NO_MODEL_OFFSET, IDENTITY
            );
            GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(buffer.getBuffer());
            RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(parameters.mode());
            GpuBuffer indexBuffer = sequential.getIndexBuffer(parameters.indexCount());
            VertexFormat.IndexType indexType = sequential.getIndexType();

            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
            if (framebuffer == null) {
                return;
            }

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Lightning ESP",
                framebuffer.getColorAttachmentView(),
                OptionalInt.empty(),
                framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null,
                OptionalDouble.empty()
            )) {
                pass.setPipeline(pipeline);
                pass.disableScissor();
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indexType);
                pass.drawIndexed(0, 0, parameters.indexCount(), 1);
            }
        }
    }

    /**
     * Opens a LINE-mode buffer in the format the Lightning renderer expects.
     * {@code POSITION_COLOR_NORMAL_LINE_WIDTH} carries the per-vertex line
     * width that gives the bolts their thick, glowing core.
     */
    public static BufferBuilder beginLines() {
        return Tessellator.getInstance().begin(
            VertexFormat.DrawMode.LINES,
            VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH
        );
    }
}