package com.lastsunset.client;

import com.lastsunset.LastSunset;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** Draws the swollen, burning sun in place of the vanilla square one. */
public final class DyingSunRenderer {
	private static final Identifier DISK = LastSunset.id("textures/environment/sun_disk.png");
	private static final Identifier CORONA = LastSunset.id("textures/environment/corona.png");
	private static final Identifier FLARES = LastSunset.id("textures/environment/flares.png");
	private static final float BASE_SIZE = 30f;

	private static @Nullable GpuBuffer quad;
	private static final Vector4f color = new Vector4f();

	private DyingSunRenderer() {
	}

	/** @return true if the custom sun was drawn and the vanilla one should be skipped. */
	public static boolean render(float alpha, PoseStack poseStack) {
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		SkyVisuals.Look look = SkyVisuals.look(partialTick);
		if (look == null || alpha <= 0f) return false;
		ensureQuad();
		float size = BASE_SIZE * 0.62f * look.sunScale;
		Vector4f c = look.sunColor;
		float a = alpha * c.w;

		// Outer glow, two counter-rotating rings of flares, then the disk itself.
		draw(poseStack, CORONA, size * 2.8f, 0f, color.set(c.x, c.y * 0.8f, c.z * 0.7f, a * 0.55f));
		draw(poseStack, FLARES, size * 2.0f, look.sunSpin, color.set(c.x, c.y * 0.75f, c.z * 0.6f, a * 0.85f));
		draw(poseStack, FLARES, size * 1.75f, -look.sunSpin * 1.4f + 1.3f, color.set(c.x, c.y * 0.9f, c.z * 0.8f, a * 0.6f));
		draw(poseStack, DISK, size, look.sunSpin * 0.25f, color.set(c.x, c.y, c.z, a));
		return true;
	}

	private static void ensureQuad() {
		if (quad != null) return;
		VertexFormat format = DefaultVertexFormat.POSITION_TEX;
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(4 * format.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
			builder.addVertex(-1f, 0f, -1f).setUv(0f, 0f);
			builder.addVertex(1f, 0f, -1f).setUv(1f, 0f);
			builder.addVertex(1f, 0f, 1f).setUv(1f, 1f);
			builder.addVertex(-1f, 0f, 1f).setUv(0f, 1f);
			try (MeshData mesh = builder.buildOrThrow()) {
				quad = RenderSystem.getDevice().createBuffer(() -> "Last Sunset celestial quad", 32, mesh.vertexBuffer());
			}
		}
	}

	private static void draw(PoseStack poseStack, Identifier texture, float size, float rotation, Vector4f tint) {
		if (quad == null || tint.w <= 0.001f) return;
		Minecraft mc = Minecraft.getInstance();
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(poseStack.last().pose());
		modelView.translate(0f, 100f, 0f);
		modelView.rotate(Axis.YP.rotation(rotation));
		modelView.scale(size, 1f, size);
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, tint, new Vector3f(), new Matrix4f());
		GpuTextureView colorTarget = mc.getMainRenderTarget().getColorTextureView();
		GpuTextureView depthTarget = mc.getMainRenderTarget().getDepthTextureView();
		AbstractTexture tex = mc.getTextureManager().getTexture(texture);
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer indexBuffer = indices.getBuffer(6);
		try (RenderPass pass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(() -> "Last Sunset dying sun", colorTarget, OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
			pass.setPipeline(RenderPipelines.CELESTIAL);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler0", tex.getTextureView(), tex.getSampler());
			pass.setVertexBuffer(0, quad);
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(0, 0, 6, 1);
		}
		modelView.popMatrix();
	}
}
