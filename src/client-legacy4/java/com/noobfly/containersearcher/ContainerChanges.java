package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.Gfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Tells the player what changed in a container since the last time they looked inside.
// Chests, barrels and shulker boxes move their lid for everyone nearby, so while a remembered one is
// loaded we notice other players opening it. Hoppers and droppers around it are checked when it opens.
public final class ContainerChanges {
	private static final int MAX_EVENTS = 8;
	private static final int MAX_PINNED_EVENTS = 3;
	private static final int REFRESH_TICKS = 20;
	private static final int POLL_TICKS = 3;
	private static final double WATCH_RADIUS = 48.0D;
	private static final double WITNESS_RADIUS = 8.0D;
	private static final int MAX_WITNESSES = 3;

	private static final int PANEL_WIDTH = 184;
	private static final int HEADER_HEIGHT = 28;
	private static final int PAD = 5;
	private static final int ROW_GAP = 3;
	private static final int LINE_HEIGHT = 10;
	// same black palette as the search screen
	private static final int PANEL_BG = 0xF00C0C0E;
	private static final int PANEL_BORDER = 0xFF3A3A41;
	private static final int HEADER_BG = 0xFF121214;
	private static final int ACCENT = 0xFF5C5C66;
	private static final int BG_CARD = 0xFF17171A;
	private static final int BG_GAIN = 0xFF13211A;
	private static final int BG_LOSS = 0xFF251417;
	private static final int TEXT_TITLE = 0xFFF2F3F5;
	private static final int TEXT_PRIMARY = 0xFFE4E5E8;
	private static final int TEXT_SECONDARY = 0xFFA8A8B0;
	private static final int TEXT_MUTED = 0xFF868690;
	private static final int TEXT_GAIN = 0xFF3BA55D;
	private static final int TEXT_LOSS = 0xFFED4245;
	private static final int TEXT_EVENT = 0xFFFAA61A;
	private static final int TEXT_HINT = 0xFFE0A96B;

	private static final class Watched {
		BlockPos primary;
		BlockPos secondary;
		boolean known;
		boolean open;
		boolean halfReported;
	}

	private static final class Delta {
		final String name;
		final int change;
		final ContainerItemRecord sample;
		ItemStack icon;

		Delta(String name, int change, ContainerItemRecord sample) {
			this.name = name;
			this.change = change;
			this.sample = sample;
		}
	}

	private static final class Hint {
		final String key;
		final String arg;

		Hint(String key, String arg) {
			this.key = key;
			this.arg = arg;
		}
	}

	private static final class Notice {
		long previousAt;
		List<Delta> deltas = new ArrayList<>();
		List<ContainerEvent> events = new ArrayList<>();
		List<Hint> hints = new ArrayList<>();
	}

	private static final Map<String, Watched> WATCHED = new HashMap<>();
	private static ClientLevel watchedLevel;
	private static int refreshTicks;
	private static int pollTicks;
	private static Notice active;

	// where the panel was drawn last frame, in screen coordinates, so the mouse wheel can scroll it
	private static int panelLeft;
	private static int panelTop;
	private static int panelHeight;
	// scrolling is in pixels and eases towards the target like the vanilla lists
	private static float scrollPos;
	private static float scrollTarget;
	private static float maxScroll;
	private static long lastFrame;

	private ContainerChanges() {
	}

	// ---- watching other players open containers ----

	public static void tick(Minecraft client, ContainerDatabase database) {
		if (client.level == null || client.player == null || !ModSettings.trackChangesEnabled()) {
			clear();
			WATCHED.clear();
			watchedLevel = null;
			return;
		}
		if (client.level != watchedLevel) {
			watchedLevel = client.level;
			WATCHED.clear();
			refreshTicks = REFRESH_TICKS;
		}
		if (++refreshTicks >= REFRESH_TICKS) {
			refreshTicks = 0;
			refresh(client, database);
		}
		if (++pollTicks >= POLL_TICKS) {
			pollTicks = 0;
			poll(client, database);
		}
	}

	private static void refresh(Minecraft client, ContainerDatabase database) {
		String dimension = ContainerSearcherClient.dimensionKey(client);
		BlockPos me = client.player.blockPosition();
		Map<String, Watched> next = new HashMap<>();
		for (ContainerRecord record : database.allForServer(ContainerSearcherClient.serverKey(client))) {
			if (!dimension.equals(record.dimension)
				|| record.entityUuid != null && !record.entityUuid.isBlank()
				|| !watchable(record.containerType)) {
				continue;
			}
			double dx = record.x + 0.5D - me.getX();
			double dy = record.y + 0.5D - me.getY();
			double dz = record.z + 0.5D - me.getZ();
			if (dx * dx + dy * dy + dz * dz > WATCH_RADIUS * WATCH_RADIUS) {
				continue;
			}
			String key = ContainerDatabase.keyOf(record);
			Watched watched = WATCHED.get(key);
			if (watched == null) {
				watched = new Watched();
			}
			watched.primary = new BlockPos(record.x, record.y, record.z);
			watched.secondary = record.secondaryX == null || record.secondaryY == null || record.secondaryZ == null
				? null
				: new BlockPos(record.secondaryX, record.secondaryY, record.secondaryZ);
			next.put(key, watched);
		}
		WATCHED.clear();
		WATCHED.putAll(next);
	}

	// ender chests are per player, so somebody else opening one tells us nothing
	private static boolean watchable(String type) {
		if (type == null) {
			return false;
		}
		return type.endsWith("chest") && !type.contains("ender")
			|| type.endsWith("barrel")
			|| type.endsWith("shulker_box");
	}

	private static void poll(Minecraft client, ContainerDatabase database) {
		for (Watched watched : WATCHED.values()) {
			BlockPos pos = watched.primary;
			if (!client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				watched.known = false;
				continue;
			}
			checkHalfBroken(client, database, watched);
			boolean open = isOpen(client.level, pos);
			if (!watched.known) {
				// first look, the lid might already be up for a reason we did not see
				watched.known = true;
				watched.open = open;
				continue;
			}
			if (open && !watched.open && !ContainerSearcherClient.isOwnContainer(pos, watched.secondary)) {
				onOpened(client, database, watched);
			}
			watched.open = open;
		}
	}

	// one half of a remembered double chest turned into something else while both halves are loaded
	private static void checkHalfBroken(Minecraft client, ContainerDatabase database, Watched watched) {
		BlockPos other = watched.secondary;
		if (other == null || !client.level.hasChunk(other.getX() >> 4, other.getZ() >> 4)) {
			return;
		}
		boolean firstOk = client.level.getBlockEntity(watched.primary) instanceof LidBlockEntity;
		boolean secondOk = client.level.getBlockEntity(other) instanceof LidBlockEntity;
		if (firstOk && secondOk) {
			watched.halfReported = false;
			return;
		}
		if (firstOk == secondOk || watched.halfReported) {
			return;
		}
		watched.halfReported = true;
		BlockPos gone = firstOk ? other : watched.primary;
		// our own swing nearby means we did it ourselves
		if (client.options.keyAttack.isDown() && client.player.distanceToSqr(gone.getX() + 0.5D, gone.getY() + 0.5D, gone.getZ() + 0.5D) <= 49.0D) {
			return;
		}
		ContainerRecord record = database.find(
			ContainerSearcherClient.serverKey(client), ContainerSearcherClient.dimensionKey(client), watched.primary);
		if (record == null) {
			return;
		}
		ContainerEvent event = new ContainerEvent();
		event.time = System.currentTimeMillis();
		event.broke = true;
		event.players = witnesses(client, watched);
		record.events.add(event);
		while (record.events.size() > MAX_EVENTS) {
			record.events.remove(0);
		}
		database.putDeferred(record);
	}

	private static boolean isOpen(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BarrelBlock) {
			return state.getValue(BarrelBlock.OPEN);
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof LidBlockEntity lid) {
			return lid.getOpenNess(1.0F) > 0.0F;
		}
		if (blockEntity instanceof ShulkerBoxBlockEntity box) {
			return box.getProgress(1.0F) > 0.0F;
		}
		return false;
	}

	private static void onOpened(Minecraft client, ContainerDatabase database, Watched watched) {
		ContainerRecord record = database.find(
			ContainerSearcherClient.serverKey(client), ContainerSearcherClient.dimensionKey(client), watched.primary);
		if (record == null) {
			return;
		}
		ContainerEvent event = new ContainerEvent();
		event.time = System.currentTimeMillis();
		event.players = witnesses(client, watched);
		if (record.events == null) {
			record.events = new ArrayList<>();
		}
		record.events.add(event);
		while (record.events.size() > MAX_EVENTS) {
			record.events.remove(0);
		}
		database.putDeferred(record);
	}

	// whoever stands next to it right now is the likely culprit, nobody close means they already walked off
	private static List<String> witnesses(Minecraft client, Watched watched) {
		double cx = watched.primary.getX() + 0.5D;
		double cy = watched.primary.getY() + 0.5D;
		double cz = watched.primary.getZ() + 0.5D;
		if (watched.secondary != null) {
			cx = (cx + watched.secondary.getX() + 0.5D) / 2.0D;
			cz = (cz + watched.secondary.getZ() + 0.5D) / 2.0D;
		}
		List<AbstractClientPlayer> near = new ArrayList<>();
		for (AbstractClientPlayer player : client.level.players()) {
			if (player != client.player && player.distanceToSqr(cx, cy, cz) <= WITNESS_RADIUS * WITNESS_RADIUS) {
				near.add(player);
			}
		}
		final double fx = cx;
		final double fy = cy;
		final double fz = cz;
		near.sort((a, b) -> Double.compare(a.distanceToSqr(fx, fy, fz), b.distanceToSqr(fx, fy, fz)));
		List<String> names = new ArrayList<>();
		for (AbstractClientPlayer player : near) {
			if (names.size() >= MAX_WITNESSES) {
				break;
			}
			names.add(player.getName().getString());
		}
		return names;
	}

	// ---- comparing with what we remembered ----

	public static void begin(
		Minecraft client,
		ContainerDatabase database,
		BlockPos pos,
		BlockPos duplicate,
		Map<String, Integer> after,
		List<ContainerItemRecord> entries
	) {
		active = null;
		scrollPos = 0.0F;
		scrollTarget = 0.0F;
		if (!ModSettings.trackChangesEnabled()) {
			return;
		}
		String server = ContainerSearcherClient.serverKey(client);
		String dimension = ContainerSearcherClient.dimensionKey(client);
		ContainerRecord previous = database.find(server, dimension, pos);
		if (previous == null && duplicate != null) {
			previous = database.find(server, dimension, duplicate);
		}
		if (previous == null) {
			// the chest we opened may be the survivor of a double chest saved under the other half
			for (ContainerRecord candidate : database.allForServer(server)) {
				if (dimension.equals(candidate.dimension) && candidate.secondaryX != null
					&& candidate.secondaryX == pos.getX() && candidate.secondaryY == pos.getY() && candidate.secondaryZ == pos.getZ()) {
					previous = candidate;
					break;
				}
			}
		}
		if (previous == null) {
			return;
		}
		boolean halfLost = previous.halfLost || previous.secondaryX != null && duplicate == null;

		Notice notice = new Notice();
		notice.previousAt = previous.updatedAt;
		Set<String> ids = new LinkedHashSet<>(previous.items.keySet());
		ids.addAll(after.keySet());
		boolean removed = false;
		boolean added = false;
		for (String id : ids) {
			int change = after.getOrDefault(id, 0) - previous.items.getOrDefault(id, 0);
			if (change == 0) {
				continue;
			}
			ContainerItemRecord sample = sampleOf(change < 0 ? previous.entries : entries, id);
			notice.deltas.add(new Delta(sample != null && sample.name != null ? sample.name : id, change, sample));
			removed |= change < 0;
			added |= change > 0;
		}
		notice.deltas.sort((a, b) -> Integer.compare(Math.abs(b.change), Math.abs(a.change)));
		if (previous.events != null) {
			notice.events.addAll(previous.events);
		}

		boolean hopperBelow = false;
		boolean feeder = false;
		String movedTo = null;
		String feederSource = null;
		List<BlockPos> halves = halves(pos, duplicate);
		BlockPos savedSecondary = previous.secondaryX == null || previous.secondaryY == null || previous.secondaryZ == null
			? null
			: new BlockPos(previous.secondaryX, previous.secondaryY, previous.secondaryZ);
		if (savedSecondary != null && !halves.contains(savedSecondary) && client.level.getBlockState(savedSecondary).is(client.level.getBlockState(pos).getBlock())) {
			halves.add(savedSecondary);
		}
		for (BlockPos half : halves) {
			BlockState state = client.level.getBlockState(half);
			if (state.is(Blocks.HOPPER)) {
				// a hopper pushes into what it faces and pulls from what is above it
				String target = containerNameAt(client, half.relative(state.getValue(HopperBlock.FACING)));
				movedTo = movedTo == null ? target : movedTo;
				String source = containerNameAt(client, half.above());
				if (source != null) {
					feeder = true;
					feederSource = feederSource == null ? source : feederSource;
				}
			}
			if (hopperBelow(client, half)) {
				hopperBelow = true;
				BlockState below = client.level.getBlockState(half.below());
				if (below.is(Blocks.HOPPER)) {
					BlockPos target = half.below().relative(below.getValue(HopperBlock.FACING));
					String name = halves.contains(target) ? null : containerNameAt(client, target);
					movedTo = movedTo == null ? name : movedTo;
				}
			}
			String source = hasFeeder(client, half, halves);
			if (source != null) {
				feeder = true;
				if (!source.isEmpty()) {
					feederSource = feederSource == null ? source : feederSource;
				}
			}
		}
		if (removed && movedTo != null) {
			notice.hints.add(new Hint("screen.noobs_container_searcher.changes_hint_moved_to", movedTo));
		} else if (removed && hopperBelow) {
			notice.hints.add(new Hint("screen.noobs_container_searcher.changes_hint_hopper_below", null));
		}
		if (added && feederSource != null) {
			notice.hints.add(new Hint("screen.noobs_container_searcher.changes_hint_moved_from", feederSource));
		} else if (added && feeder) {
			notice.hints.add(new Hint("screen.noobs_container_searcher.changes_hint_feeder", null));
		}
		if (halfLost) {
			notice.hints.add(new Hint("screen.noobs_container_searcher.changes_hint_half_lost", null));
		}
		if (removed && movedTo == null && !hopperBelow && !halfLost && notice.events.isEmpty()) {
			notice.hints.add(new Hint("screen.noobs_container_searcher.changes_hint_unknown", null));
		}

		if (notice.deltas.isEmpty() && notice.events.isEmpty() && notice.hints.isEmpty()) {
			return;
		}
		active = notice;
	}

	public static void clear() {
		active = null;
		scrollPos = 0.0F;
		scrollTarget = 0.0F;
		maxScroll = 0.0F;
		panelHeight = 0;
	}

	private static List<BlockPos> halves(BlockPos pos, BlockPos duplicate) {
		List<BlockPos> list = new ArrayList<>(2);
		list.add(pos);
		if (duplicate != null) {
			list.add(duplicate);
		}
		return list;
	}

	// a hopper, dropper or dispenser that points into the container
	// null when nothing feeds the container, otherwise the name of what feeds it ("" when that is unknown)
	private static String hasFeeder(Minecraft client, BlockPos half, List<BlockPos> halves) {
		if (!client.level.getEntitiesOfClass(MinecartHopper.class, new AABB(half.above())).isEmpty()) {
			return "";
		}
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = half.relative(direction);
			if (halves.contains(neighbor)) {
				continue;
			}
			BlockState state = client.level.getBlockState(neighbor);
			if (state.is(Blocks.HOPPER) && state.getValue(HopperBlock.FACING) == direction.getOpposite()) {
				// the hopper takes from the container above it
				String source = containerNameAt(client, neighbor.above());
				return source == null ? "" : source;
			}
			if ((state.is(Blocks.DROPPER) || state.is(Blocks.DISPENSER))
				&& state.getValue(DispenserBlock.FACING) == direction.getOpposite()) {
				return state.getBlock().getName().getString();
			}
		}
		return null;
	}

	private static String containerNameAt(Minecraft client, BlockPos pos) {
		if (!(client.level.getBlockEntity(pos) instanceof Container)) {
			return null;
		}
		return client.level.getBlockState(pos).getBlock().getName().getString();
	}

	// a hopper block or a hopper minecart right under the container
	private static boolean hopperBelow(Minecraft client, BlockPos half) {
		BlockPos below = half.below();
		return client.level.getBlockState(below).is(Blocks.HOPPER)
			|| !client.level.getEntitiesOfClass(MinecartHopper.class, new AABB(below)).isEmpty();
	}

	private static ContainerItemRecord sampleOf(List<ContainerItemRecord> entries, String itemId) {
		if (entries == null) {
			return null;
		}
		for (ContainerItemRecord entry : entries) {
			if (itemId.equals(entry.itemId)) {
				return entry;
			}
		}
		return null;
	}

	// ---- drawing ----

	// called for every slot we draw, only the first container slot actually paints the panel
	public static void renderPanel(Gfx graphics, Slot slot) {
		Notice notice = active;
		if (notice == null || slot.container instanceof Inventory || !ModSettings.trackChangesEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (!(Compat.screen(client) instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		for (Slot candidate : menu.slots) {
			if (!(candidate.container instanceof Inventory)) {
				if (candidate != slot) {
					return;
				}
				break;
			}
		}

		Font font = client.font;
		List<Row> all = buildRows(client, font, notice);
		List<Row> rows = new ArrayList<>();
		List<Row> pinned = new ArrayList<>();
		for (Row row : all) {
			(row.pinned ? pinned : rows).add(row);
		}
		int pinnedHeight = 0;
		for (Row row : pinned) {
			pinnedHeight += row.height + ROW_GAP;
		}

		int guiWidth = client.getWindow().getGuiScaledWidth();
		int guiHeight = client.getWindow().getGuiScaledHeight();
		int imageHeight = 0;
		for (Slot candidate : menu.slots) {
			imageHeight = Math.max(imageHeight, candidate.y + 16 + 7);
		}
		int screenLeft = (guiWidth - 176) / 2;
		int screenTop = (guiHeight - imageHeight) / 2;
		// the panel never grows taller than the chest screen, the rest is reached with the mouse wheel
		int available = Math.max(HEADER_HEIGHT + PAD + 30, Math.min(imageHeight, guiHeight - screenTop - 4));

		int contentHeight = PAD;
		for (Row row : rows) {
			contentHeight += row.height + ROW_GAP;
		}
		int viewport = Math.min(contentHeight, Math.max(30, available - HEADER_HEIGHT - pinnedHeight));
		int shown = rows.size();
		if (!Gfx.CLIPS && contentHeight > viewport) {
			// no clipping on this version: show only the rows that fit completely
			shown = 0;
			int used = PAD;
			while (shown < rows.size() && used + rows.get(shown).height + ROW_GAP <= viewport) {
				used += rows.get(shown).height + ROW_GAP;
				shown++;
			}
			viewport = used;
			contentHeight = used;
		}
		maxScroll = Gfx.CLIPS ? Math.max(0, contentHeight - viewport) : 0;
		long nowMs = System.currentTimeMillis();
		float dt = lastFrame == 0L ? 16.0F : Math.min(100L, nowMs - lastFrame);
		lastFrame = nowMs;
		scrollTarget = Math.max(0.0F, Math.min(maxScroll, scrollTarget));
		scrollPos += (scrollTarget - scrollPos) * (1.0F - (float) Math.exp(-dt / 55.0F));
		if (Math.abs(scrollTarget - scrollPos) < 0.3F) {
			scrollPos = scrollTarget;
		}
		int height = HEADER_HEIGHT + viewport + pinnedHeight;

		// always left of the chest, on a small window it runs off the screen instead of covering the chest
		int x = -(PANEL_WIDTH + 6);
		int y = 0;
		panelLeft = screenLeft + x;
		panelTop = screenTop;
		panelHeight = height;
		graphics.fill(x, y, x + PANEL_WIDTH, y + height, PANEL_BG);
		graphics.outline(x, y, PANEL_WIDTH, height, PANEL_BORDER);

		graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + HEADER_HEIGHT, HEADER_BG);
		graphics.fill(x + 1, y + HEADER_HEIGHT, x + PANEL_WIDTH - 1, y + HEADER_HEIGHT + 1, ACCENT);
		graphics.icon("icon_history", x + 6, y + 6, 16);
		graphics.text(font, font.plainSubstrByWidth(Component.translatable("screen.noobs_container_searcher.changes_title").getString(), PANEL_WIDTH - 32),
			x + 26, y + 6, TEXT_TITLE);
		graphics.text(font, font.plainSubstrByWidth(Component.translatable("screen.noobs_container_searcher.changes_last_seen", ago(System.currentTimeMillis() - notice.previousAt)).getString(), PANEL_WIDTH - 32),
			x + 26, y + 17, TEXT_MUTED);

		int contentTop = y + HEADER_HEIGHT + 1;
		int contentBottom = contentTop + viewport;
		boolean clip = maxScroll > 0.0F;
		if (clip) {
			graphics.enableScissor(x + 1, contentTop, x + PANEL_WIDTH - 1, contentBottom);
		}
		int cy = contentTop + PAD - 1 - Math.round(scrollPos);
		int cardLeft = x + PAD;
		int cardRight = x + PANEL_WIDTH - PAD;
		for (int i = 0; i < shown; i++) {
			Row row = rows.get(i);
			if (cy + row.height >= contentTop && cy <= contentBottom) {
				drawRow(graphics, font, row, cardLeft, cardRight, cy);
			}
			cy += row.height + ROW_GAP;
		}
		if (clip) {
			graphics.disableScissor();
			// slim bar on the right edge so it is obvious the list can be scrolled
			int trackTop = contentTop + 3;
			int trackBottom = contentBottom - 3;
			int trackX = x + PANEL_WIDTH - PAD + 1;
			int trackHeight = trackBottom - trackTop;
			int thumbHeight = Math.max(12, trackHeight * viewport / contentHeight);
			int thumbTop = trackTop + Math.round((trackHeight - thumbHeight) * scrollPos / maxScroll);
			graphics.fill(trackX, trackTop, trackX + 3, trackBottom, BG_CARD);
			graphics.fill(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, TEXT_SECONDARY);
		}

		int py = contentBottom;
		for (Row row : pinned) {
			drawRow(graphics, font, row, cardLeft, cardRight, py);
			py += row.height + ROW_GAP;
		}
	}

	private static void drawRow(Gfx graphics, Font font, Row row, int cardLeft, int cardRight, int cy) {
		graphics.fill(cardLeft, cy, cardRight, cy + row.height, row.bg);
		int iconY = cy + (row.height - 16) / 2;
		if (row.icon != null) {
			graphics.fakeItem(row.icon, cardLeft + 4, iconY);
		}
		int textX = cardLeft + 4 + 20;
		int textRight = cardRight - 5;
		if (row.amount != null) {
			int amountWidth = font.width(row.amount);
			graphics.text(font, row.amount, textRight - amountWidth, cy + (row.height - 8) / 2, row.amountColor);
			textRight -= amountWidth + 5;
		}
		int textY = cy + (row.height - row.lines.size() * LINE_HEIGHT + 1) / 2;
		for (String line : row.lines) {
			graphics.text(font, font.plainSubstrByWidth(line, textRight - textX), textX, textY, row.textColor);
			textY += LINE_HEIGHT;
		}
	}

	// mouse wheel over the panel scrolls it, returns true when the scroll was used
	public static boolean scroll(double mouseX, double mouseY, double amount) {
		if (active == null || maxScroll <= 0.0F || amount == 0.0D) {
			return false;
		}
		if (mouseX < panelLeft || mouseX > panelLeft + PANEL_WIDTH || mouseY < panelTop || mouseY > panelTop + panelHeight) {
			return false;
		}
		scrollTarget = Math.max(0.0F, Math.min(maxScroll, scrollTarget - (float) amount * 22.0F));
		return true;
	}

	private static final class Row {
		final int stripe;
		final int bg;
		final ItemStack icon;
		final List<String> lines;
		final int textColor;
		final String amount;
		final int amountColor;
		final int height;
		// events and hints stay at the bottom of the panel while the item changes scroll
		boolean pinned;

		Row(int stripe, int bg, ItemStack icon, List<String> lines, int textColor, String amount, int amountColor) {
			this.stripe = stripe;
			this.bg = bg;
			this.icon = icon;
			this.lines = lines;
			this.textColor = textColor;
			this.amount = amount;
			this.amountColor = amountColor;
			this.height = Math.max(20, lines.size() * LINE_HEIGHT + 8);
		}
	}

	private static List<Row> buildRows(Minecraft client, Font font, Notice notice) {
		List<Row> rows = new ArrayList<>();
		int textWidth = PANEL_WIDTH - PAD * 2 - 7 - 20 - 5;

		int shown = notice.deltas.size();
		for (int i = 0; i < shown; i++) {
			Delta delta = notice.deltas.get(i);
			if (delta.icon == null && delta.sample != null) {
				delta.icon = decode(client, delta.sample);
			}
			boolean gain = delta.change > 0;
			rows.add(new Row(gain ? TEXT_GAIN : TEXT_LOSS, gain ? BG_GAIN : BG_LOSS,
				delta.icon != null && !delta.icon.isEmpty() ? delta.icon : new ItemStack(Items.BARRIER),
				List.of(delta.name), TEXT_PRIMARY, (gain ? "+" : "-") + Math.abs(delta.change), gain ? TEXT_GAIN : TEXT_LOSS));
		}
		if (notice.deltas.isEmpty()) {
			rows.add(new Row(ACCENT, BG_CARD, new ItemStack(Items.CHEST),
				List.of(Component.translatable("screen.noobs_container_searcher.changes_none").getString()),
				TEXT_MUTED, null, 0));
		}

		int firstEvent = Math.max(0, notice.events.size() - MAX_PINNED_EVENTS);
		for (ContainerEvent event : notice.events.subList(firstEvent, notice.events.size())) {
			String when = ago(System.currentTimeMillis() - event.time);
			String who = event.players == null || event.players.isEmpty()
				? Component.translatable("screen.noobs_container_searcher.changes_someone").getString()
				: String.join(", ", event.players);
			rows.add(new Row(event.broke ? TEXT_LOSS : TEXT_EVENT, BG_CARD, new ItemStack(event.broke ? Items.IRON_PICKAXE : Items.PLAYER_HEAD),
				wrap(font, Component.translatable(event.broke ? "screen.noobs_container_searcher.changes_broke" : "screen.noobs_container_searcher.changes_opened", who, when).getString(), textWidth, 2),
				TEXT_PRIMARY, null, 0));
			rows.get(rows.size() - 1).pinned = true;
		}
		for (Hint hint : notice.hints) {
			ItemStack icon = new ItemStack(hint.key.endsWith("feeder") || hint.key.endsWith("moved_from") ? Items.DROPPER
				: hint.key.endsWith("hopper_below") || hint.key.endsWith("moved_to") ? Items.HOPPER
				: hint.key.endsWith("half_lost") ? Items.CHEST
				: Items.COMPASS);
			rows.add(new Row(TEXT_HINT, BG_CARD, icon,
				wrap(font, (hint.arg == null ? Component.translatable(hint.key) : Component.translatable(hint.key, hint.arg)).getString(), textWidth, 3), TEXT_SECONDARY, null, 0));
			rows.get(rows.size() - 1).pinned = true;
		}
		return rows;
	}

	private static List<String> wrap(Font font, String text, int width, int maxLines) {
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = current.length() == 0 ? word : current + " " + word;
			if (current.length() > 0 && font.width(candidate) > width) {
				lines.add(current.toString());
				current = new StringBuilder(word);
			} else {
				current = new StringBuilder(candidate);
			}
		}
		if (current.length() > 0) {
			lines.add(current.toString());
		}
		if (lines.size() > maxLines) {
			lines = new ArrayList<>(lines.subList(0, maxLines));
		}
		return lines;
	}

	private static String ago(long millis) {
		long minutes = Math.max(0L, millis) / 60_000L;
		if (minutes < 1L) {
			long seconds = Math.max(0L, millis) / 1000L;
			return seconds < 10L
				? Component.translatable("screen.noobs_container_searcher.changes_ago_now").getString()
				: Component.translatable("screen.noobs_container_searcher.changes_ago_sec", seconds).getString();
		}
		if (minutes < 60L) {
			return Component.translatable("screen.noobs_container_searcher.changes_ago_min", minutes).getString();
		}
		long hours = minutes / 60L;
		if (hours < 48L) {
			return Component.translatable("screen.noobs_container_searcher.changes_ago_hour", hours).getString();
		}
		return Component.translatable("screen.noobs_container_searcher.changes_ago_day", hours / 24L).getString();
	}

	private static ItemStack decode(Minecraft client, ContainerItemRecord record) {
		if (record.stackData == null || record.stackData.isBlank() || client.level == null) {
			return ItemStack.EMPTY;
		}
		try {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess());
			Tag tag = TagParser.parseTag(record.stackData);
			return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
		} catch (Exception ignored) {
			return ItemStack.EMPTY;
		}
	}
}
