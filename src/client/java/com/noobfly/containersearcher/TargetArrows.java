package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// arrows on the screen edge that point to the searched containers while they are out of view
public final class TargetArrows {
	private static final int MAX_ARROWS = 4;
	private static final int MARGIN = 24;
	private static final int ICON_SIZE = 32;
	private static final int LABEL_COLOR = 0xFFFFFFFF;

	private TargetArrows() {
	}

	private record Target(double dirX, double dirY, double distance) {
	}

	public static void render(Gfx graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || Compat.screen(client) != null || !ModSettings.targetArrowsEnabled()) {
			return;
		}
		HighlightManager manager = HighlightManager.instance();
		if (manager == null) {
			return;
		}
		List<double[]> boxes = manager.visibleBoxes(client);
		if (boxes.isEmpty()) {
			return;
		}

		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		Vec3 eye = client.player.getEyePosition();
		double yaw = Math.toRadians(client.player.getYRot());
		double pitch = Math.toRadians(client.player.getXRot());
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		double tanHalf = Math.tan(Math.toRadians(client.options.fov().get()) / 2.0D);
		double aspect = (double) width / height;

		List<Target> targets = new ArrayList<>();
		for (double[] box : boxes) {
			double dx = (box[0] + box[3]) / 2.0D - eye.x;
			double dy = (box[1] + box[4]) / 2.0D - eye.y;
			double dz = (box[2] + box[5]) / 2.0D - eye.z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (distance < 1.0E-3D) {
				continue;
			}
			double right = (dx * rx + dz * rz) / distance;
			double up = (dx * ux + dy * uy + dz * uz) / distance;
			double forward = (dx * fx + dy * fy + dz * fz) / distance;
			if (forward > 0.05D) {
				double screenX = right / forward / (tanHalf * aspect);
				double screenY = up / forward / tanHalf;
				if (Math.abs(screenX) < 0.92D && Math.abs(screenY) < 0.92D) {
					continue;
				}
			}
			double sx = right;
			double sy = -up;
			double length = Math.sqrt(sx * sx + sy * sy);
			if (length < 0.02D) {
				// straight behind or straight ahead and off screen: point down
				sx = 0.0D;
				sy = 1.0D;
				length = 1.0D;
			}
			targets.add(new Target(sx / length, sy / length, distance));
		}
		targets.sort(Comparator.comparingDouble(Target::distance));

		double halfWidth = width / 2.0D - MARGIN;
		double halfHeight = height / 2.0D - MARGIN;
		for (int i = 0; i < Math.min(MAX_ARROWS, targets.size()); i++) {
			Target target = targets.get(i);
			double scaleX = target.dirX == 0.0D ? Double.MAX_VALUE : halfWidth / Math.abs(target.dirX);
			double scaleY = target.dirY == 0.0D ? Double.MAX_VALUE : halfHeight / Math.abs(target.dirY);
			double scale = Math.min(scaleX, scaleY);
			int cx = (int) Math.round(width / 2.0D + target.dirX * scale);
			int cy = (int) Math.round(height / 2.0D + target.dirY * scale);
			int step = (int) Math.round(Math.toDegrees(Math.atan2(target.dirX, -target.dirY)) / 5.0D);
			graphics.icon("arrow_" + String.format("%02d", Math.floorMod(step, 72)), cx - ICON_SIZE / 2, cy - ICON_SIZE / 2, ICON_SIZE);

			String label = Math.round(target.distance) + " m";
			int labelWidth = client.font.width(label);
			// the label sits on the inner side of the arrow so it never leaves the screen
			int labelX = (int) Math.round(cx - target.dirX * 24.0D - labelWidth / 2.0D);
			int labelY = (int) Math.round(cy - target.dirY * 20.0D - 4.0D);
			graphics.text(client.font, label, labelX, labelY, LABEL_COLOR);
		}
	}
}
