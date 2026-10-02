package com.noobfly.containersearcher;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HighlightManager {
	private static final long HIGHLIGHT_DURATION_MILLIS = 60_000L;
	public static final int HIGHLIGHT_COLOR = 0xFFFFFF55;
	public static final float LINE_WIDTH = 2.0F;

	private final List<Marker> markers = new ArrayList<>();
	private final Map<String, Long> glowingVillagers = new LinkedHashMap<>();

	public void show(Minecraft client, List<ContainerRecord> records, String currentDimension) {
		clear(client);
		if (client.level == null) {
			return;
		}

		long expiresAt = System.currentTimeMillis() + HIGHLIGHT_DURATION_MILLIS;
		for (ContainerRecord record : records) {
			if (record.entityUuid != null && !record.entityUuid.isBlank()) {
				glowingVillagers.put(record.entityUuid, expiresAt);
				continue;
			}
			BlockPos pos = new BlockPos(record.x, record.y, record.z);
			if (currentDimension.equals(record.dimension) && shouldRenderAt(client, pos)) {
				BlockPos secondaryPos = record.secondaryX == null
					? null
					: new BlockPos(record.secondaryX, record.secondaryY, record.secondaryZ);
				BlockPos origin = secondaryPos == null
					? pos
					: new BlockPos(
						Math.min(pos.getX(), secondaryPos.getX()),
						Math.min(pos.getY(), secondaryPos.getY()),
						Math.min(pos.getZ(), secondaryPos.getZ())
					);
				VoxelShape shape = secondaryPos == null
					? Shapes.block()
					: Shapes.box(
						0.0D,
						0.0D,
						0.0D,
						Math.max(pos.getX(), secondaryPos.getX()) - origin.getX() + 1.0D,
						Math.max(pos.getY(), secondaryPos.getY()) - origin.getY() + 1.0D,
						Math.max(pos.getZ(), secondaryPos.getZ()) - origin.getZ() + 1.0D
					);
				markers.add(new Marker(origin, secondaryPos, shape, expiresAt));
			}
		}
	}

	public List<double[]> visibleBoxes(Minecraft client) {
		if (markers.isEmpty() || client.level == null || client.player == null) {
			return List.of();
		}
		List<double[]> boxes = new ArrayList<>();
		for (Marker marker : markers) {
			if (!shouldRenderAt(client, marker.origin)) {
				continue;
			}
			double minX = marker.origin.getX();
			double minY = marker.origin.getY();
			double minZ = marker.origin.getZ();
			boxes.add(new double[]{
				minX,
				minY,
				minZ,
				minX + marker.shape.bounds().getXsize(),
				minY + marker.shape.bounds().getYsize(),
				minZ + marker.shape.bounds().getZsize()
			});
		}
		return boxes;
	}

	public void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		markers.removeIf(marker -> client.level == null || marker.expiresAt <= now);
		List<String> expired = glowingVillagers.entrySet().stream()
			.filter(entry -> client.level == null || entry.getValue() <= now)
			.map(Map.Entry::getKey)
			.toList();
		for (String uuid : expired) {
			glowingVillagers.remove(uuid);
		}
	}

	public void removeEntity(String uuid) {
		glowingVillagers.remove(uuid);
	}

	public boolean isVillagerHighlighted(String uuid) {
		return glowingVillagers.containsKey(uuid);
	}

	public void removeAt(BlockPos pos) {
		markers.removeIf(marker -> marker.contains(pos));
	}

	public void clear(Minecraft client) {
		markers.clear();
		glowingVillagers.clear();
	}

	public record ItemDisplay(Vec3 center, Direction direction, List<String> itemIds) {
	}

	public List<ItemDisplay> itemDisplaysFor(Minecraft client, List<ContainerRecord> records, String currentDimension) {
		if (client.level == null || client.player == null) {
			return List.of();
		}
		List<ItemDisplay> displays = new ArrayList<>();
		for (ContainerRecord record : records) {
			if (record.entityUuid != null && !record.entityUuid.isBlank()) {
				continue;
			}
			if (record.items.isEmpty() || !currentDimension.equals(record.dimension)) {
				continue;
			}
			BlockPos pos = new BlockPos(record.x, record.y, record.z);
			if (!shouldRenderAt(client, pos)) {
				continue;
			}
			BlockPos secondaryPos = record.secondaryX == null
				? null
				: new BlockPos(record.secondaryX, record.secondaryY, record.secondaryZ);
			BlockPos origin = secondaryPos == null
				? pos
				: new BlockPos(
					Math.min(pos.getX(), secondaryPos.getX()),
					Math.min(pos.getY(), secondaryPos.getY()),
					Math.min(pos.getZ(), secondaryPos.getZ())
				);
			VoxelShape shape = secondaryPos == null
				? Shapes.block()
				: Shapes.box(
					0.0D,
					0.0D,
					0.0D,
					Math.max(pos.getX(), secondaryPos.getX()) - origin.getX() + 1.0D,
					Math.max(pos.getY(), secondaryPos.getY()) - origin.getY() + 1.0D,
					Math.max(pos.getZ(), secondaryPos.getZ()) - origin.getZ() + 1.0D
				);
			OpenFace openFace = findOpenFace(client, pos, origin, shape);
			if (openFace == null) {
				continue;
			}
			displays.add(new ItemDisplay(openFace.center(), openFace.direction(), List.copyOf(record.items.keySet())));
		}
		return displays;
	}

	private static final Direction[] HORIZONTAL_DIRECTIONS = {
		Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
	};

	private record OpenFace(Vec3 center, Direction direction) {
	}

	private static OpenFace findOpenFace(Minecraft client, BlockPos pos, BlockPos origin, VoxelShape shape) {
		int minX = origin.getX();
		int minZ = origin.getZ();
		int maxX = minX + (int) shape.bounds().getXsize() - 1;
		int maxZ = minZ + (int) shape.bounds().getZsize() - 1;
		int y = origin.getY();

		Direction chestFacing = chestFacing(client, pos);
		if (chestFacing != null) {
			OpenFace anchored = anchorForDirection(client, chestFacing, minX, maxX, minZ, maxZ, y);
			if (anchored != null) {
				return anchored;
			}
		}

		for (Direction dir : HORIZONTAL_DIRECTIONS) {
			OpenFace anchored = anchorForDirection(client, dir, minX, maxX, minZ, maxZ, y);
			if (anchored != null) {
				return anchored;
			}
		}
		return null;
	}

	private static Direction chestFacing(Minecraft client, BlockPos pos) {
		if (client.level == null || !client.level.hasChunkAt(pos)) {
			return null;
		}
		BlockState state = client.level.getBlockState(pos);
		return state.getBlock() instanceof ChestBlock ? state.getValue(ChestBlock.FACING) : null;
	}

	private static OpenFace anchorForDirection(
		Minecraft client, Direction dir, int minX, int maxX, int minZ, int maxZ, int y
	) {
		boolean anyExterior = false;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				if (nx >= minX && nx <= maxX && nz >= minZ && nz <= maxZ) {
					continue;
				}
				anyExterior = true;
				if (isSolidWall(client, nx, y, nz)) {
					return null;
				}
			}
		}
		if (!anyExterior) {
			return null;
		}
		double centerX = (minX + maxX) / 2.0D + 0.5D;
		double centerZ = (minZ + maxZ) / 2.0D + 0.5D;
		double centerY = y + 0.5D;
		return new OpenFace(new Vec3(centerX, centerY, centerZ), dir);
	}

	private static boolean isSolidWall(Minecraft client, int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (client.level == null || !client.level.hasChunkAt(pos)) {
			return false;
		}
		return !client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty();
	}

	private static boolean shouldRenderAt(Minecraft client, BlockPos pos) {
		if (client.level == null || client.player == null || !client.level.hasChunkAt(pos)) {
			return false;
		}
		int renderDistance = client.options.getEffectiveRenderDistance();
		BlockPos playerPos = client.player.blockPosition();
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		int playerChunkX = playerPos.getX() >> 4;
		int playerChunkZ = playerPos.getZ() >> 4;
		return Math.abs(chunkX - playerChunkX) <= renderDistance
			&& Math.abs(chunkZ - playerChunkZ) <= renderDistance;
	}

	private record Marker(BlockPos origin, BlockPos secondaryPos, VoxelShape shape, long expiresAt) {
		private boolean contains(BlockPos pos) {
			if (origin.equals(pos) || pos.equals(secondaryPos)) {
				return true;
			}
			return pos.getX() >= origin.getX()
				&& pos.getY() >= origin.getY()
				&& pos.getZ() >= origin.getZ()
				&& pos.getX() < origin.getX() + shape.bounds().getXsize()
				&& pos.getY() < origin.getY() + shape.bounds().getYsize()
				&& pos.getZ() < origin.getZ() + shape.bounds().getZsize();
		}
	}
}
