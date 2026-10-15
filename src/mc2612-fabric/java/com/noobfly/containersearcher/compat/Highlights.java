package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.noobfly.containersearcher.ContainerRecord;
import com.noobfly.containersearcher.ContainerSearcherClient;
import com.noobfly.containersearcher.HighlightManager;
import com.noobfly.containersearcher.ModSettings;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Highlights {
	private static final double FRAME_SURFACE_OFFSET = 0.46875D;
	private static final float ITEM_DISPLAY_SCALE = 0.4F;
	private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

	private Highlights() {
	}

	public static void register(HighlightManager manager) {
		LevelRenderEvents.END_MAIN.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (Compat.screen(client) != null) {
				return;
			}
			List<double[]> boxes = manager.visibleBoxes(client);
			if (boxes.isEmpty()) {
				return;
			}
			GizmoStyle style = GizmoStyle.stroke(HighlightManager.HIGHLIGHT_COLOR, HighlightManager.LINE_WIDTH);
			for (double[] box : boxes) {
				Gizmos.cuboid(new AABB(box[0], box[1], box[2], box[3], box[4], box[5]), style).setAlwaysOnTop();
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			Minecraft client = Minecraft.getInstance();
			if (!ModSettings.itemDisplayEnabled() || client.level == null || client.player == null) {
				return;
			}
			List<ContainerRecord> records = ContainerSearcherClient.knownRecords(client);
			List<HighlightManager.ItemDisplay> displays = manager.itemDisplaysFor(
				client, records, ContainerSearcherClient.dimensionKey(client)
			);
			if (displays.isEmpty()) {
				return;
			}

			Vec3 cameraPos = context.gameRenderer().getMainCamera().position();
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			ItemModelResolver resolver = client.getItemModelResolver();
			long secondsElapsed = System.currentTimeMillis() / 1000L;

			for (HighlightManager.ItemDisplay display : displays) {
				List<String> itemIds = display.itemIds();
				String itemId = itemIds.get((int) (secondsElapsed % itemIds.size()));
				Identifier identifier = Identifier.tryParse(itemId);
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
				renderState.submit(poseStack, collector, FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
		});
	}
}
