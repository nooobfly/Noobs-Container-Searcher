package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;
import java.util.List;

public final class LibrarianRerollController {
	private static final long PROFESSION_TIMEOUT_MILLIS = 1_500L;
	private static final long REPLACE_DELAY_MILLIS = 1_000L;
	private static final long BREAK_TIMEOUT_MILLIS = 6_000L;
	private static final long TRADE_OPEN_TIMEOUT_MILLIS = 3_000L;
	private static final double PICKUP_DISTANCE = 0.6D;
	private static final double PICKUP_SPEED_PER_TICK = 0.11D;
	private static final long PICKUP_MOVE_TIMEOUT_MILLIS = 1_200L;

	private State state = State.IDLE;
	private List<TargetBook> desiredBooks = List.of();
	private TargetBook matchedBook;
	private UUID villagerUuid;
	private BlockHitResult placementHit;
	private BlockPos lecternPos;
	private long deadline;
	private long lastActionAt;
	private boolean destroyStarted;
	private int originalHotbarSlot = -1;
	private int attempts;
	private Vec3 pickupOrigin;
	private Vec3 pickupDirection;

	public void beginSelection(List<TargetBook> books) {
		stop(false, null);
		desiredBooks = List.copyOf(books);
		matchedBook = null;
		state = State.SELECT_VILLAGER;
		message("message.noobs_container_searcher.reroll_select_villager");
	}

	public boolean handleEntityUse(Entity entity) {
		if (state != State.SELECT_VILLAGER || !(entity instanceof Villager villager)) {
			return false;
		}
		if (professionId(villager) != null && !isLibrarian(villager)) {
			message("message.noobs_container_searcher.reroll_wrong_profession");
			return true;
		}
		villagerUuid = villager.getUUID();
		state = State.SELECT_LECTERN_POSITION;
		message("message.noobs_container_searcher.reroll_select_block");
		return true;
	}

	public boolean handleBlockUse(BlockHitResult hitResult) {
		if (state != State.SELECT_LECTERN_POSITION) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		BlockPos target = hitResult.getBlockPos().relative(hitResult.getDirection());
		if (client.level == null || !client.level.getBlockState(target).canBeReplaced()) {
			message("message.noobs_container_searcher.reroll_block_occupied");
			return true;
		}
		placementHit = hitResult;
		lecternPos = target.immutable();
		state = State.PLACING;
		deadline = 0L;
		attempts = 0;
		message("message.noobs_container_searcher.reroll_started");
		return true;
	}

	public void tick(Minecraft client) {
		if (!state.automatic()) {
			return;
		}
		if (client.player == null || client.level == null || client.gameMode == null) {
			stop(true, "message.noobs_container_searcher.reroll_world_lost");
			return;
		}
		Villager villager = findVillager(client);
		if (villager == null || !villager.isAlive()) {
			stop(true, "message.noobs_container_searcher.reroll_villager_lost");
			return;
		}

		long now = System.currentTimeMillis();
		switch (state) {
			case PLACING -> placeLectern(client, now);
			case WAITING_FOR_PROFESSION -> waitForProfession(client, villager, now);
			case OPENING_TRADES -> openTrades(client, villager, now);
			case CHECKING_TRADES -> checkTrades(client, now);
			case BREAKING -> breakLectern(client, now);
			case PICKUP_MOVING_FORWARD -> moveForwardForPickup(client, now);
			case PICKUP_MOVING_BACK -> moveBackAfterPickup(client, now);
			case WAITING_FOR_PROFESSION_LOSS -> waitForProfessionLoss(villager, now);
			case WAITING_TO_REPLACE -> {
				if (now >= deadline) {
					state = State.PLACING;
					deadline = 0L;
				}
			}
			default -> { }
		}
	}

	private void placeLectern(Minecraft client, long now) {
		if (client.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
			state = State.WAITING_FOR_PROFESSION;
			deadline = now + PROFESSION_TIMEOUT_MILLIS;
			return;
		}
		if (!client.level.getBlockState(lecternPos).canBeReplaced()) {
			stop(true, "message.noobs_container_searcher.reroll_block_occupied");
			return;
		}
		int lecternSlot = findHotbarItem(client, Items.LECTERN);
		if (lecternSlot < 0) {
			stop(true, "message.noobs_container_searcher.reroll_no_lectern");
			return;
		}
		rememberOriginalSlot(client);
		selectSlot(client, lecternSlot);
		if (now - lastActionAt >= 250L) {
			client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, placementHit);
			client.player.swing(InteractionHand.MAIN_HAND);
			lastActionAt = now;
			if (deadline == 0L) {
				deadline = now + 1_500L;
			}
		}
		if (now >= deadline) {
			stop(true, "message.noobs_container_searcher.reroll_place_failed");
		}
	}

	private void waitForProfession(Minecraft client, Villager villager, long now) {
		if (isLibrarian(villager)) {
			state = State.OPENING_TRADES;
			deadline = now + TRADE_OPEN_TIMEOUT_MILLIS;
			lastActionAt = 0L;
			return;
		}
		if (professionId(villager) != null) {
			state = State.BREAKING;
			prepareBreaking(now);
			return;
		}
		if (now >= deadline) {
			state = State.BREAKING;
			prepareBreaking(now);
		}
	}

	private void openTrades(Minecraft client, Villager villager, long now) {
		if (Compat.screen(client) instanceof MerchantScreen) {
			state = State.CHECKING_TRADES;
			return;
		}
		if (now - lastActionAt >= 500L) {
			ContainerSearcherClient.prepareVillagerCapture(villager.getUUID());
			Compat.interact(client, villager, InteractionHand.MAIN_HAND);
			client.player.swing(InteractionHand.MAIN_HAND);
			lastActionAt = now;
		}
		if (now >= deadline) {
			stop(true, "message.noobs_container_searcher.reroll_trade_open_failed");
		}
	}

	private void checkTrades(Minecraft client, long now) {
		if (!(Compat.screen(client) instanceof MerchantScreen screen)) {
			state = State.OPENING_TRADES;
			deadline = now + TRADE_OPEN_TIMEOUT_MILLIS;
			return;
		}
		MerchantMenu menu = screen.getMenu();
		if (menu.getOffers().isEmpty()) {
			return;
		}
		for (MerchantOffer offer : menu.getOffers()) {
			TargetBook match = matchingDesiredBook(offer.getResult());
			if (match != null) {
				matchedBook = match;
				attempts++;
				succeed(client);
				return;
			}
		}
		attempts++;
		client.player.closeContainer();
		state = State.BREAKING;
		prepareBreaking(now);
	}

	private void breakLectern(Minecraft client, long now) {
		if (!client.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
			client.gameMode.stopDestroyBlock();
			destroyStarted = false;
			beginPickupMovement(client, now);
			return;
		}
		int axeSlot = findPreferredAxe(client);
		if (axeSlot < 0) {
			stop(true, "message.noobs_container_searcher.reroll_no_axe");
			return;
		}
		selectSlot(client, axeSlot);
		Direction face = placementHit == null ? Direction.UP : placementHit.getDirection();
		if (!destroyStarted) {
			destroyStarted = client.gameMode.startDestroyBlock(lecternPos, face);
		} else {
			client.gameMode.continueDestroyBlock(lecternPos, face);
		}
		client.player.swing(InteractionHand.MAIN_HAND);
		if (now >= deadline) {
			client.gameMode.stopDestroyBlock();
			stop(true, "message.noobs_container_searcher.reroll_break_failed");
		}
	}

	private void beginPickupMovement(Minecraft client, long now) {
		pickupOrigin = client.player.position();
		Vec3 look = client.player.getLookAngle();
		double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
		pickupDirection = horizontalLength < 1.0E-4D
			? new Vec3(0.0D, 0.0D, 1.0D)
			: new Vec3(look.x / horizontalLength, 0.0D, look.z / horizontalLength);
		state = State.PICKUP_MOVING_FORWARD;
		deadline = now + PICKUP_MOVE_TIMEOUT_MILLIS;
	}

	private void moveForwardForPickup(Minecraft client, long now) {
		double traveled = horizontalDistance(client.player.position(), pickupOrigin);
		if (traveled >= PICKUP_DISTANCE || now >= deadline) {
			stopHorizontalMovement(client);
			state = State.PICKUP_MOVING_BACK;
			deadline = now + PICKUP_MOVE_TIMEOUT_MILLIS;
			return;
		}
		setHorizontalVelocity(client, pickupDirection.scale(PICKUP_SPEED_PER_TICK));
	}

	private void moveBackAfterPickup(Minecraft client, long now) {
		Vec3 current = client.player.position();
		Vec3 difference = new Vec3(pickupOrigin.x - current.x, 0.0D, pickupOrigin.z - current.z);
		double distance = difference.length();
		if (distance <= 0.04D || now >= deadline) {
			stopHorizontalMovement(client);
			state = State.WAITING_FOR_PROFESSION_LOSS;
			deadline = now + BREAK_TIMEOUT_MILLIS;
			return;
		}
		double speed = Math.min(PICKUP_SPEED_PER_TICK, distance);
		setHorizontalVelocity(client, difference.normalize().scale(speed));
	}

	private static double horizontalDistance(Vec3 left, Vec3 right) {
		double x = left.x - right.x;
		double z = left.z - right.z;
		return Math.sqrt(x * x + z * z);
	}

	private static void setHorizontalVelocity(Minecraft client, Vec3 velocity) {
		Vec3 current = client.player.getDeltaMovement();
		client.player.setDeltaMovement(velocity.x, current.y, velocity.z);
	}

	private static void stopHorizontalMovement(Minecraft client) {
		if (client.player == null) {
			return;
		}
		Vec3 current = client.player.getDeltaMovement();
		client.player.setDeltaMovement(0.0D, current.y, 0.0D);
	}

	private void waitForProfessionLoss(Villager villager, long now) {
		if (professionId(villager) == null) {
			state = State.WAITING_TO_REPLACE;
			deadline = now + REPLACE_DELAY_MILLIS;
			return;
		}
		if (now >= deadline) {
			stop(true, "message.noobs_container_searcher.reroll_profession_locked");
		}
	}

	private TargetBook matchingDesiredBook(ItemStack stack) {
		if (stack.getItem() != Items.ENCHANTED_BOOK) {
			return null;
		}
		ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
		if (enchantments == null) {
			return null;
		}
		for (var entry : enchantments.entrySet()) {
			String id = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
			for (TargetBook target : desiredBooks) {
				if (target.enchantment().toString().equals(id) && entry.getIntValue() == target.level()) {
					return target;
				}
			}
		}
		return null;
	}

	private void succeed(Minecraft client) {
		state = State.SUCCESS;
		restoreOriginalSlot(client);
		message("message.noobs_container_searcher.reroll_success", desiredName(client, matchedBook), attempts);
	}

	public void onKeyboardInput(int key, int scancode, int action, int mods) {
		Minecraft client = Minecraft.getInstance();
		if (action != 1 || !state.automatic() || isAllowedDuringReroll(client, key, scancode, mods)) {
			return;
		}
		if (state.automatic()) {
			stop(true, "message.noobs_container_searcher.reroll_stopped_by_key");
		}
	}

	private static boolean isAllowedDuringReroll(Minecraft client, int key, int scancode, int mods) {
		if (key == GLFW.GLFW_KEY_ESCAPE
			|| (mods & GLFW.GLFW_MOD_ALT) != 0
			|| key == GLFW.GLFW_KEY_LEFT_ALT
			|| key == GLFW.GLFW_KEY_RIGHT_ALT
			|| Compat.screen(client) instanceof ChatScreen) {
			return true;
		}
		return client.options.keyInventory.matches(key, scancode)
			|| client.options.keyChat.matches(key, scancode)
			|| client.options.keyCommand.matches(key, scancode)
			|| client.options.keyTogglePerspective.matches(key, scancode);
	}

	public void stopByUser() {
		stop(true, "message.noobs_container_searcher.reroll_stopped");
	}

	private void stop(boolean restoreSlot, String messageKey) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode != null && client.gameMode.isDestroying()) {
			client.gameMode.stopDestroyBlock();
		}
		stopHorizontalMovement(client);
		if (restoreSlot) {
			restoreOriginalSlot(client);
		}
		state = State.IDLE;
		destroyStarted = false;
		deadline = 0L;
		if (messageKey != null) {
			message(messageKey);
		}
	}

	private void prepareBreaking(long now) {
		destroyStarted = false;
		deadline = now + BREAK_TIMEOUT_MILLIS;
	}

	private Villager findVillager(Minecraft client) {
		if (villagerUuid == null || client.level == null) {
			return null;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Villager villager && villagerUuid.equals(villager.getUUID())) {
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

	private static boolean isLibrarian(Villager villager) {
		return villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN;
	}

	private static int findHotbarItem(Minecraft client, net.minecraft.world.item.Item item) {
		for (int slot = 0; slot < 9; slot++) {
			if (client.player.getInventory().getItem(slot).getItem() == item) {
				return slot;
			}
		}
		return -1;
	}

	private static int findPreferredAxe(Minecraft client) {
		int diamondSlot = -1;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = client.player.getInventory().getItem(slot);
			if (stack.getItem() == Items.NETHERITE_AXE) {
				return slot;
			}
			if (stack.getItem() == Items.DIAMOND_AXE) {
				diamondSlot = slot;
			}
		}
		return diamondSlot;
	}

	private void rememberOriginalSlot(Minecraft client) {
		if (originalHotbarSlot < 0) {
			originalHotbarSlot = client.player.getInventory().selected;
		}
	}

	private static void selectSlot(Minecraft client, int slot) {
		if (client.player.getInventory().selected == slot) {
			return;
		}
		client.player.getInventory().selected = slot;
		client.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
	}

	private void restoreOriginalSlot(Minecraft client) {
		if (originalHotbarSlot >= 0 && client.player != null) {
			selectSlot(client, originalHotbarSlot);
		}
		originalHotbarSlot = -1;
	}

	private Component desiredName(Minecraft client, TargetBook target) {
		if (target == null) {
			return Component.literal("?");
		}
		if (client.level != null) {
			var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			var enchantment = registry.get(net.minecraft.resources.ResourceKey.create(
				net.minecraft.core.registries.Registries.ENCHANTMENT, target.enchantment()
			));
			if (enchantment.isPresent()) {
				return Enchantment.getFullname(enchantment.get(), target.level());
			}
		}
		return Component.literal(target.enchantment() + " " + target.level());
	}

	private static void message(String key, Object... args) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			Compat.message(client.player, Component.translatable(key, args));
		}
	}

	public State state() {
		return state;
	}

	public int attempts() {
		return attempts;
	}

	public record TargetBook(ResourceLocation enchantment, int level) { }

	public enum State {
		IDLE(false),
		SELECT_VILLAGER(false),
		SELECT_LECTERN_POSITION(false),
		PLACING(true),
		WAITING_FOR_PROFESSION(true),
		OPENING_TRADES(true),
		CHECKING_TRADES(true),
		BREAKING(true),
		PICKUP_MOVING_FORWARD(true),
		PICKUP_MOVING_BACK(true),
		WAITING_FOR_PROFESSION_LOSS(true),
		WAITING_TO_REPLACE(true),
		SUCCESS(false);

		private final boolean automatic;

		State(boolean automatic) {
			this.automatic = automatic;
		}

		public boolean automatic() {
			return automatic;
		}
	}
}
