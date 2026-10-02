package com.noobfly.containersearcher.compat;

import com.noobfly.containersearcher.HighlightManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class Highlights {
	private Highlights() {
	}

	public static void register(HighlightManager manager) {
		WorldRenderEvents.END_MAIN.register(context -> {
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
	}
}
