package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noobfly.containersearcher.HighlightManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Highlights {
	private Highlights() {
	}

	public static void register(HighlightManager manager) {
		WorldRenderEvents.END.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (Compat.screen(client) != null) {
				return;
			}
			List<double[]> boxes = manager.visibleBoxes(client);
			if (boxes.isEmpty()) {
				return;
			}

			Camera camera = context.camera();
			Vec3 cameraPos = camera.getPosition();
			PoseStack poseStack = context.matrixStack();
			MultiBufferSource.BufferSource bufferSource = (MultiBufferSource.BufferSource) context.consumers();
			VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

			int color = HighlightManager.HIGHLIGHT_COLOR;
			float alpha = ((color >> 24) & 0xFF) / 255.0F;
			float red = ((color >> 16) & 0xFF) / 255.0F;
			float green = ((color >> 8) & 0xFF) / 255.0F;
			float blue = (color & 0xFF) / 255.0F;

			for (double[] box : boxes) {
				LevelRenderer.renderLineBox(
					poseStack,
					consumer,
					box[0] - cameraPos.x, box[1] - cameraPos.y, box[2] - cameraPos.z,
					box[3] - cameraPos.x, box[4] - cameraPos.y, box[5] - cameraPos.z,
					red, green, blue, alpha
				);
			}
			bufferSource.endBatch(RenderType.lines());
		});
	}
}
