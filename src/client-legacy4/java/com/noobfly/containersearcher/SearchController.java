package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.Gfx;
import com.noobfly.containersearcher.compat.Keys;
import com.noobfly.containersearcher.compat.Platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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
	private static <S> SuggestionProvider<S> itemSuggestions() {
		return (context, builder) -> {
			String remaining = builder.getRemainingLowerCase();
			BuiltInRegistries.ITEM.keySet().stream()
				.map(ResourceLocation::toString)
				.filter(id -> id.startsWith(remaining) || id.substring(id.indexOf(':') + 1).startsWith(remaining))
				.forEach(builder::suggest);
			return builder.buildFuture();
		};
	}

	// how a command replies to whoever ran it, differs per loader
	public interface CommandMessenger<S> {
		void feedback(S source, Component message);

		void error(S source, Component message);
	}

	private static ResourceLocation activeSearchItem;
	private static String activeFilter;
	// signature of the exact stack picked in the gui, so other enchanted books etc. don't glow too
	private static String activeVariant;
	private static final java.util.Map<ItemStack, Boolean> VARIANT_CACHE = new java.util.WeakHashMap<>();

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

		Platform.registerCommands(this);
	}

	public <S> void registerCommands(CommandDispatcher<S> dispatcher, CommandMessenger<S> messenger) {
		dispatcher.register(createCommand("containersearch", messenger));
		dispatcher.register(createCommand("csr", messenger));
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
		activeVariant = null;
		VARIANT_CACHE.clear();
	}

	public static void renderMatchingSlot(Gfx graphics, Slot slot) {
		ContainerChanges.renderPanel(graphics, slot);
		if (activeSearchItem == null) {
			return;
		}

		ItemStack stack = slot.getItem();
		if (slot.container instanceof Inventory
			|| stack.isEmpty()
			|| !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(activeSearchItem)
			|| !matchesActiveFilter(stack)
			|| !matchesActiveVariant(stack)) {
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

	private <S> LiteralArgumentBuilder<S> createCommand(String name, CommandMessenger<S> messenger) {
		return LiteralArgumentBuilder.<S>literal(name)
			.then(LiteralArgumentBuilder.<S>literal("gui")
				.executes(context -> openSearchMenu(context.getSource(), messenger)))
			.then(LiteralArgumentBuilder.<S>literal("menu")
				.executes(context -> openSearchMenu(context.getSource(), messenger)))
			.then(LiteralArgumentBuilder.<S>literal("clearhighlight")
				.executes(context -> clearHighlights(context.getSource(), messenger)))
			.then(RequiredArgumentBuilder.<S, ResourceLocation>argument("item", ResourceLocationArgument.id())
				.suggests(SearchController.<S>itemSuggestions())
				.executes(context -> search(context.getSource(), context.getArgument("item", ResourceLocation.class), false, messenger))
				.then(LiteralArgumentBuilder.<S>literal("global")
					.executes(context -> search(context.getSource(), context.getArgument("item", ResourceLocation.class), true, messenger))));
	}

	private <S> int search(S source, ResourceLocation identifier, boolean global, CommandMessenger<S> messenger) {
		return search(
			identifier,
			global,
			message -> messenger.feedback(source, message),
			message -> messenger.error(source, message)
		);
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
		activeVariant = null;
		VARIANT_CACHE.clear();

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

	private <S> int clearHighlights(S source, CommandMessenger<S> messenger) {
		highlights.clear(Minecraft.getInstance());
		messenger.feedback(source, Component.translatable("message.noobs_container_searcher.highlights_cleared"));
		return 1;
	}

	private <S> int openSearchMenu(S source, CommandMessenger<S> messenger) {
		return openSearchMenu(Minecraft.getInstance(), message -> messenger.error(source, message));
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

	void focusItem(String itemId, List<ContainerRecord> records, String filter, ContainerItemRecord variant) {
		Minecraft client = Minecraft.getInstance();
		ResourceLocation identifier = ResourceLocation.tryParse(itemId);
		if (identifier != null) {
			activeSearchItem = identifier;
		}
		activeFilter = filter;
		activeVariant = variant == null ? null : variantSignature(variant.itemId, variant.name, variant.enchantmentNames);
		VARIANT_CACHE.clear();
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

	private static String variantSignature(String itemId, String name, List<String> enchantNames) {
		List<String> sorted = new java.util.ArrayList<>(enchantNames);
		java.util.Collections.sort(sorted);
		return itemId + "|" + name + "|" + sorted;
	}

	private static boolean matchesActiveVariant(ItemStack stack) {
		if (activeVariant == null) {
			return true;
		}
		return VARIANT_CACHE.computeIfAbsent(stack, s -> {
			List<String> names = new java.util.ArrayList<>();
			addEnchantmentNames(names, s.get(DataComponents.ENCHANTMENTS));
			addEnchantmentNames(names, s.get(DataComponents.STORED_ENCHANTMENTS));
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
			return activeVariant.equals(variantSignature(id, s.getHoverName().getString(), names));
		});
	}

	private static void addEnchantmentNames(List<String> out, ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			out.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}
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
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coordinates))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(
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
			return BuiltInRegistries.BLOCK.get(blockId).getName();
		}
		return Component.literal(storedType);
	}

	private static Component itemName(ResourceLocation itemId) {
		if (BuiltInRegistries.ITEM.containsKey(itemId)) {
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
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
