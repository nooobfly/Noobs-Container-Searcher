package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noobfly.containersearcher.HighlightManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;
import com.noobfly.containersearcher.ContainerRecord;
import com.noobfly.containersearcher.ContainerSearcherClient;
import com.noobfly.containersearcher.ModSettings;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;

import java.util.List;

public final class Highlights {
	private static final double FRAME_SURFACE_OFFSET = 0.46875D;
	private static final float ITEM_DISPLAY_SCALE = 0.4F;
	private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

	private Highlights() {
	}

	public static void register(HighlightManager manager) {
		WorldRenderEvents.LAST.register(context -> {
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
				ShapeRenderer.renderLineBox(
					poseStack,
					consumer,
					box[0] - cameraPos.x, box[1] - cameraPos.y, box[2] - cameraPos.z,
					box[3] - cameraPos.x, box[4] - cameraPos.y, box[5] - cameraPos.z,
					red, green, blue, alpha
				);
			}
			bufferSource.endBatch(RenderType.lines());
		});

		WorldRenderEvents.AFTER_ENTITIES.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (!ModSettings.itemDisplayEnabled() || client.level == null || client.player == null) {
				return;
			}
			if (!(context.consumers() instanceof MultiBufferSource.BufferSource bufferSource)) {
				return;
			}
			List<ContainerRecord> records = ContainerSearcherClient.knownRecords(client);
			List<HighlightManager.ItemDisplay> displays = manager.itemDisplaysFor(
				client, records, ContainerSearcherClient.dimensionKey(client)
			);
			if (displays.isEmpty()) {
				return;
			}

			Vec3 cameraPos = context.camera().getPosition();
			PoseStack poseStack = context.matrixStack();
			ItemModelResolver resolver = client.getItemModelResolver();
			long secondsElapsed = System.currentTimeMillis() / 1000L;

			for (HighlightManager.ItemDisplay display : displays) {
				List<String> itemIds = display.itemIds();
				String itemId = itemIds.get((int) (secondsElapsed % itemIds.size()));
				ResourceLocation identifier = ResourceLocation.tryParse(itemId);
				if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
					continue;
				}
				ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(identifier));
				ItemStackRenderState renderState = new ItemStackRenderState();
				resolver.updateForNonLiving(renderState, stack, ItemDisplayContext.FIXED, client.player);
				if (renderState.isEmpty()) {
					continue;
				}

				Direction dir = display.direction();
				Vec3 center = display.center();
				double posX = center.x() + dir.getStepX() * FRAME_SURFACE_OFFSET;
				double posY = center.y() + dir.getStepY() * FRAME_SURFACE_OFFSET;
				double posZ = center.z() + dir.getStepZ() * FRAME_SURFACE_OFFSET;
				float facingYRot = 180.0F - dir.toYRot();

				poseStack.pushPose();
				poseStack.translate(posX - cameraPos.x(), posY - cameraPos.y(), posZ - cameraPos.z());
				poseStack.mulPose(Axis.YP.rotationDegrees(facingYRot));
				poseStack.scale(ITEM_DISPLAY_SCALE, ITEM_DISPLAY_SCALE, ITEM_DISPLAY_SCALE);
				renderState.render(poseStack, bufferSource, FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY);
				poseStack.popPose();
			}
			bufferSource.endBatch();
		});
	}
}
