package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.ClientCmd;
import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.Gfx;
import com.noobfly.containersearcher.compat.Keys;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.function.Consumer;

public final class SearchController {
	private static final SuggestionProvider<FabricClientCommandSource> ITEM_SUGGESTIONS = (context, builder) -> {
		String remaining = builder.getRemainingLowerCase();
		BuiltInRegistries.ITEM.keySet().stream()
			.map(ResourceLocation::toString)
			.filter(id -> id.startsWith(remaining) || id.substring(id.indexOf(':') + 1).startsWith(remaining))
			.forEach(builder::suggest);
		return builder.buildFuture();
	};

	private static ResourceLocation activeSearchItem;
	private static String activeFilter;

	private final ContainerDatabase database;
	private final HighlightManager highlights;
	private KeyMapping searchHeldItemKey;
	private KeyMapping searchModifierKey;
	private KeyMapping openSearchMenuKey;

	public SearchController(ContainerDatabase database, HighlightManager highlights) {
		this.database = database;
		this.highlights = highlights;
	}

	public void register() {
		searchHeldItemKey = Keys.register("key.noobs_container_searcher.search_held_item", InputConstants.KEY_R);
		searchModifierKey = Keys.register("key.noobs_container_searcher.search_modifier", InputConstants.KEY_LALT);
		openSearchMenuKey = Keys.register("key.noobs_container_searcher.open_search_menu", InputConstants.KEY_G);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(createCommand("containersearch"));
			dispatcher.register(createCommand("csr"));
		});
	}

	public void tick(Minecraft client) {
		while (searchHeldItemKey.consumeClick()) {
			if (searchModifierKey.isDown()) {
				searchHeldItem(client);
			}
		}
		while (openSearchMenuKey.consumeClick()) {
			openSearchMenu(client);
		}
	}

	public static void clearActiveItem() {
		activeSearchItem = null;
		activeFilter = null;
	}

	public static void renderMatchingSlot(Gfx graphics, Slot slot) {
		if (activeSearchItem == null) {
			return;
		}

		ItemStack stack = slot.getItem();
		if (slot.container instanceof Inventory
			|| stack.isEmpty()
			|| !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(activeSearchItem)
			|| !matchesActiveFilter(stack)) {
			return;
		}

		int pulse = (int) ((System.currentTimeMillis() / 8L) % 160L);
		int alpha = pulse < 80 ? 100 + pulse : 260 - pulse;
		int color = (alpha << 24) | 0xFFFF55;
		int x = slot.x;
		int y = slot.y;
		graphics.fill(x - 1, y - 1, x + 17, y + 1, color);
		graphics.fill(x - 1, y + 15, x + 17, y + 17, color);
		graphics.fill(x - 1, y + 1, x + 1, y + 15, color);
		graphics.fill(x + 15, y + 1, x + 17, y + 15, color);
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> createCommand(String name) {
		return ClientCmd.literal(name)
			.then(ClientCmd.literal("gui")
				.executes(context -> openSearchMenu(context.getSource())))
			.then(ClientCmd.literal("menu")
				.executes(context -> openSearchMenu(context.getSource())))
			.then(ClientCmd.literal("clearhighlight")
				.executes(context -> clearHighlights(context.getSource())))
			.then(ClientCmd.argument("item", ResourceLocationArgument.id())
				.suggests(ITEM_SUGGESTIONS)
				.executes(context -> search(context.getSource(), context.getArgument("item", ResourceLocation.class), false))
				.then(ClientCmd.literal("global")
					.executes(context -> search(context.getSource(), context.getArgument("item", ResourceLocation.class), true))));
	}

	private int search(FabricClientCommandSource source, ResourceLocation identifier, boolean global) {
		return search(identifier, global, source::sendFeedback, source::sendError);
	}

	private void searchHeldItem(Minecraft client) {
		if (client.player == null || client.level == null || Compat.screen(client) != null) {
			return;
		}

		ItemStack heldStack = client.player.getMainHandItem();
		if (heldStack.isEmpty()) {
			Compat.message(client.player, 
				Component.translatable("message.noobs_container_searcher.empty_hand")
			);
			return;
		}

		ResourceLocation identifier = BuiltInRegistries.ITEM.getKey(heldStack.getItem());
		search(
			identifier,
			false,
			message -> Compat.message(client.player, message),
			message -> Compat.message(client.player, message.copy().withStyle(ChatFormatting.RED))
		);
	}

	private int search(
		ResourceLocation identifier,
		boolean global,
		Consumer<Component> feedback,
		Consumer<Component> error
	) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			error.accept(Component.translatable("message.noobs_container_searcher.not_in_world"));
			return 0;
		}

		String itemId = identifier.toString();
		if (!BuiltInRegistries.ITEM.containsKey(identifier)) {
			error.accept(Component.translatable("message.noobs_container_searcher.unknown_item", itemId));
			return 0;
		}
		activeSearchItem = identifier;
		activeFilter = null;

		List<ContainerRecord> matches = database.search(
			ContainerSearcherClient.serverKey(client),
			ContainerSearcherClient.dimensionKey(client),
			client.player.blockPosition(),
			itemId,
			global
		);
		highlights.show(client, matches, ContainerSearcherClient.dimensionKey(client));

		if (matches.isEmpty()) {
			feedback.accept(Component.translatable(global
				? "message.noobs_container_searcher.not_found_global"
				: "message.noobs_container_searcher.not_found_nearby"));
			return 0;
		}

		feedback.accept(Component.translatable(
				"message.noobs_container_searcher.results_header",
				itemName(identifier),
				matches.size()
			)
			.withStyle(ChatFormatting.GOLD));
		for (ContainerRecord record : matches) {
			feedback.accept(resultLine(record, itemId));
		}
		return matches.size();
	}

	private int clearHighlights(FabricClientCommandSource source) {
		highlights.clear(Minecraft.getInstance());
		source.sendFeedback(Component.translatable("message.noobs_container_searcher.highlights_cleared"));
		return 1;
	}

	private int openSearchMenu(FabricClientCommandSource source) {
		return openSearchMenu(Minecraft.getInstance(), source::sendError);
	}

	private void openSearchMenu(Minecraft client) {
		openSearchMenu(client, message -> {
			if (client.player != null) {
				Compat.message(client.player, message.copy().withStyle(ChatFormatting.RED));
			}
		});
	}

	private int openSearchMenu(Minecraft client, Consumer<Component> error) {
		if (client.player == null || client.level == null) {
			error.accept(Component.translatable("message.noobs_container_searcher.not_in_world"));
			return 0;
		}
		if (Compat.screen(client) != null) {
			return 0;
		}
		Compat.setScreen(client, new ContainerSearchScreen(this, database.allForServer(ContainerSearcherClient.serverKey(client))));
		return 1;
	}

	void clearServerData(Minecraft client) {
		database.clearServer(ContainerSearcherClient.serverKey(client));
		highlights.clear(client);
	}

	void focusItem(String itemId, List<ContainerRecord> records, String filter) {
		Minecraft client = Minecraft.getInstance();
		ResourceLocation identifier = ResourceLocation.tryParse(itemId);
		if (identifier != null) {
			activeSearchItem = identifier;
		}
		activeFilter = filter;
		highlights.show(client, records, ContainerSearcherClient.dimensionKey(client));
		if (client.player == null) {
			return;
		}
		Compat.message(client.player, Component.translatable(
			"message.noobs_container_searcher.gui_selected",
			itemName(identifier),
			records.size()
		).withStyle(ChatFormatting.GOLD));
		for (ContainerRecord record : records) {
			Compat.message(client.player, resultLine(record, itemId));
		}
	}

	private static boolean matchesActiveFilter(ItemStack stack) {
		if (activeFilter == null) {
			return true;
		}
		return switch (activeFilter) {
			case "flag:enchanted" -> stack.isEnchanted() || stack.hasFoil();
			case "flag:unenchanted" -> !stack.isEnchanted() && !stack.hasFoil();
			case "flag:lore" -> {
				var lore = stack.get(DataComponents.LORE);
				yield lore != null && !lore.lines().isEmpty();
			}
			case "flag:damaged" -> stack.isDamageableItem() && stack.getDamageValue() > 0;
			case "flag:full_durability" -> stack.isDamageableItem() && stack.getDamageValue() == 0;
			case "flag:low_durability" -> stack.isDamageableItem()
				&& stack.getMaxDamage() > 0
				&& Math.round((stack.getMaxDamage() - stack.getDamageValue()) * 100.0F / stack.getMaxDamage()) <= 25;
			default -> activeFilter.startsWith("ench:")
				&& hasEnchantment(stack.get(DataComponents.ENCHANTMENTS), activeFilter.substring("ench:".length()))
				|| activeFilter.startsWith("ench:")
				&& hasEnchantment(stack.get(DataComponents.STORED_ENCHANTMENTS), activeFilter.substring("ench:".length()));
		};
	}

	private static boolean hasEnchantment(ItemEnchantments enchantments, String enchantmentId) {
		if (enchantments == null || enchantments.isEmpty()) {
			return false;
		}
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			String id = enchantment.unwrapKey()
				.map(key -> key.location().toString())
				.orElse(enchantment.getRegisteredName());
			if (enchantmentId.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static Component resultLine(ContainerRecord record, String itemId) {
		int count = record.items.get(itemId);
		String coordinates = record.x + " " + record.y + " " + record.z;
		return Component.translatable(
				"message.noobs_container_searcher.result_prefix",
				count,
				containerName(record.containerType)
			)
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal("[" + coordinates + "]").withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.CopyToClipboard(coordinates))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(
					"message.noobs_container_searcher.copy_coordinates"
				)))))
			.append(Component.translatable(
				"message.noobs_container_searcher.dimension_suffix",
				dimensionName(record.dimension)
			).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static Component containerName(String storedType) {
		if (storedType != null && storedType.startsWith("villager:")) {
			String profession = storedType.substring("villager:".length());
			ResourceLocation id = ResourceLocation.tryParse(profession);
			String path = id == null ? profession : id.getPath();
			return Component.translatable(
				"screen.noobs_container_searcher.villager_type",
				Component.translatable("entity.minecraft.villager." + path)
			);
		}
		ResourceLocation blockId = ResourceLocation.tryParse(storedType);
		if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
			return BuiltInRegistries.BLOCK.getValue(blockId).getName();
		}
		return Component.literal(storedType);
	}

	private static Component itemName(ResourceLocation itemId) {
		if (BuiltInRegistries.ITEM.containsKey(itemId)) {
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(itemId));
			return stack.getHoverName();
		}
		return Component.literal(itemId.toString());
	}

	private static Component dimensionName(String storedDimension) {
		ResourceLocation id = ResourceLocation.tryParse(storedDimension);
		if (id == null) {
			return Component.literal(storedDimension);
		}
		String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		String translated = Component.translatable(key).getString();
		return translated.equals(key) ? Component.literal(id.getPath()) : Component.translatable(key);
	}
}
