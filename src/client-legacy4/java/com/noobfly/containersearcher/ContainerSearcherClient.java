package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.Highlights;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ContainerSearcherClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("noobs-container-searcher");
	private static final ContainerDatabase DATABASE = new ContainerDatabase();
	private static final HighlightManager HIGHLIGHTS = new HighlightManager();
	private static final SearchController SEARCH = new SearchController(DATABASE, HIGHLIGHTS);
	private static final LibrarianRerollController LIBRARIAN_REROLL = new LibrarianRerollController();

	private static final int STALE_CONFIRMATIONS = 3;
	private static final int VILLAGER_STALE_CONFIRMATIONS = 5;
	private static final double VERIFY_RADIUS = 48.0D;
	private static final double VILLAGER_VERIFY_RADIUS = 32.0D;
	// Dünyaya girdikten sonra chunkler ve varlıklar yerleşene kadar hiçbir kayıt silinmez.
	private static final long WORLD_SETTLE_MS = 15_000L;
	private static final double LOOK_REACH = 6.0D;

	private static final Map<String, Integer> STALE_STRIKES = new HashMap<>();

	private static BlockPos pendingContainerPos;
	private static BlockPos pendingDuplicatePos;
	private static BlockPos openContainerPos;
	private static BlockPos openDuplicatePos;
	private static long pendingContainerExpiresAt;
	private static BlockPos lookedContainerPos;
	private static BlockPos lookedDuplicatePos;
	private static long lookedContainerExpiresAt;
	private static UUID pendingVillagerUuid;
	private static long pendingVillagerExpiresAt;
	private static int lastCapturedContainerId = -1;
	private static int lastCapturedFingerprint;
	private static int staleRecordCheckTicks;
	private static ClientLevel trackedLevel;
	private static long verifyReadyAt;

	@Override
	public void onInitializeClient() {
		DATABASE.load();
		SEARCH.register();

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (LIBRARIAN_REROLL.handleBlockUse(hitResult)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos clickedPos = hitResult.getBlockPos().immutable();
			if (isContainerBlock(world, clickedPos)) {
				markPendingContainer(world, clickedPos);
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (LIBRARIAN_REROLL.handleEntityUse(entity)) {
				return InteractionResult.SUCCESS;
			}
			if (entity instanceof Villager) {
				pendingVillagerUuid = entity.getUUID();
				pendingVillagerExpiresAt = System.currentTimeMillis() + 5_000L;
			} else {
				markContainerBehindEntity(player, world, entity);
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			HIGHLIGHTS.tick(client);
			trackLookedContainer(client);
			captureOpenScreen(client);
			removeStaleRecordsInLoadedChunks(client);
			SEARCH.tick(client);
			LIBRARIAN_REROLL.tick(client);
		});
		Highlights.register(HIGHLIGHTS);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenEvents.remove(screen).register(removedScreen -> {
					SearchController.clearActiveItem();
					openContainerPos = null;
					openDuplicatePos = null;
					pendingVillagerUuid = null;
				});
			}
		});
	}

	private static void removeStaleRecordsInLoadedChunks(Minecraft client) {
		if (!trackLevel(client) || ++staleRecordCheckTicks < 20) {
			return;
		}
		staleRecordCheckTicks = 0;
		// Dünyaya/boyuta yeni girildiyse chunkler ve varlıklar tam ulaşmadan silme yapma.
		if (System.currentTimeMillis() < verifyReadyAt) {
			return;
		}

		String server = serverKey(client);
		String dimension = dimensionKey(client);
		List<ContainerRecord> staleRecords = new ArrayList<>();
		Set<String> seenKeys = new HashSet<>();
		for (ContainerRecord record : DATABASE.allForServer(server)) {
			String key = ContainerDatabase.keyOf(record);
			seenKeys.add(key);
			if (!dimension.equals(record.dimension)) {
				STALE_STRIKES.remove(key);
				continue;
			}

			Boolean missing = record.entityUuid != null && !record.entityUuid.isBlank()
				? isVillagerMissing(client, record)
				: isContainerMissing(client, record);
			// null: şu an güvenilir bir karar verilemiyor (uzak, chunk yüklü değil vb.)
			if (missing == null || !missing) {
				STALE_STRIKES.remove(key);
				continue;
			}

			int strikes = STALE_STRIKES.merge(key, 1, Integer::sum);
			int required = record.entityUuid != null && !record.entityUuid.isBlank()
				? VILLAGER_STALE_CONFIRMATIONS
				: STALE_CONFIRMATIONS;
			if (strikes >= required) {
				STALE_STRIKES.remove(key);
				staleRecords.add(record);
			}
		}
		STALE_STRIKES.keySet().retainAll(seenKeys);

		if (staleRecords.isEmpty()) {
			DATABASE.flush();
			return;
		}
		DATABASE.removeAll(staleRecords);
		for (ContainerRecord record : staleRecords) {
			if (record.entityUuid != null) {
				HIGHLIGHTS.removeEntity(record.entityUuid);
			}
			HIGHLIGHTS.removeAt(new BlockPos(record.x, record.y, record.z));
			BlockPos secondaryPos = secondaryPos(record);
			if (secondaryPos != null) {
				HIGHLIGHTS.removeAt(secondaryPos);
			}
		}
		LOGGER.debug("Yuklu chunklarda artik bulunmayan {} konteyner kaydi silindi", staleRecords.size());
	}

	private static boolean trackLevel(Minecraft client) {
		if (client.level != trackedLevel) {
			DATABASE.flushNow();
			trackedLevel = client.level;
			STALE_STRIKES.clear();
			verifyReadyAt = System.currentTimeMillis() + WORLD_SETTLE_MS;
		}
		return client.level != null && client.player != null;
	}

	private static Boolean isVillagerMissing(Minecraft client, ContainerRecord record) {
		Villager villager = findVillager(client, record.entityUuid);
		if (villager != null) {
			String profession = professionId(villager);
			if (!java.util.Objects.equals(record.villagerProfession, profession)) {
				return true;
			}
			BlockPos currentPos = villager.blockPosition();
			if (record.x != currentPos.getX() || record.y != currentPos.getY() || record.z != currentPos.getZ()) {
				record.x = currentPos.getX();
				record.y = currentPos.getY();
				record.z = currentPos.getZ();
				// Köylüler sürekli hareket eder; her adımda diske yazma.
				DATABASE.putDeferred(record);
			}
			return false;
		}

		BlockPos pos = new BlockPos(record.x, record.y, record.z);
		if (!client.level.hasChunk(record.x >> 4, record.z >> 4)
			|| client.player.blockPosition().distSqr(pos) > VILLAGER_VERIFY_RADIUS * VILLAGER_VERIFY_RADIUS) {
			return null;
		}
		return true;
	}

	private static Boolean isContainerMissing(Minecraft client, ContainerRecord record) {
		if (record.containerType == null || record.containerType.isBlank()) {
			return null;
		}

		BlockPos primaryPos = new BlockPos(record.x, record.y, record.z);
		if (!isVerifiable(client, primaryPos)) {
			return null;
		}
		if (!record.containerType.equals(blockId(client, primaryPos))) {
			return true;
		}

		BlockPos secondaryPos = secondaryPos(record);
		if (secondaryPos == null) {
			return false;
		}
		// Çift sandık chunk sınırını geçebilir; iki yarısı da doğrulanabilir olmadan karar verme.
		if (!isVerifiable(client, secondaryPos)) {
			return null;
		}
		BlockState primaryState = client.level.getBlockState(primaryPos);
		BlockPos currentConnection = connectedChestPos(primaryPos, primaryState);
		return !record.containerType.equals(blockId(client, secondaryPos))
			|| !secondaryPos.equals(currentConnection);
	}

	private static boolean isVerifiable(Minecraft client, BlockPos pos) {
		return client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
			&& client.player.blockPosition().distSqr(pos) <= VERIFY_RADIUS * VERIFY_RADIUS;
	}

	private static String blockId(Minecraft client, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString();
	}

	private static BlockPos secondaryPos(ContainerRecord record) {
		if (record.secondaryX == null || record.secondaryY == null || record.secondaryZ == null) {
			return null;
		}
		return new BlockPos(record.secondaryX, record.secondaryY, record.secondaryZ);
	}

	private static boolean isContainerBlock(Level level, BlockPos pos) {
		if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
			return false;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof MenuProvider) {
			return true;
		}
		return blockEntity != null && level.getBlockState(pos).getMenuProvider(level, pos) != null;
	}

	private static void markPendingContainer(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		pendingContainerPos = canonicalContainerPos(pos, state);
		pendingDuplicatePos = pendingContainerPos.equals(pos) ? connectedChestPos(pos, state) : pos;
		pendingContainerExpiresAt = System.currentTimeMillis() + 5_000L;
	}

	private static void markContainerBehindEntity(Entity player, Level level, Entity entity) {
		if (!level.isClientSide()) {
			// Tek oyunculuda olay sunucu tarafında da tetiklenir; istemci durumunu bozma.
			return;
		}
		BlockPos looked = pickedContainerPos(player, level);
		if (looked != null) {
			markPendingContainer(level, looked);
			return;
		}
		if (entity instanceof BlockAttachedEntity attached) {
			BlockPos support = attached.getPos().relative(entity.getDirection().getOpposite());
			if (isContainerBlock(level, support)) {
				markPendingContainer(level, support);
			} else if (isContainerBlock(level, attached.getPos())) {
				markPendingContainer(level, attached.getPos());
			}
		}
	}

	private static void trackLookedContainer(Minecraft client) {
		if (client.player == null || client.level == null || Compat.screen(client) != null) {
			return;
		}
		BlockPos pos = pickedContainerPos(client.player, client.level);
		if (pos == null) {
			return;
		}
		BlockState state = client.level.getBlockState(pos);
		lookedContainerPos = canonicalContainerPos(pos, state);
		lookedDuplicatePos = lookedContainerPos.equals(pos) ? connectedChestPos(pos, state) : pos;
		lookedContainerExpiresAt = System.currentTimeMillis() + 5_000L;
	}

	private static boolean matchesContainerSize(
		Minecraft client,
		AbstractContainerMenu menu,
		BlockPos primaryPos,
		BlockPos secondaryPos
	) {
		int expected = containerSize(client, primaryPos);
		if (expected <= 0) {
			return true;
		}
		if (secondaryPos != null) {
			int secondarySize = containerSize(client, secondaryPos);
			if (secondarySize > 0) {
				expected += secondarySize;
			}
		}

		int slots = 0;
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				slots++;
			}
		}
		return slots == expected;
	}

	private static boolean matchesContainerTitle(Minecraft client, AbstractContainerScreen<?> screen, BlockPos pos) {
		BlockState state = client.level.getBlockState(pos);
		MenuProvider provider = state.getMenuProvider(client.level, pos);
		if (provider == null) {
			return true;
		}
		return provider.getDisplayName().getString().equals(screen.getTitle().getString());
	}

	private static int containerSize(Minecraft client, BlockPos pos) {
		return client.level.getBlockEntity(pos) instanceof Container container ? container.getContainerSize() : 0;
	}

	private static BlockPos pickedContainerPos(Entity viewer, Level level) {
		HitResult hit = viewer.pick(LOOK_REACH, 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = blockHit.getBlockPos().immutable();
		return isContainerBlock(level, pos) ? pos : null;
	}

	private void captureOpenScreen(Minecraft client) {
		if (Compat.screen(client) instanceof MerchantScreen screen) {
			captureOpenVillager(client, screen.getMenu());
			return;
		}
		captureOpenContainer(client);
	}

	private void captureOpenContainer(Minecraft client) {
		if (!(Compat.screen(client) instanceof AbstractContainerScreen<?> screen)
			|| client.player == null
			|| client.level == null) {
			lastCapturedContainerId = -1;
			lastCapturedFingerprint = 0;
			openContainerPos = null;
			openDuplicatePos = null;
			if (System.currentTimeMillis() > pendingContainerExpiresAt) {
				pendingContainerPos = null;
				pendingDuplicatePos = null;
			}
			return;
		}

		AbstractContainerMenu menu = screen.getMenu();
		if (menu == client.player.inventoryMenu) {
			// Oyuncu envanteri; hiçbir bloğa ait değil.
			return;
		}
		long now = System.currentTimeMillis();
		if (openContainerPos == null && pendingContainerPos != null && now <= pendingContainerExpiresAt) {
			openContainerPos = pendingContainerPos;
			openDuplicatePos = pendingDuplicatePos;
			pendingContainerPos = null;
			pendingDuplicatePos = null;
		}
		if (openContainerPos == null && lookedContainerPos != null && now <= lookedContainerExpiresAt) {
			if (!matchesContainerSize(client, menu, lookedContainerPos, lookedDuplicatePos)
				|| !matchesContainerTitle(client, screen, lookedContainerPos)) {
				return;
			}
			openContainerPos = lookedContainerPos;
			openDuplicatePos = lookedDuplicatePos;
			lookedContainerPos = null;
			lookedDuplicatePos = null;
		}
		if (openContainerPos == null) {
			return;
		}

		Map<String, Integer> contents = new LinkedHashMap<>();
		List<ContainerItemRecord> entries = new ArrayList<>();
		boolean hasContainerSlots = false;
		int fingerprint = 1;
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory) {
				continue;
			}
			hasContainerSlots = true;
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				contents.merge(itemId, stack.getCount(), Integer::sum);
				entries.add(itemRecord(client, stack, itemId));
				fingerprint = 31 * fingerprint + itemId.hashCode();
				fingerprint = 31 * fingerprint + stack.getCount();
			} else {
				fingerprint *= 31;
			}
		}
		if (!hasContainerSlots) {
			return;
		}
		HIGHLIGHTS.removeAt(openContainerPos);
		if (openDuplicatePos != null) {
			HIGHLIGHTS.removeAt(openDuplicatePos);
		}
		if (menu.containerId == lastCapturedContainerId && fingerprint == lastCapturedFingerprint) {
			return;
		}

		ContainerRecord record = new ContainerRecord();
		record.server = serverKey(client);
		record.dimension = dimensionKey(client);
		record.x = openContainerPos.getX();
		record.y = openContainerPos.getY();
		record.z = openContainerPos.getZ();
		if (openDuplicatePos != null) {
			record.secondaryX = openDuplicatePos.getX();
			record.secondaryY = openDuplicatePos.getY();
			record.secondaryZ = openDuplicatePos.getZ();
		}
		record.containerType = BuiltInRegistries.BLOCK.getKey(
			client.level.getBlockState(openContainerPos).getBlock()
		).toString();
		record.updatedAt = System.currentTimeMillis();
		record.items = contents;
		record.entries = entries;
		DATABASE.put(record, openDuplicatePos);
		lastCapturedContainerId = menu.containerId;
		lastCapturedFingerprint = fingerprint;
		LOGGER.debug("{} konumundaki konteyner kaydedildi", openContainerPos);
	}

	private void captureOpenVillager(Minecraft client, MerchantMenu menu) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (pendingVillagerUuid == null || System.currentTimeMillis() > pendingVillagerExpiresAt) {
			pendingVillagerUuid = null;
			return;
		}
		Villager villager = findVillager(client, pendingVillagerUuid.toString());
		if (villager == null) {
			return;
		}
		String profession = professionId(villager);
		if (profession == null) {
			return;
		}

		Map<String, Integer> contents = new LinkedHashMap<>();
		List<ContainerItemRecord> entries = new ArrayList<>();
		int fingerprint = profession.hashCode();
		for (MerchantOffer offer : menu.getOffers()) {
			ItemStack result = offer.getResult();
			if (result.isEmpty()) {
				continue;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
			contents.merge(itemId, result.getCount(), Integer::sum);
			entries.add(itemRecord(client, result, itemId));
			fingerprint = 31 * fingerprint + result.hashCode();
		}
		if (entries.isEmpty() || menu.containerId == lastCapturedContainerId && fingerprint == lastCapturedFingerprint) {
			return;
		}

		BlockPos pos = villager.blockPosition();
		ContainerRecord record = new ContainerRecord();
		record.server = serverKey(client);
		record.dimension = dimensionKey(client);
		record.x = pos.getX();
		record.y = pos.getY();
		record.z = pos.getZ();
		record.entityUuid = villager.getStringUUID();
		record.villagerProfession = profession;
		record.containerType = "villager:" + profession;
		record.updatedAt = System.currentTimeMillis();
		record.items = contents;
		record.entries = entries;
		DATABASE.put(record);
		lastCapturedContainerId = menu.containerId;
		lastCapturedFingerprint = fingerprint;
		LOGGER.debug("{} meslekli {} köylüsünün takasları kaydedildi", profession, record.entityUuid);
	}

	private static Villager findVillager(Minecraft client, String uuidText) {
		if (client.level == null || uuidText == null) {
			return null;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Villager villager && uuidText.equals(villager.getStringUUID())) {
				return villager;
			}
		}
		return null;
	}

	private static String professionId(Villager villager) {
		VillagerProfession profession = villager.getVillagerData().getProfession();
		if (VillagerProfession.NONE.equals(profession)) {
			return null;
		}
		ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
		return id == null ? null : id.toString();
	}

	private static ContainerItemRecord itemRecord(Minecraft client, ItemStack stack, String itemId) {
		ContainerItemRecord record = new ContainerItemRecord();
		record.itemId = itemId;
		record.stackData = encodeStack(client, stack);
		record.count = stack.getCount();
		record.name = stack.getHoverName().getString();
		record.enchanted = stack.isEnchanted();
		record.damageable = stack.isDamageableItem();
		record.damage = stack.getDamageValue();
		record.maxDamage = stack.getMaxDamage();
		StringBuilder searchText = new StringBuilder(itemId).append(' ').append(record.name);
		for (Component line : net.minecraft.client.gui.screens.Screen.getTooltipFromItem(client, stack)) {
			String text = line.getString();
			record.tooltipLines.add(text);
			searchText.append(' ').append(text);
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				String text = line.getString();
				record.loreLines.add(text);
				searchText.append(' ').append(text);
			}
		}
		addEnchantments(record, searchText, stack.get(DataComponents.ENCHANTMENTS));
		addEnchantments(record, searchText, stack.get(DataComponents.STORED_ENCHANTMENTS));
		record.searchText = searchText.toString().toLowerCase(Locale.ROOT);
		return record;
	}

	private static String encodeStack(Minecraft client, ItemStack stack) {
		if (client.level == null) {
			return null;
		}
		return ItemStack.CODEC
			.encodeStart(RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess()), stack)
			.result()
			.map(Tag::toString)
			.orElse(null);
	}

	private static void addEnchantments(ContainerItemRecord record, StringBuilder searchText, ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			String id = enchantment.unwrapKey()
				.map(key -> key.location().toString())
				.orElse(enchantment.getRegisteredName());
			String name = Enchantment.getFullname(enchantment, entry.getIntValue()).getString();
			record.enchantments.add(id);
			record.enchantmentNames.add(name);
			searchText.append(' ').append(id).append(' ').append(name);
		}
	}

	private static BlockPos canonicalContainerPos(BlockPos clickedPos, BlockState state) {
		BlockPos connected = connectedChestPos(clickedPos, state);
		if (connected == null) {
			return clickedPos;
		}
		return comparePositions(clickedPos, connected) <= 0 ? clickedPos : connected;
	}

	private static BlockPos connectedChestPos(BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
			return null;
		}
		return pos.relative(ChestBlock.getConnectedDirection(state)).immutable();
	}

	private static int comparePositions(BlockPos left, BlockPos right) {
		int x = Integer.compare(left.getX(), right.getX());
		if (x != 0) {
			return x;
		}
		int y = Integer.compare(left.getY(), right.getY());
		return y != 0 ? y : Integer.compare(left.getZ(), right.getZ());
	}

	static String dimensionKey(Minecraft client) {
		return client.level.dimension().location().toString();
	}

	static String serverKey(Minecraft client) {
		if (client.getCurrentServer() != null) {
			return "multiplayer:" + client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		}
		if (client.getSingleplayerServer() != null) {
			return "singleplayer:" + client.getSingleplayerServer().getWorldData().getLevelName();
		}
		return "singleplayer:unknown";
	}

	public static boolean isVillagerHighlighted(Entity entity) {
		return entity instanceof Villager && HIGHLIGHTS.isVillagerHighlighted(entity.getStringUUID());
	}

	static LibrarianRerollController librarianReroll() {
		return LIBRARIAN_REROLL;
	}

	static void prepareVillagerCapture(UUID villagerUuid) {
		pendingVillagerUuid = villagerUuid;
		pendingVillagerExpiresAt = System.currentTimeMillis() + 5_000L;
	}

	public static void onKeyboardInput(int key, int scancode, int action, int mods) {
		LIBRARIAN_REROLL.onKeyboardInput(key, scancode, action, mods);
	}
}
