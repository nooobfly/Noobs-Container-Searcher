package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.CompatScreen;
import com.noobfly.containersearcher.compat.Gfx;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ContainerSearchScreen extends CompatScreen {
	private static final int ROW_HEIGHT = 22;
	private static final int FILTER_ROW_HEIGHT = 16;
	private static final int PANEL_BACKGROUND = 0xF016181C;
	private static final int PANEL_BORDER = 0xFF1E1F22;
	private static final int PANEL_BORDER_LIGHT = 0xFF4E5058;
	private static final int ROW_BACKGROUND = 0x88313338;
	private static final int ROW_BACKGROUND_ALT = 0x882B2D31;
	private static final int ROW_HOVER = 0xAA3A3D45;
	private static final int CONTROL_BACKGROUND = 0xFF111214;
	private static final int GUI_ACCENT = 0xFFDCDDDE;
	private static final int GUI_ACCENT_SOFT = 0xFFB5BAC1;
	private static final int ENCHANT_PURPLE = 0xFFFF55FF;
	private static final int ENCHANT_BLUE = 0xFFC9CDFB;
	private static final int MAX_DISTANCE = 1000;
	private static final int UNLIMITED_DISTANCE = -1;

	private final SearchController controller;
	private final List<ContainerRecord> records;
	private final List<ResultRow> rows = new ArrayList<>();
	private final List<FilterEntry> filters = new ArrayList<>();
	private final List<ContainerTypeEntry> containerTypes = new ArrayList<>();

	private EditBox searchBox;
	private EditBox filterSearchBox;
	private String selectedFilter;
	private final Set<String> selectedContainerTypeKeys = new LinkedHashSet<>();
	private int maxDistance = UNLIMITED_DISTANCE;
	private double resultScroll;
	private double filterScroll;
	private double containerTypeScroll;
	private boolean draggingDistanceSlider;

	public ContainerSearchScreen(SearchController controller, List<ContainerRecord> records) {
		super(Component.translatable("screen.noobs_container_searcher.title"));
		this.controller = controller;
		this.records = records;
		rebuild();
	}

	@Override
	protected void init() {
		searchBox = new EditBox(font, 0, 0, 220, 20, Component.translatable("screen.noobs_container_searcher.search"));
		searchBox.setHint(Component.translatable("screen.noobs_container_searcher.search_hint"));
		searchBox.setResponder(value -> {
			resultScroll = 0;
			rebuild();
		});
		addRenderableWidget(searchBox);
		filterSearchBox = new EditBox(font, 0, 0, 160, 18, Component.translatable("screen.noobs_container_searcher.filter_search"));
		filterSearchBox.setHint(Component.translatable("screen.noobs_container_searcher.filter_search_hint"));
		filterSearchBox.setResponder(value -> {
			filterScroll = 0;
			containerTypeScroll = 0;
			rebuild();
		});
		addRenderableWidget(filterSearchBox);
		addRenderableWidget(Button.builder(
			Component.translatable("screen.noobs_container_searcher.open_reroll_page"),
			button -> Compat.setScreen(minecraft, new LibrarianRotationScreen(controller, records))
		).bounds(panelLeft() + 10, panelTop() + 8, 180, 20).build());
		addRenderableWidget(Button.builder(
			Component.translatable("screen.noobs_container_searcher.clear_data"),
			button -> confirmClearData()
		).bounds(panelLeft() + 198, panelTop() + 8, 140, 20).build());
		searchBox.setFocused(true);
		setFocused(searchBox);
		positionSearchBoxes();
	}

	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		String value = searchBox == null ? "" : searchBox.getValue();
		String filterValue = filterSearchBox == null ? "" : filterSearchBox.getValue();
		super.resize(minecraft, width, height);
		searchBox.setValue(value);
		filterSearchBox.setValue(filterValue);
		rebuild();
	}

	@Override
	protected void renderContent(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x991E1F22);
		positionSearchBoxes();
		int left = panelLeft();
		int top = panelTop();
		int panelWidth = panelWidth();
		int panelHeight = panelHeight();
		int filterWidth = Math.min(180, Math.max(130, panelWidth / 3));
		int resultLeft = left + filterWidth + 8;

		graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BACKGROUND);
		graphics.outline(left, top, panelWidth, panelHeight, PANEL_BORDER_LIGHT);
		graphics.outline(left + 1, top + 1, panelWidth - 2, panelHeight - 2, PANEL_BORDER);
		graphics.centeredText(font, title, width / 2, top + 8, GUI_ACCENT);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.filters"), left + 10, top + 38, GUI_ACCENT_SOFT);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.container_types"), left + 10, containerTypeTitleY(), GUI_ACCENT_SOFT);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.results", rows.size()), resultLeft, top + 38, GUI_ACCENT_SOFT);

		renderWidgets(mouseX, mouseY, partialTick);
		renderDistanceSlider(graphics, mouseX, mouseY, resultLeft, top + 54, left + panelWidth - resultLeft - 8);
		renderFilters(graphics, mouseX, mouseY, left + 8, filterListTop(), filterWidth - 12, filterListHeight());
		renderContainerTypes(graphics, mouseX, mouseY, left + 8, containerTypeListTop(), filterWidth - 12, containerTypeListHeight());
		renderResults(graphics, mouseX, mouseY, resultLeft, resultsTop(), left + panelWidth - resultLeft - 8, resultsHeight());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0) {
			return false;
		}

		int left = panelLeft();
		int top = panelTop();
		int panelWidth = panelWidth();
		int panelHeight = panelHeight();
		int filterWidth = Math.min(180, Math.max(130, panelWidth / 3));
		int filterX = left + 8;
		int filterListTop = filterListTop();
		int filterListHeight = filterListHeight();
		int containerTypeListTop = containerTypeListTop();
		int containerTypeListHeight = containerTypeListHeight();
		int resultLeft = left + filterWidth + 8;
		int resultWidth = left + panelWidth - resultLeft - 8;

		if (inside(mouseX, mouseY, distanceSliderX(resultLeft), distanceSliderY(), distanceSliderWidth(resultWidth), 14)) {
			updateDistanceSlider(mouseX, resultLeft, resultWidth);
			draggingDistanceSlider = true;
			return true;
		}

		if (inside(mouseX, mouseY, filterX, filterListTop, filterWidth - 12, filterListHeight)) {
			int index = (int) ((mouseY - filterListTop + filterScroll) / FILTER_ROW_HEIGHT);
			if (index >= 0 && index < filters.size()) {
				FilterEntry filter = filters.get(index);
				selectedFilter = filter.id.equals(selectedFilter) ? null : filter.id;
				resultScroll = 0;
				rebuild();
				return true;
			}
		}

		if (inside(mouseX, mouseY, filterX, containerTypeListTop, filterWidth - 12, containerTypeListHeight)) {
			int index = (int) ((mouseY - containerTypeListTop + containerTypeScroll) / FILTER_ROW_HEIGHT);
			if (index >= 0 && index < containerTypes.size()) {
				ContainerTypeEntry type = containerTypes.get(index);
				if (!selectedContainerTypeKeys.remove(type.key)) {
					selectedContainerTypeKeys.add(type.key);
				}
				resultScroll = 0;
				rebuild();
				return true;
			}
		}

		int listTop = resultsTop();
		int listHeight = resultsHeight();
		if (inside(mouseX, mouseY, resultLeft, listTop, resultWidth, listHeight)) {
			int index = (int) ((mouseY - listTop + resultScroll) / ROW_HEIGHT);
			if (index >= 0 && index < rows.size()) {
				ResultRow row = rows.get(index);
				controller.focusItem(row.itemId, recordsFor(row), selectedFilter);
				onClose();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (draggingDistanceSlider) {
			int left = panelLeft();
			int panelWidth = panelWidth();
			int filterWidth = Math.min(180, Math.max(130, panelWidth / 3));
			int resultLeft = left + filterWidth + 8;
			updateDistanceSlider(mouseX, resultLeft, left + panelWidth - resultLeft - 8);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingDistanceSlider = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int left = panelLeft();
		int panelWidth = panelWidth();
		int filterWidth = Math.min(180, Math.max(130, panelWidth / 3));
		int listTop = resultsTop();
		int listHeight = resultsHeight();
		int filterListTop = filterListTop();
		int filterListHeight = filterListHeight();
		int containerTypeListTop = containerTypeListTop();
		int containerTypeListHeight = containerTypeListHeight();

		if (inside(mouseX, mouseY, left + 8, filterListTop, filterWidth - 12, filterListHeight)) {
			filterScroll = clampScroll(filterScroll - verticalAmount * 18, filters.size() * FILTER_ROW_HEIGHT, filterListHeight);
			return true;
		}
		if (inside(mouseX, mouseY, left + 8, containerTypeListTop, filterWidth - 12, containerTypeListHeight)) {
			containerTypeScroll = clampScroll(
				containerTypeScroll - verticalAmount * 18,
				containerTypes.size() * FILTER_ROW_HEIGHT,
				containerTypeListHeight
			);
			return true;
		}
		int resultLeft = left + filterWidth + 8;
		if (inside(mouseX, mouseY, resultLeft, listTop, left + panelWidth - resultLeft - 8, listHeight)) {
			resultScroll = clampScroll(resultScroll - verticalAmount * ROW_HEIGHT, rows.size() * ROW_HEIGHT, listHeight);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		if (filterSearchBox.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void confirmClearData() {
		Compat.setScreen(minecraft, new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					controller.clearServerData(minecraft);
					records.clear();
					resultScroll = 0;
					rebuild();
				}
				Compat.setScreen(minecraft, this);
			},
			Component.translatable("screen.noobs_container_searcher.clear_data_title"),
			Component.translatable("screen.noobs_container_searcher.clear_data_message")
		));
	}

	private void rebuild() {
		rows.clear();
		filters.clear();
		containerTypes.clear();
		String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		String filterQuery = filterSearchBox == null ? "" : normalizedSearch(filterSearchBox.getValue());
		Map<String, FilterEntry> filterMap = new LinkedHashMap<>();
		Map<String, ContainerTypeEntry> containerTypeMap = new LinkedHashMap<>();
		addFixedFilters(filterMap);

		Map<String, ResultRow> rowMap = new LinkedHashMap<>();
		for (ContainerRecord record : records) {
			countContainerType(containerTypeMap, record);
			if (!passesDistance(record) || !passesContainerType(record, containerTypeMap)) {
				continue;
			}
			for (ContainerItemRecord item : entries(record)) {
				countFixedFilters(filterMap, item);
				for (int i = 0; i < item.enchantments.size(); i++) {
					String id = item.enchantments.get(i);
					String fallback = i < item.enchantmentNames.size() ? item.enchantmentNames.get(i) : id;
					String name = localizedEnchantmentName(id);
					filterMap.computeIfAbsent("ench:" + id, ignored -> new FilterEntry("ench:" + id, name, id + " " + fallback + " " + name)).count += item.count;
				}

				if (selectedFilter != null && !passesFilter(item, selectedFilter)) {
					continue;
				}
				if (!query.isEmpty() && !matches(item, query)) {
					continue;
				}
				String key = rowKey(record, item);
				ResultRow row = rowMap.get(key);
				if (row == null) {
					rowMap.put(key, new ResultRow(record, item));
				} else {
					row.add(item);
				}
			}
		}
		rows.addAll(rowMap.values());

		filters.addAll(filterMap.values());
		filters.removeIf(filter -> filter.count <= 0);
		if (!filterQuery.isEmpty()) {
			filters.removeIf(filter -> !filter.matches(filterQuery));
		}
		containerTypes.addAll(containerTypeMap.values());
		containerTypes.removeIf(type -> type.count <= 0);
		if (!filterQuery.isEmpty()) {
			containerTypes.removeIf(type -> !type.matches(filterQuery));
		}
		filters.sort(Comparator
			.comparingInt((FilterEntry filter) -> filter.order)
			.thenComparing(filter -> filter.name.toLowerCase(Locale.ROOT)));
		containerTypes.sort(Comparator.comparing(type -> type.name.toLowerCase(Locale.ROOT)));
		rows.sort(Comparator
			.comparing((ResultRow row) -> row.name.toLowerCase(Locale.ROOT))
			.thenComparing(row -> row.record.dimension)
			.thenComparingInt(row -> row.record.x)
			.thenComparingInt(row -> row.record.y)
			.thenComparingInt(row -> row.record.z));
		resultScroll = clampScroll(resultScroll, rows.size() * ROW_HEIGHT, resultsHeight());
		filterScroll = clampScroll(filterScroll, filters.size() * FILTER_ROW_HEIGHT, filterListHeight());
		containerTypeScroll = clampScroll(containerTypeScroll, containerTypes.size() * FILTER_ROW_HEIGHT, containerTypeListHeight());
	}

	private void renderFilters(Gfx graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
		graphics.enableScissor(x, y, x + width, y + height);
		int start = Math.max(0, (int) (filterScroll / FILTER_ROW_HEIGHT));
		int offset = y - (int) (filterScroll % FILTER_ROW_HEIGHT);
		for (int i = start; i < filters.size(); i++) {
			int rowY = offset + (i - start) * FILTER_ROW_HEIGHT;
			if (rowY > y + height) {
				break;
			}
			FilterEntry filter = filters.get(i);
			boolean selected = filter.id.equals(selectedFilter);
			boolean hovered = inside(mouseX, mouseY, x, rowY, width, FILTER_ROW_HEIGHT);
			int accent = filterColor(filter);
			graphics.fill(x, rowY, x + width, rowY + FILTER_ROW_HEIGHT - 1, selected ? 0xAA2F3136 : hovered ? 0x663A3D45 : 0x441F2024);
			graphics.fill(x, rowY, x + 2, rowY + FILTER_ROW_HEIGHT - 1, accent);
			if (selected) {
				graphics.outline(x, rowY, width, FILTER_ROW_HEIGHT - 1, accent);
			}
			graphics.text(font, trim(filter.name + " (" + filter.count + ")", width - 10), x + 5, rowY + 4, selected ? 0xFFFFFF55 : accent);
		}
		graphics.disableScissor();
		if (filters.isEmpty()) {
			graphics.text(font, Component.translatable("screen.noobs_container_searcher.no_filters"), x + 4, y + 6, 0xFF888888);
		}
	}

	private void renderContainerTypes(Gfx graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
		graphics.enableScissor(x, y, x + width, y + height);
		int start = Math.max(0, (int) (containerTypeScroll / FILTER_ROW_HEIGHT));
		int offset = y - (int) (containerTypeScroll % FILTER_ROW_HEIGHT);
		for (int i = start; i < containerTypes.size(); i++) {
			int rowY = offset + (i - start) * FILTER_ROW_HEIGHT;
			if (rowY > y + height) {
				break;
			}
			ContainerTypeEntry type = containerTypes.get(i);
			boolean selected = selectedContainerTypeKeys.contains(type.key);
			boolean hovered = inside(mouseX, mouseY, x, rowY, width, FILTER_ROW_HEIGHT);
			int accent = selected ? 0xFF55FFFF : 0xFFB5BAC1;
			graphics.fill(x, rowY, x + width, rowY + FILTER_ROW_HEIGHT - 1, selected ? 0xAA253A3D : hovered ? 0x663A3D45 : 0x441F2024);
			graphics.fill(x, rowY, x + 2, rowY + FILTER_ROW_HEIGHT - 1, accent);
			if (selected) {
				graphics.outline(x, rowY, width, FILTER_ROW_HEIGHT - 1, accent);
			}
			graphics.text(font, trim(type.name + " (" + type.count + ")", width - 10), x + 5, rowY + 4, accent);
		}
		graphics.disableScissor();
		if (containerTypes.isEmpty()) {
			graphics.text(font, Component.translatable("screen.noobs_container_searcher.no_container_types"), x + 4, y + 6, 0xFF888888);
		}
	}

	private void renderResults(Gfx graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
		graphics.enableScissor(x, y, x + width, y + height);
		int start = Math.max(0, (int) (resultScroll / ROW_HEIGHT));
		int offset = y - (int) (resultScroll % ROW_HEIGHT);
		for (int i = start; i < rows.size(); i++) {
			int rowY = offset + (i - start) * ROW_HEIGHT;
			if (rowY > y + height) {
				break;
			}
			ResultRow row = rows.get(i);
			boolean hovered = inside(mouseX, mouseY, x, rowY, width, ROW_HEIGHT);
			graphics.fill(x, rowY, x + width, rowY + ROW_HEIGHT - 1, hovered ? ROW_HOVER : i % 2 == 0 ? ROW_BACKGROUND : ROW_BACKGROUND_ALT);
			Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.itemId));
			ItemStack stack = ItemStack.EMPTY;
			if (item != null) {
				stack = displayStack(item, row.item);
				graphics.fakeItem(stack, x + 4, rowY + 3);
				graphics.itemDecorations(font, stack, x + 4, rowY + 3, row.count > 1 ? Integer.toString(row.count) : null);
				if (inside(mouseX, mouseY, x + 4, rowY + 3, 16, 16)) {
					graphics.tooltip(font, tooltip(stack), mouseX, mouseY);
				}
			}
			int idWidth = Math.min(font.width(row.itemId), Math.max(84, width / 3));
			int textWidth = Math.max(40, width - 34 - idWidth - 8);
			graphics.text(font, trim(row.name, textWidth), x + 26, rowY + 2, itemNameColor(stack, row.item));
			String detail = containerName(row.record.containerType).getString()
				+ " | " + row.record.x + " " + row.record.y + " " + row.record.z
				+ " | " + shortDimension(row.record.dimension)
				+ distanceDetail(row.record)
				+ itemDetail(row.item);
			graphics.text(font, trim(detail, textWidth), x + 26, rowY + 12, row.item.enchanted ? ENCHANT_BLUE : 0xFFB0A7BC);
			graphics.text(font, trim(row.itemId, idWidth), x + width - idWidth - 4, rowY + 7, 0xFF777777);
		}
		graphics.disableScissor();
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), x + 4, y + 6, 0xFF888888);
		}
	}

	private List<ContainerRecord> recordsFor(ResultRow row) {
		Map<String, ContainerRecord> matches = new HashMap<>();
		for (ResultRow other : rows) {
			if (itemVariantKey(other.item).equals(itemVariantKey(row.item))
				&& isVillager(other.record) == isVillager(row.record)
				&& (selectedFilter == null || passesFilter(other.item, selectedFilter))) {
				matches.put(recordIdentity(other.record), other.record);
			}
		}
		return new ArrayList<>(matches.values());
	}

	private static String rowKey(ContainerRecord record, ContainerItemRecord item) {
		return recordIdentity(record) + "|" + itemVariantKey(item);
	}

	private static String itemVariantKey(ContainerItemRecord item) {
		if (item.stackData != null && !item.stackData.isBlank()) {
			return item.stackData;
		}
		return item.itemId + "|" + item.name + "|" + item.damage + "|" + item.enchantments + "|" + item.enchantmentNames;
	}

	private static String recordIdentity(ContainerRecord record) {
		return record.entityUuid == null
			? record.server + "|" + record.dimension + "|" + record.x + "|" + record.y + "|" + record.z
			: record.server + "|" + record.dimension + "|villager|" + record.entityUuid;
	}

	private static boolean isVillager(ContainerRecord record) {
		return record.entityUuid != null && !record.entityUuid.isBlank();
	}

	private static List<ContainerItemRecord> entries(ContainerRecord record) {
		if (record.entries != null && !record.entries.isEmpty()) {
			return record.entries;
		}
		List<ContainerItemRecord> fallback = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : record.items.entrySet()) {
			ContainerItemRecord item = new ContainerItemRecord();
			item.itemId = entry.getKey();
			item.count = entry.getValue();
			item.name = itemName(entry.getKey());
			item.searchText = (item.itemId + " " + item.name).toLowerCase(Locale.ROOT);
			fallback.add(item);
		}
		return fallback;
	}

	private static boolean matches(ContainerItemRecord item, String query) {
		for (String token : query.split("\\s+")) {
			if (!token.isBlank() && !matchesToken(item, token)) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesToken(ContainerItemRecord item, String token) {
		String currentName = localizedItemName(item).toLowerCase(Locale.ROOT);
		if (token.equals("enchanted") || token.equals("buyulu") || token.equals("büyülü")) {
			return item.enchanted;
		}
		if (token.equals("unenchant") || token.equals("unenchanted") || token.equals("buyusuz") || token.equals("büyüsüz")) {
			return !item.enchanted;
		}
		if (token.startsWith("name:") || token.startsWith("isim:")) {
			String value = token.substring(token.indexOf(':') + 1);
			return currentName.contains(value);
		}
		if (token.startsWith("lore:") || token.startsWith("aciklama:") || token.startsWith("açıklama:")) {
			String value = token.substring(token.indexOf(':') + 1);
			return loreText(item).contains(value);
		}
		if (token.startsWith("durability:") || token.startsWith("dur:") || token.startsWith("dayaniklilik:") || token.startsWith("dayanıklılık:")) {
			String value = token.substring(token.indexOf(':') + 1);
			return matchesDurability(item, value);
		}
		return item.searchText != null && item.searchText.contains(token)
			|| item.itemId.toLowerCase(Locale.ROOT).contains(token)
			|| currentName.contains(token);
	}

	private static void addFixedFilters(Map<String, FilterEntry> filters) {
		filters.put("flag:enchanted", new FilterEntry("flag:enchanted", Component.translatable("screen.noobs_container_searcher.filter_enchanted").getString(), 0));
		filters.put("flag:unenchanted", new FilterEntry("flag:unenchanted", Component.translatable("screen.noobs_container_searcher.filter_unenchanted").getString(), 0));
		filters.put("flag:lore", new FilterEntry("flag:lore", Component.translatable("screen.noobs_container_searcher.filter_lore").getString(), 0));
		filters.put("flag:damaged", new FilterEntry("flag:damaged", Component.translatable("screen.noobs_container_searcher.filter_damaged").getString(), 0));
		filters.put("flag:full_durability", new FilterEntry("flag:full_durability", Component.translatable("screen.noobs_container_searcher.filter_full_durability").getString(), 0));
		filters.put("flag:low_durability", new FilterEntry("flag:low_durability", Component.translatable("screen.noobs_container_searcher.filter_low_durability").getString(), 0));
	}

	private static void countContainerType(Map<String, ContainerTypeEntry> types, ContainerRecord record) {
		if (record.containerType == null || record.containerType.isBlank()) {
			return;
		}
		String name = containerName(record.containerType).getString();
		ContainerTypeEntry entry = types.computeIfAbsent(containerTypeKey(record.containerType), ignored -> new ContainerTypeEntry(name));
		entry.typeIds.add(record.containerType);
		entry.searchText = normalizedSearch(entry.searchText + " " + record.containerType);
		entry.count += itemCount(record);
	}

	private static void countFixedFilters(Map<String, FilterEntry> filters, ContainerItemRecord item) {
		if (item.enchanted) {
			filters.get("flag:enchanted").count += item.count;
		} else {
			filters.get("flag:unenchanted").count += item.count;
		}
		if (!loreText(item).isBlank()) {
			filters.get("flag:lore").count += item.count;
		}
		if (item.damageable && item.damage > 0) {
			filters.get("flag:damaged").count += item.count;
		}
		if (item.damageable && item.damage == 0) {
			filters.get("flag:full_durability").count += item.count;
		}
		if (item.damageable && durabilityPercent(item) <= 25) {
			filters.get("flag:low_durability").count += item.count;
		}
	}

	private static boolean passesFilter(ContainerItemRecord item, String filter) {
		return switch (filter) {
			case "flag:enchanted" -> item.enchanted;
			case "flag:unenchanted" -> !item.enchanted;
			case "flag:lore" -> !loreText(item).isBlank();
			case "flag:damaged" -> item.damageable && item.damage > 0;
			case "flag:full_durability" -> item.damageable && item.damage == 0;
			case "flag:low_durability" -> item.damageable && durabilityPercent(item) <= 25;
			default -> filter.startsWith("ench:") && item.enchantments.contains(filter.substring("ench:".length()));
		};
	}

	private static boolean matchesDurability(ContainerItemRecord item, String expression) {
		if (!item.damageable || item.maxDamage <= 0) {
			return false;
		}
		int percent = durabilityPercent(item);
		try {
			if (expression.startsWith("<=")) {
				return percent <= Integer.parseInt(expression.substring(2).replace("%", ""));
			}
			if (expression.startsWith(">=")) {
				return percent >= Integer.parseInt(expression.substring(2).replace("%", ""));
			}
			if (expression.startsWith("<")) {
				return percent < Integer.parseInt(expression.substring(1).replace("%", ""));
			}
			if (expression.startsWith(">")) {
				return percent > Integer.parseInt(expression.substring(1).replace("%", ""));
			}
			return percent == Integer.parseInt(expression.replace("%", ""));
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private static int durabilityPercent(ContainerItemRecord item) {
		if (!item.damageable || item.maxDamage <= 0) {
			return 100;
		}
		int remaining = Math.max(0, item.maxDamage - item.damage);
		return Math.round(remaining * 100.0F / item.maxDamage);
	}

	private static String tooltipText(ContainerItemRecord item) {
		if (item.tooltipLines == null || item.tooltipLines.isEmpty()) {
			return "";
		}
		return String.join(" ", item.tooltipLines).toLowerCase(Locale.ROOT);
	}

	private static String loreText(ContainerItemRecord item) {
		if (item.loreLines == null || item.loreLines.isEmpty()) {
			return "";
		}
		return String.join(" ", item.loreLines).toLowerCase(Locale.ROOT);
	}

	private static String itemDetail(ContainerItemRecord item) {
		StringBuilder detail = new StringBuilder();
		if (item.damageable && item.maxDamage > 0) {
			detail.append(" | ").append(durabilityPercent(item)).append("%");
		}
		return detail.toString();
	}

	private String distanceDetail(ContainerRecord record) {
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return "";
		}
		if (!ContainerSearcherClient.dimensionKey(minecraft).equals(record.dimension)) {
			return " | " + Component.translatable("screen.noobs_container_searcher.different_dimension").getString();
		}
		double dx = record.x + 0.5D - minecraft.player.getX();
		double dy = record.y + 0.5D - minecraft.player.getY();
		double dz = record.z + 0.5D - minecraft.player.getZ();
		return " | " + Component.translatable(
			"screen.noobs_container_searcher.distance",
			Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))
		).getString();
	}

	private static int filterColor(FilterEntry filter) {
		if (filter.id.startsWith("ench:")) {
			return ENCHANT_PURPLE;
		}
		return switch (filter.id) {
			case "flag:enchanted" -> ENCHANT_PURPLE;
			case "flag:unenchanted" -> 0xFFBFBFBF;
			case "flag:lore" -> 0xFFFFFF55;
			case "flag:damaged" -> 0xFFFF5555;
			case "flag:full_durability" -> 0xFF55FF55;
			case "flag:low_durability" -> 0xFFFFAA00;
			default -> 0xFFE0E0E0;
		};
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

	private static String containerTypeKey(String storedType) {
		if (storedType == null || storedType.isBlank()) {
			return "";
		}
		return normalizedSearch(containerName(storedType).getString());
	}

	private static int itemCount(ContainerRecord record) {
		int count = 0;
		for (int value : record.items.values()) {
			count += value;
		}
		return count;
	}

	private static int itemNameColor(ItemStack stack, ContainerItemRecord item) {
		if (item.enchanted || stack.hasFoil()) {
			return ENCHANT_PURPLE;
		}
		if (!stack.isEmpty()) {
			Rarity rarity = stack.getRarity();
			return switch (rarity) {
				case UNCOMMON -> 0xFFFFFF55;
				case RARE -> 0xFF55FFFF;
				case EPIC -> 0xFFFF55FF;
				default -> 0xFFFFFFFF;
			};
		}
		return item.enchanted ? ENCHANT_PURPLE : 0xFFFFFFFF;
	}

	private static ItemStack displayStack(Item item, ContainerItemRecord record) {
		ItemStack decoded = decodeStack(record);
		if (!decoded.isEmpty()) {
			return decoded;
		}

		ItemStack stack = new ItemStack(item, Math.min(record.count, 99));
		if (record.enchanted) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		if (record.damageable && record.maxDamage > 0) {
			stack.setDamageValue(record.damage);
		}
		if (record.loreLines != null && !record.loreLines.isEmpty()) {
			List<Component> lore = new ArrayList<>();
			for (String line : record.loreLines) {
				lore.add(Component.literal(line));
			}
			stack.set(DataComponents.LORE, new ItemLore(lore));
		}
		return stack;
	}

	private List<Component> tooltip(ItemStack stack) {
		return Screen.getTooltipFromItem(minecraft, stack);
	}

	private static ItemStack decodeStack(ContainerItemRecord record) {
		Minecraft client = Minecraft.getInstance();
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

	private static String itemName(String itemId) {
		ResourceLocation id = ResourceLocation.tryParse(itemId);
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
			Item item = BuiltInRegistries.ITEM.get(id);
			return item.getName(new ItemStack(item)).getString();
		}
		return itemId;
	}

	private static String localizedItemName(ContainerItemRecord record) {
		ItemStack stack = decodeStack(record);
		if (!stack.isEmpty()) {
			return stack.getHoverName().getString();
		}
		return itemName(record.itemId);
	}

	private static String localizedEnchantmentName(String enchantmentId) {
		ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
		if (id == null) {
			return enchantmentId;
		}
		String key = "enchantment." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		String translated = Component.translatable(key).getString();
		return translated.equals(key) ? enchantmentId : translated;
	}

	private String trim(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(8, maxWidth - font.width("..."))) + "...";
	}

	private static String shortDimension(String dimension) {
		ResourceLocation id = ResourceLocation.tryParse(dimension);
		if (id == null) {
			return dimension;
		}
		String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		String translated = Component.translatable(key).getString();
		return translated.equals(key) ? id.getPath() : translated;
	}

	private void positionSearchBox() {
		if (searchBox != null) {
			searchBox.setX(panelLeft() + panelWidth() - 236);
			searchBox.setY(panelTop() + 32);
		}
	}

	private void positionSearchBoxes() {
		positionSearchBox();
		if (filterSearchBox != null) {
			int filterWidth = Math.min(180, Math.max(130, panelWidth() / 3));
			filterSearchBox.setX(panelLeft() + 8);
			filterSearchBox.setY(panelTop() + 52);
			filterSearchBox.setWidth(filterWidth - 12);
		}
	}

	private int filterListTop() {
		return panelTop() + 74;
	}

	private int filterListHeight() {
		return Math.max(34, containerTypeTitleY() - filterListTop() - 6);
	}

	private int containerTypeTitleY() {
		return panelTop() + panelHeight() - containerTypeSectionHeight();
	}

	private int containerTypeListTop() {
		return containerTypeTitleY() + 14;
	}

	private int containerTypeListHeight() {
		return Math.max(18, panelTop() + panelHeight() - containerTypeListTop() - 10);
	}

	private int containerTypeSectionHeight() {
		return Math.min(94, Math.max(62, panelHeight() / 3));
	}

	private int resultsTop() {
		return panelTop() + 74;
	}

	private int resultsHeight() {
		return panelHeight() - 84;
	}

	private int distanceSliderY() {
		return panelTop() + 54;
	}

	private static int distanceSliderX(int resultLeft) {
		return resultLeft + 82;
	}

	private static int distanceSliderWidth(int resultWidth) {
		return Math.max(60, resultWidth - 176);
	}

	private void renderDistanceSlider(Gfx graphics, int mouseX, int mouseY, int x, int y, int width) {
		int sliderX = distanceSliderX(x);
		int sliderWidth = distanceSliderWidth(width);
		String label = Component.translatable("screen.noobs_container_searcher.distance_filter").getString();
		String value = maxDistance == UNLIMITED_DISTANCE
			? Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString()
			: Component.translatable("screen.noobs_container_searcher.distance_value", maxDistance).getString();
		graphics.text(font, label, x, y + 2, GUI_ACCENT_SOFT);
		graphics.fill(sliderX, y + 5, sliderX + sliderWidth, y + 8, CONTROL_BACKGROUND);
		graphics.fill(sliderX, y + 5, distanceKnobX(sliderX, sliderWidth), y + 8, 0xFF5865F2);
		int knobX = distanceKnobX(sliderX, sliderWidth);
		graphics.fill(knobX - 2, y + 2, knobX + 3, y + 11, inside(mouseX, mouseY, sliderX, y, sliderWidth, 14) ? GUI_ACCENT : GUI_ACCENT_SOFT);
		graphics.text(font, trim(value, 76), sliderX + sliderWidth + 8, y + 2, GUI_ACCENT_SOFT);
	}

	private int distanceKnobX(int sliderX, int sliderWidth) {
		if (maxDistance == UNLIMITED_DISTANCE) {
			return sliderX + sliderWidth;
		}
		double ratio = Math.max(0, Math.min(1, maxDistance / (double) MAX_DISTANCE));
		return sliderX + (int) Math.round(ratio * sliderWidth);
	}

	private void updateDistanceSlider(double mouseX, int resultLeft, int resultWidth) {
		int sliderX = distanceSliderX(resultLeft);
		int sliderWidth = distanceSliderWidth(resultWidth);
		double ratio = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderWidth));
		int newDistance = ratio >= 0.97D ? UNLIMITED_DISTANCE : Math.max(25, (int) Math.round(ratio * MAX_DISTANCE / 25.0D) * 25);
		if (newDistance != maxDistance) {
			maxDistance = newDistance;
			resultScroll = 0;
			rebuild();
		}
	}

	private boolean passesDistance(ContainerRecord record) {
		if (maxDistance == UNLIMITED_DISTANCE || minecraft == null || minecraft.player == null || minecraft.level == null) {
			return true;
		}
		if (!ContainerSearcherClient.dimensionKey(minecraft).equals(record.dimension)) {
			return false;
		}
		double dx = record.x + 0.5D - minecraft.player.getX();
		double dy = record.y + 0.5D - minecraft.player.getY();
		double dz = record.z + 0.5D - minecraft.player.getZ();
		return dx * dx + dy * dy + dz * dz <= maxDistance * (double) maxDistance;
	}

	private boolean passesContainerType(ContainerRecord record, Map<String, ContainerTypeEntry> containerTypeMap) {
		if (selectedContainerTypeKeys.isEmpty()) {
			return true;
		}
		ContainerTypeEntry entry = containerTypeMap.get(containerTypeKey(record.containerType));
		return entry != null && selectedContainerTypeKeys.contains(entry.key);
	}

	private int panelLeft() {
		return width / 2 - panelWidth() / 2;
	}

	private int panelTop() {
		return height / 2 - panelHeight() / 2;
	}

	private int panelWidth() {
		return (int) Math.round(width * 0.8D);
	}

	private int panelHeight() {
		return (int) Math.round(height * 0.8D);
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}

	private static double clampScroll(double value, int contentHeight, int viewHeight) {
		return Math.max(0, Math.min(value, Math.max(0, contentHeight - viewHeight)));
	}

	private static String normalizedSearch(String value) {
		String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized.replace('ı', 'i');
	}

	private static final class ResultRow {
		private final ContainerRecord record;
		private final ContainerItemRecord item;
		private final String itemId;
		private final String name;
		private int count;

		private ResultRow(ContainerRecord record, ContainerItemRecord item) {
			this.record = record;
			this.item = item;
			this.itemId = item.itemId;
			this.name = localizedItemName(item);
			this.count = item.count;
		}

		private void add(ContainerItemRecord item) {
			count += item.count;
		}
	}

	private static final class FilterEntry {
		private final String id;
		private final String name;
		private final String searchText;
		private final int order;
		private int count;

		private FilterEntry(String id, String name) {
			this(id, name, id + " " + name, 1);
		}

		private FilterEntry(String id, String name, String searchText) {
			this(id, name, searchText, 1);
		}

		private FilterEntry(String id, String name, int order) {
			this(id, name, id + " " + name, order);
		}

		private FilterEntry(String id, String name, String searchText, int order) {
			this.id = id;
			this.name = name;
			this.searchText = normalizedSearch(searchText);
			this.order = order;
		}

		private boolean matches(String query) {
			return searchText.contains(query);
		}
	}

	private static final class ContainerTypeEntry {
		private final String key;
		private final String name;
		private final Set<String> typeIds = new LinkedHashSet<>();
		private String searchText;
		private int count;

		private ContainerTypeEntry(String name) {
			this.key = normalizedSearch(name);
			this.name = name;
			this.searchText = this.key;
		}

		private boolean matches(String query) {
			return searchText.contains(query);
		}
	}
}
