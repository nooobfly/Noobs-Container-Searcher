package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.CompatScreen;
import com.noobfly.containersearcher.compat.Gfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;

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
	private enum Tab { SEARCH, REROLL, SETTINGS }

	private static final int ROW_HEIGHT = 22;
	private static final int FILTER_ROW_HEIGHT = 16;
	private static final int SIDEBAR_WIDTH = 176;

	// Palette
	private static final int APP_BACKDROP = 0xCC0B0C0F;
	private static final int PANEL_BACKGROUND = 0xFA16171C;
	private static final int SIDEBAR_BACKGROUND = 0xFA101115;
	private static final int SIDEBAR_DIVIDER = 0xFF25262C;
	private static final int PANEL_BORDER = 0xFF2A2B31;
	private static final int CARD_BACKGROUND = 0x80202126;
	private static final int CARD_BACKGROUND_ALT = 0x801A1B20;
	private static final int CARD_HOVER = 0x9A2A2C34;
	private static final int CONTROL_BACKGROUND = 0xFF0E0F12;
	private static final int CONTROL_BORDER = 0xFF2E2F36;

	private static final int TEXT_PRIMARY = 0xFFF2F3F5;
	private static final int TEXT_SECONDARY = 0xFF9A9CA6;
	private static final int TEXT_MUTED = 0xFF5C5E68;

	private static final int ACCENT = 0xFF5865F2;
	private static final int ACCENT_HOVER = 0xFF7A85FF;
	private static final int ACCENT_SOFT = 0x265865F2;
	private static final int DANGER = 0xFFED4245;
	private static final int DANGER_HOVER = 0xFFF26669;
	private static final int SUCCESS = 0xFF3BA55D;
	private static final int WARNING = 0xFFFAA61A;

	private static final int ENCHANT_PURPLE = 0xFFFF55FF;
	private static final int ENCHANT_BLUE = 0xFFC9CDFB;
	private static final int MAX_DISTANCE = 1000;
	private static final int UNLIMITED_DISTANCE = -1;
	private static final int[] DISTANCE_PRESETS = {100, 250, 500, UNLIMITED_DISTANCE};
	private static final int REROLL_ROW_HEIGHT = 20;

	private final SearchController controller;
	private final List<ContainerRecord> records;
	private final LibrarianRerollController reroll;
	private final List<ResultRow> rows = new ArrayList<>();
	private final List<FilterEntry> filters = new ArrayList<>();
	private final List<ContainerTypeEntry> containerTypes = new ArrayList<>();
	private final List<EnchantmentChoice> allChoices = new ArrayList<>();
	private final List<EnchantmentChoice> visibleChoices = new ArrayList<>();
	private final Set<EnchantmentChoice> selectedChoices = new LinkedHashSet<>();

	private Tab tab = Tab.SEARCH;
	private EditBox searchBox;
	private EditBox filterSearchBox;
	private EditBox rerollSearchBox;
	private String selectedFilter;
	private final Set<String> selectedContainerTypeKeys = new LinkedHashSet<>();
	private int maxDistance = UNLIMITED_DISTANCE;
	private double resultScroll;
	private double filterScroll;
	private double containerTypeScroll;
	private double rerollScroll;
	private boolean draggingDistanceSlider;
	private int distanceDragX;
	private int distanceDragWidth;

	public ContainerSearchScreen(SearchController controller, List<ContainerRecord> records) {
		super(Component.translatable("screen.noobs_container_searcher.title"));
		this.controller = controller;
		this.records = records;
		this.reroll = ContainerSearcherClient.librarianReroll();
		rebuild();
	}

	@Override
	protected void init() {
		searchBox = new EditBox(font, 0, 0, 220, 22, Component.translatable("screen.noobs_container_searcher.search"));
		searchBox.setHint(Component.translatable("screen.noobs_container_searcher.search_hint"));
		searchBox.setBordered(false);
		searchBox.setTextColor(TEXT_PRIMARY);
		searchBox.setResponder(value -> {
			resultScroll = 0;
			rebuild();
		});
		addRenderableWidget(searchBox);

		filterSearchBox = new EditBox(font, 0, 0, 160, 18, Component.translatable("screen.noobs_container_searcher.filter_search"));
		filterSearchBox.setHint(Component.translatable("screen.noobs_container_searcher.filter_search_hint"));
		filterSearchBox.setBordered(false);
		filterSearchBox.setTextColor(TEXT_PRIMARY);
		filterSearchBox.setResponder(value -> {
			filterScroll = 0;
			containerTypeScroll = 0;
			rebuild();
		});
		addRenderableWidget(filterSearchBox);

		rerollSearchBox = new EditBox(font, 0, 0, 220, 22, Component.translatable("screen.noobs_container_searcher.reroll_search"));
		rerollSearchBox.setHint(Component.translatable("screen.noobs_container_searcher.reroll_search_hint"));
		rerollSearchBox.setBordered(false);
		rerollSearchBox.setTextColor(TEXT_PRIMARY);
		rerollSearchBox.setResponder(value -> rebuildChoices());
		addRenderableWidget(rerollSearchBox);

		loadEnchantments();
		positionWidgets();
		switchTab(Tab.SEARCH);
	}

	@Override
	public void resize(int width, int height) {
		String value = searchBox == null ? "" : searchBox.getValue();
		String filterValue = filterSearchBox == null ? "" : filterSearchBox.getValue();
		String rerollValue = rerollSearchBox == null ? "" : rerollSearchBox.getValue();
		super.resize(width, height);
		searchBox.setValue(value);
		filterSearchBox.setValue(filterValue);
		rerollSearchBox.setValue(rerollValue);
		rebuild();
	}

	private void switchTab(Tab next) {
		tab = next;
		searchBox.setVisible(tab == Tab.SEARCH);
		filterSearchBox.setVisible(tab == Tab.SEARCH);
		rerollSearchBox.setVisible(tab == Tab.REROLL);
		EditBox focusTarget = tab == Tab.SEARCH ? searchBox : tab == Tab.REROLL ? rerollSearchBox : null;
		searchBox.setFocused(focusTarget == searchBox);
		filterSearchBox.setFocused(false);
		rerollSearchBox.setFocused(focusTarget == rerollSearchBox);
		setFocused(focusTarget);
	}

	@Override
	protected void renderContent(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, APP_BACKDROP);
		positionWidgets();

		int left = panelLeft();
		int top = panelTop();
		int panelWidth = panelWidth();
		int panelHeight = panelHeight();
		int contentLeft = left + SIDEBAR_WIDTH;
		int contentWidth = panelWidth - SIDEBAR_WIDTH;

		graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BACKGROUND);
		graphics.outline(left, top, panelWidth, panelHeight, PANEL_BORDER);

		renderSidebar(graphics, mouseX, mouseY, left, top, panelHeight);
		graphics.fill(contentLeft, top, contentLeft + 1, top + panelHeight, SIDEBAR_DIVIDER);

		switch (tab) {
			case SEARCH -> renderSearchTab(graphics, mouseX, mouseY, contentLeft, top, contentWidth, panelHeight);
			case REROLL -> renderRerollTab(graphics, mouseX, mouseY, contentLeft, top, contentWidth, panelHeight);
			case SETTINGS -> renderSettingsTab(graphics, mouseX, mouseY, contentLeft, top, contentWidth, panelHeight);
		}

		renderWidgets(mouseX, mouseY, partialTick);
	}


	private void renderSidebar(Gfx graphics, int mouseX, int mouseY, int left, int top, int panelHeight) {
		graphics.fill(left, top, left + SIDEBAR_WIDTH, top + panelHeight, SIDEBAR_BACKGROUND);

		graphics.text(font, title, left + 20, top + 20, TEXT_PRIMARY);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.results", rows.size()), left + 20, top + 31, TEXT_MUTED);
		graphics.fill(left + 16, top + 48, left + SIDEBAR_WIDTH - 16, top + 49, SIDEBAR_DIVIDER);

		int itemY = top + 62;
		itemY = renderSidebarItem(graphics, mouseX, mouseY, left, itemY, Tab.SEARCH,
			Component.translatable("screen.noobs_container_searcher.tab_search"),
			Component.translatable("screen.noobs_container_searcher.tab_search_hint"), ACCENT);
		itemY = renderSidebarItem(graphics, mouseX, mouseY, left, itemY, Tab.REROLL,
			Component.translatable("screen.noobs_container_searcher.tab_reroll"),
			rerollStatusLine(), reroll.state() == LibrarianRerollController.State.IDLE ? ACCENT : SUCCESS);
		renderSidebarItem(graphics, mouseX, mouseY, left, itemY, Tab.SETTINGS,
			Component.translatable("screen.noobs_container_searcher.tab_settings"),
			Component.translatable("screen.noobs_container_searcher.tab_settings_hint"), ACCENT);

		String close = Component.translatable("screen.noobs_container_searcher.close").getString();
		int closeWidth = font.width(close) + 16;
		boolean closeHovered = inside(mouseX, mouseY, left + 16, top + panelHeight - 34, closeWidth, 22);
		graphics.fill(left + 16, top + panelHeight - 34, left + 16 + closeWidth, top + panelHeight - 12, closeHovered ? CARD_HOVER : CARD_BACKGROUND);
		graphics.text(font, close, left + 24, top + panelHeight - 27, closeHovered ? TEXT_PRIMARY : TEXT_SECONDARY);
	}

	private int renderSidebarItem(Gfx graphics, int mouseX, int mouseY, int left, int y, Tab itemTab, Component label, Component hint, int accent) {
		int height = 40;
		int x = left + 12;
		int width = SIDEBAR_WIDTH - 24;
		boolean active = tab == itemTab;
		boolean hovered = inside(mouseX, mouseY, x, y, width, height);
		graphics.fill(x, y, x + width, y + height, active ? ACCENT_SOFT : hovered ? CARD_HOVER : 0);
		graphics.fill(x, y, x + 3, y + height, active ? accent : 0);
		graphics.text(font, label, x + 14, y + 9, active ? TEXT_PRIMARY : TEXT_SECONDARY);
		graphics.text(font, hint, x + 14, y + 21, active ? 0xFFC7CBFF : TEXT_MUTED);
		return y + height + 6;
	}

	private Component rerollStatusLine() {
		return switch (reroll.state()) {
			case IDLE -> Component.translatable("screen.noobs_container_searcher.tab_reroll_hint");
			case SELECT_VILLAGER -> Component.translatable("screen.noobs_container_searcher.reroll_select_villager");
			case SELECT_LECTERN_POSITION -> Component.translatable("screen.noobs_container_searcher.reroll_select_block");
			case SUCCESS -> Component.translatable("screen.noobs_container_searcher.reroll_status_success");
			default -> Component.translatable("screen.noobs_container_searcher.reroll_status_running", reroll.attempts());
		};
	}


	private void renderSearchTab(Gfx graphics, int mouseX, int mouseY, int left, int top, int width, int panelHeight) {
		int filterWidth = Math.min(190, Math.max(140, width / 3));
		int resultLeft = left + filterWidth + 20;
		int resultWidth = left + width - resultLeft - 16;

		graphics.text(font, Component.translatable("screen.noobs_container_searcher.filters"), left + 16, top + 32, TEXT_SECONDARY);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.container_types"), left + 16, containerTypeTitleY(top, panelHeight), TEXT_SECONDARY);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.search"), resultLeft, top + 10, TEXT_SECONDARY);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.results", rows.size()), resultLeft, top + 46, TEXT_SECONDARY);

		renderTextField(graphics, filterSearchBox, mouseX, mouseY);
		renderFilters(graphics, mouseX, mouseY, left + 16, filterListTop(top), filterWidth - 8, filterListHeight(top, panelHeight));
		renderContainerTypes(graphics, mouseX, mouseY, left + 16, containerTypeListTop(top, panelHeight), filterWidth - 8, containerTypeListHeight(top, panelHeight));

		renderTextField(graphics, searchBox, mouseX, mouseY);
		renderDistanceSlider(graphics, mouseX, mouseY, resultLeft, resultsDistanceSliderY(top), resultWidth);
		renderResults(graphics, mouseX, mouseY, resultLeft, resultsTop(top), resultWidth, resultsHeight(top, panelHeight));
	}


	private void renderRerollTab(Gfx graphics, int mouseX, int mouseY, int left, int top, int width, int panelHeight) {
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.reroll_book_count", visibleChoices.size()), left + 16, top + 46, TEXT_SECONDARY);
		renderTextField(graphics, rerollSearchBox, mouseX, mouseY);

		int listX = left + 16;
		int listY = top + 82;
		int listWidth = width - 32;
		int listHeight = panelHeight - 150;
		graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
		int start = Math.max(0, (int) (rerollScroll / REROLL_ROW_HEIGHT));
		int offset = listY - (int) (rerollScroll % REROLL_ROW_HEIGHT);
		for (int i = start; i < visibleChoices.size(); i++) {
			int rowY = offset + (i - start) * REROLL_ROW_HEIGHT;
			if (rowY >= listY + listHeight) {
				break;
			}
			EnchantmentChoice choice = visibleChoices.get(i);
			boolean hovered = inside(mouseX, mouseY, listX, rowY, listWidth, REROLL_ROW_HEIGHT - 2);
			boolean selected = selectedChoices.contains(choice);
			graphics.fill(listX, rowY, listX + listWidth, rowY + REROLL_ROW_HEIGHT - 2, selected ? ACCENT_SOFT : hovered ? CARD_HOVER : i % 2 == 0 ? CARD_BACKGROUND : CARD_BACKGROUND_ALT);
			graphics.fill(listX, rowY, listX + 3, rowY + REROLL_ROW_HEIGHT - 2, selected ? ACCENT : 0);
			graphics.text(font, choice.name, listX + 12, rowY + 6, selected ? TEXT_PRIMARY : TEXT_SECONDARY);
			String idText = choice.id.toString();
			graphics.text(font, idText, listX + listWidth - font.width(idText) - 10, rowY + 6, TEXT_MUTED);
		}
		graphics.disableScissor();
		if (visibleChoices.isEmpty()) {
			graphics.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), listX + 4, listY + 6, TEXT_MUTED);
		}

		boolean canSelect = !selectedChoices.isEmpty();
		String selection = selectedChoices.isEmpty()
			? Component.translatable("screen.noobs_container_searcher.no_book_selected").getString()
			: selectedChoices.size() == 1
				? Component.translatable("screen.noobs_container_searcher.selected_book", selectedChoices.iterator().next().name).getString()
				: Component.translatable("screen.noobs_container_searcher.selected_books", selectedChoices.size()).getString();
		int clearWidth = font.width(Component.translatable("screen.noobs_container_searcher.clear_selection").getString()) + 16;
		graphics.text(font, trim(selection, listWidth - clearWidth - 10), listX, listY + listHeight + 8, TEXT_SECONDARY);
		renderButton(graphics, mouseX, mouseY, listX + listWidth - clearWidth, listY + listHeight + 2, clearWidth, 18,
			Component.translatable("screen.noobs_container_searcher.clear_selection"), CARD_HOVER, 0x9A3A3C46, canSelect);
		String multiHint = Component.translatable("screen.noobs_container_searcher.reroll_multi_hint").getString();
		graphics.text(font, multiHint, listX, listY + listHeight + 22, TEXT_MUTED);

		int buttonY = top + panelHeight - 36;
		renderButton(graphics, mouseX, mouseY, listX, buttonY, 170, 24,
			Component.translatable("screen.noobs_container_searcher.select_villager"), ACCENT, ACCENT_HOVER, canSelect);
		renderButton(graphics, mouseX, mouseY, listX + 180, buttonY, 140, 24,
			Component.translatable("screen.noobs_container_searcher.stop_reroll"), DANGER, DANGER_HOVER,
			reroll.state() != LibrarianRerollController.State.IDLE);
	}


	private void renderSettingsTab(Gfx graphics, int mouseX, int mouseY, int left, int top, int width, int panelHeight) {
		int cardX = left + 16;
		int cardWidth = width - 32;
		int cardY = top + 50;

		graphics.text(font, Component.translatable("screen.noobs_container_searcher.tab_settings"), cardX, top + 22, TEXT_PRIMARY);

		int distanceCardHeight = 88;
		graphics.fill(cardX, cardY, cardX + cardWidth, cardY + distanceCardHeight, CARD_BACKGROUND);
		graphics.fill(cardX, cardY, cardX + 3, cardY + distanceCardHeight, ACCENT);
		renderDistanceSlider(graphics, mouseX, mouseY, cardX + 16, cardY + 14, cardWidth - 32);
		renderDistanceChips(graphics, mouseX, mouseY, cardX + 16, cardY + 52, cardWidth - 32);

		int dangerY = cardY + distanceCardHeight + 16;
		graphics.fill(cardX, dangerY, cardX + cardWidth, dangerY + 54, CARD_BACKGROUND);
		graphics.fill(cardX, dangerY, cardX + 3, dangerY + 54, DANGER);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.clear_data_title"), cardX + 16, dangerY + 10, TEXT_PRIMARY);
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.clear_data_hint"), cardX + 16, dangerY + 21, TEXT_MUTED);
		renderButton(graphics, mouseX, mouseY, cardX + cardWidth - 146, dangerY + 15, 130, 24,
			Component.translatable("screen.noobs_container_searcher.clear_data"), DANGER, DANGER_HOVER, true);
	}


	private void renderTextField(Gfx graphics, EditBox box, int mouseX, int mouseY) {
		int x = box.getX();
		int y = box.getY();
		int w = box.getWidth();
		int h = box.getHeight();
		boolean focused = box.isFocused();
		graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, CONTROL_BACKGROUND);
		graphics.outline(x - 2, y - 2, w + 4, h + 4, focused ? ACCENT : CONTROL_BORDER);
		graphics.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, focused ? ACCENT : CONTROL_BORDER);
	}

	private void renderButton(Gfx graphics, int mouseX, int mouseY, int x, int y, int width, int height, Component label, int color, int hoverColor, boolean enabled) {
		boolean hovered = enabled && inside(mouseX, mouseY, x, y, width, height);
		int fill = !enabled ? 0xFF25262C : hovered ? hoverColor : color;
		graphics.fill(x, y, x + width, y + height, fill);
		int textColor = enabled ? 0xFFFFFFFF : TEXT_MUTED;
		graphics.centeredText(font, label, x + width / 2, y + (height - 8) / 2, textColor);
	}


	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != 0) {
			return false;
		}
		double mouseX = event.x();
		double mouseY = event.y();
		int left = panelLeft();
		int top = panelTop();
		int panelWidth = panelWidth();
		int panelHeight = panelHeight();

		if (handleSidebarClick(mouseX, mouseY, left, top, panelHeight)) {
			return true;
		}

		int contentLeft = left + SIDEBAR_WIDTH;
		int contentWidth = panelWidth - SIDEBAR_WIDTH;
		return switch (tab) {
			case SEARCH -> handleSearchClick(mouseX, mouseY, contentLeft, top, contentWidth);
			case REROLL -> handleRerollClick(mouseX, mouseY, contentLeft, top, contentWidth, panelHeight, event.hasControlDown());
			case SETTINGS -> handleSettingsClick(mouseX, mouseY, contentLeft, top, contentWidth);
		};
	}

	private boolean handleSidebarClick(double mouseX, double mouseY, int left, int top, int panelHeight) {
		int x = left + 12;
		int width = SIDEBAR_WIDTH - 24;
		int itemY = top + 62;
		if (inside(mouseX, mouseY, x, itemY, width, 40)) {
			switchTab(Tab.SEARCH);
			return true;
		}
		itemY += 46;
		if (inside(mouseX, mouseY, x, itemY, width, 40)) {
			switchTab(Tab.REROLL);
			return true;
		}
		itemY += 46;
		if (inside(mouseX, mouseY, x, itemY, width, 40)) {
			switchTab(Tab.SETTINGS);
			return true;
		}
		String close = Component.translatable("screen.noobs_container_searcher.close").getString();
		int closeWidth = font.width(close) + 16;
		if (inside(mouseX, mouseY, left + 16, top + panelHeight - 34, closeWidth, 22)) {
			onClose();
			return true;
		}
		return false;
	}

	private boolean handleSearchClick(double mouseX, double mouseY, int left, int top, int width) {
		int filterWidth = Math.min(190, Math.max(140, width / 3));
		int resultLeftForSlider = left + filterWidth + 20;
		int resultWidthForSlider = left + width - resultLeftForSlider - 16;
		int sliderLabelY = resultsDistanceSliderY(top);
		int trackY = sliderLabelY + 15;
		int pillWidth = distancePillWidth();
		if (inside(mouseX, mouseY, distanceSliderX(resultLeftForSlider), trackY, distanceSliderWidth(resultWidthForSlider, pillWidth), 16)) {
			distanceDragX = resultLeftForSlider;
			distanceDragWidth = resultWidthForSlider;
			updateDistanceSlider(mouseX, distanceDragX, distanceDragWidth, pillWidth);
			draggingDistanceSlider = true;
			return true;
		}
		int filterX = left + 16;
		int filterListTop = filterListTop(top);
		int filterListHeight = filterListHeight(top, panelHeight());
		int containerTypeListTop = containerTypeListTop(top, panelHeight());
		int containerTypeListHeight = containerTypeListHeight(top, panelHeight());
		int resultLeft = left + filterWidth + 20;
		int resultWidth = left + width - resultLeft - 16;

		if (inside(mouseX, mouseY, filterX, filterListTop, filterWidth - 8, filterListHeight)) {
			int index = (int) ((mouseY - filterListTop + filterScroll) / FILTER_ROW_HEIGHT);
			if (index >= 0 && index < filters.size()) {
				FilterEntry filter = filters.get(index);
				selectedFilter = filter.id.equals(selectedFilter) ? null : filter.id;
				resultScroll = 0;
				rebuild();
				return true;
			}
		}

		if (inside(mouseX, mouseY, filterX, containerTypeListTop, filterWidth - 8, containerTypeListHeight)) {
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

		int listTop = resultsTop(top);
		int listHeight = resultsHeight(top, panelHeight());
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

	private boolean handleRerollClick(double mouseX, double mouseY, int left, int top, int width, int panelHeight, boolean controlDown) {
		int listX = left + 16;
		int listY = top + 82;
		int listWidth = width - 32;
		int listHeight = panelHeight - 150;
		if (inside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
			int index = (int) ((mouseY - listY + rerollScroll) / REROLL_ROW_HEIGHT);
			if (index >= 0 && index < visibleChoices.size()) {
				EnchantmentChoice choice = visibleChoices.get(index);
				if (controlDown) {
					if (!selectedChoices.remove(choice)) {
						selectedChoices.add(choice);
					}
				} else {
					selectedChoices.clear();
					selectedChoices.add(choice);
				}
				return true;
			}
		}
		int clearWidth = font.width(Component.translatable("screen.noobs_container_searcher.clear_selection").getString()) + 16;
		if (!selectedChoices.isEmpty() && inside(mouseX, mouseY, listX + listWidth - clearWidth, listY + listHeight + 2, clearWidth, 18)) {
			selectedChoices.clear();
			return true;
		}
		int buttonY = top + panelHeight - 36;
		if (!selectedChoices.isEmpty() && inside(mouseX, mouseY, listX, buttonY, 170, 24)) {
			beginSelection();
			return true;
		}
		if (reroll.state() != LibrarianRerollController.State.IDLE && inside(mouseX, mouseY, listX + 180, buttonY, 140, 24)) {
			reroll.stopByUser();
			return true;
		}
		return false;
	}

	private String distanceValueText() {
		return maxDistance == UNLIMITED_DISTANCE
			? Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString()
			: Component.translatable("screen.noobs_container_searcher.distance_value", maxDistance).getString();
	}

	private int distancePillWidth() {
		String longest = Component.translatable("screen.noobs_container_searcher.distance_value", MAX_DISTANCE).getString();
		String unlimited = Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString();
		return Math.max(font.width(longest), font.width(unlimited)) + 16;
	}

	private boolean handleSettingsClick(double mouseX, double mouseY, int left, int top, int width) {
		int cardX = left + 16;
		int cardWidth = width - 32;
		int cardY = top + 50;
		int sliderLabelY = cardY + 14;
		int trackY = sliderLabelY + 15;
		int pillWidth = distancePillWidth();
		if (inside(mouseX, mouseY, distanceSliderX(cardX + 16), trackY, distanceSliderWidth(cardWidth - 32, pillWidth), 16)) {
			distanceDragX = cardX + 16;
			distanceDragWidth = cardWidth - 32;
			updateDistanceSlider(mouseX, distanceDragX, distanceDragWidth, pillWidth);
			draggingDistanceSlider = true;
			return true;
		}
		int chipY = cardY + 52;
		int chipPreset = distanceChipAt(mouseX, mouseY, cardX + 16, chipY);
		if (chipPreset != Integer.MIN_VALUE) {
			maxDistance = chipPreset;
			resultScroll = 0;
			rebuild();
			return true;
		}
		int dangerY = cardY + 88 + 16;
		if (inside(mouseX, mouseY, cardX + cardWidth - 146, dangerY + 15, 130, 24)) {
			confirmClearData();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingDistanceSlider) {
			int pillWidth = distancePillWidth();
			updateDistanceSlider(event.x(), distanceDragX, distanceDragWidth, pillWidth);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingDistanceSlider = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int left = panelLeft() + SIDEBAR_WIDTH;
		int width = panelLeft() + panelWidth() - left;
		int top = panelTop();
		int panelHeight = panelHeight();

		if (tab == Tab.SEARCH) {
			int filterWidth = Math.min(190, Math.max(140, width / 3));
			int filterListTop = filterListTop(top);
			int filterListHeight = filterListHeight(top, panelHeight);
			int containerTypeListTop = containerTypeListTop(top, panelHeight);
			int containerTypeListHeight = containerTypeListHeight(top, panelHeight);
			int resultLeft = left + filterWidth + 20;
			int listTop = resultsTop(top);
			int listHeight = resultsHeight(top, panelHeight);

			if (inside(mouseX, mouseY, left + 16, filterListTop, filterWidth - 8, filterListHeight)) {
				filterScroll = clampScroll(filterScroll - verticalAmount * 18, filters.size() * FILTER_ROW_HEIGHT, filterListHeight);
				return true;
			}
			if (inside(mouseX, mouseY, left + 16, containerTypeListTop, filterWidth - 8, containerTypeListHeight)) {
				containerTypeScroll = clampScroll(containerTypeScroll - verticalAmount * 18, containerTypes.size() * FILTER_ROW_HEIGHT, containerTypeListHeight);
				return true;
			}
			if (inside(mouseX, mouseY, resultLeft, listTop, left + width - resultLeft - 16, listHeight)) {
				resultScroll = clampScroll(resultScroll - verticalAmount * ROW_HEIGHT, rows.size() * ROW_HEIGHT, listHeight);
				return true;
			}
		} else if (tab == Tab.REROLL) {
			int listY = top + 82;
			int listHeight = panelHeight - 150;
			if (inside(mouseX, mouseY, left + 16, listY, width - 32, listHeight)) {
				rerollScroll = clampScroll(rerollScroll - verticalAmount * REROLL_ROW_HEIGHT, visibleChoices.size() * REROLL_ROW_HEIGHT, listHeight);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (tab == Tab.SEARCH) {
			if (searchBox.keyPressed(event)) {
				return true;
			}
			if (filterSearchBox.keyPressed(event)) {
				return true;
			}
		}
		if (tab == Tab.REROLL && rerollSearchBox.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Compat.setScreen(minecraft, null);
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

	private void beginSelection() {
		if (selectedChoices.isEmpty()) {
			return;
		}
		reroll.beginSelection(selectedChoices.stream()
			.map(choice -> new LibrarianRerollController.TargetBook(choice.id, choice.level))
			.toList());
	}


	private void loadEnchantments() {
		allChoices.clear();
		if (minecraft == null || minecraft.level == null) {
			return;
		}
		var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (var entry : registry.entrySet()) {
			Holder<Enchantment> holder = registry.wrapAsHolder(entry.getValue());
			if (!holder.is(EnchantmentTags.TRADEABLE)) {
				continue;
			}
			Identifier id = entry.getKey().identifier();
			for (int level = entry.getValue().getMinLevel(); level <= entry.getValue().getMaxLevel(); level++) {
				String name = Enchantment.getFullname(holder, level).getString();
				allChoices.add(new EnchantmentChoice(id, level, name));
			}
		}
		allChoices.sort(Comparator.comparing(choice -> normalizedSearch(choice.name)));
		rebuildChoices();
	}

	private void rebuildChoices() {
		String query = rerollSearchBox == null ? "" : normalizedSearch(rerollSearchBox.getValue());
		visibleChoices.clear();
		for (EnchantmentChoice choice : allChoices) {
			if (query.isEmpty() || normalizedSearch(choice.name + " " + choice.id).contains(query)) {
				visibleChoices.add(choice);
			}
		}
		rerollScroll = clampScroll(rerollScroll, visibleChoices.size() * REROLL_ROW_HEIGHT, panelHeight() - 150);
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
		int top = panelTop();
		int panelHeight = panelHeight();
		resultScroll = clampScroll(resultScroll, rows.size() * ROW_HEIGHT, resultsHeight(top, panelHeight));
		filterScroll = clampScroll(filterScroll, filters.size() * FILTER_ROW_HEIGHT, filterListHeight(top, panelHeight));
		containerTypeScroll = clampScroll(containerTypeScroll, containerTypes.size() * FILTER_ROW_HEIGHT, containerTypeListHeight(top, panelHeight));
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
			graphics.fill(x, rowY, x + width, rowY + FILTER_ROW_HEIGHT - 1, selected ? ACCENT_SOFT : hovered ? CARD_HOVER : CARD_BACKGROUND);
			graphics.fill(x, rowY, x + 2, rowY + FILTER_ROW_HEIGHT - 1, selected ? accent : 0);
			graphics.text(font, trim(filter.name + " (" + filter.count + ")", width - 10), x + 8, rowY + 4, selected ? TEXT_PRIMARY : accent);
		}
		graphics.disableScissor();
		if (filters.isEmpty()) {
			graphics.text(font, Component.translatable("screen.noobs_container_searcher.no_filters"), x + 4, y + 6, TEXT_MUTED);
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
			int accent = selected ? 0xFF55D6D6 : TEXT_SECONDARY;
			graphics.fill(x, rowY, x + width, rowY + FILTER_ROW_HEIGHT - 1, selected ? 0x2655D6D6 : hovered ? CARD_HOVER : CARD_BACKGROUND);
			graphics.fill(x, rowY, x + 2, rowY + FILTER_ROW_HEIGHT - 1, selected ? accent : 0);
			graphics.text(font, trim(type.name + " (" + type.count + ")", width - 10), x + 8, rowY + 4, selected ? TEXT_PRIMARY : accent);
		}
		graphics.disableScissor();
		if (containerTypes.isEmpty()) {
			graphics.text(font, Component.translatable("screen.noobs_container_searcher.no_container_types"), x + 4, y + 6, TEXT_MUTED);
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
			graphics.fill(x, rowY, x + width, rowY + ROW_HEIGHT - 1, hovered ? CARD_HOVER : i % 2 == 0 ? CARD_BACKGROUND : CARD_BACKGROUND_ALT);
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(row.itemId));
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
			graphics.text(font, trim(detail, textWidth), x + 26, rowY + 12, row.item.enchanted ? ENCHANT_BLUE : TEXT_SECONDARY);
			graphics.text(font, trim(row.itemId, idWidth), x + width - idWidth - 4, rowY + 7, TEXT_MUTED);
		}
		graphics.disableScissor();
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), x + 4, y + 6, TEXT_MUTED);
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
			case "flag:unenchanted" -> TEXT_SECONDARY;
			case "flag:lore" -> WARNING;
			case "flag:damaged" -> DANGER;
			case "flag:full_durability" -> SUCCESS;
			case "flag:low_durability" -> WARNING;
			default -> TEXT_PRIMARY;
		};
	}

	private static Component containerName(String storedType) {
		if (storedType != null && storedType.startsWith("villager:")) {
			String profession = storedType.substring("villager:".length());
			Identifier id = Identifier.tryParse(profession);
			String path = id == null ? profession : id.getPath();
			return Component.translatable(
				"screen.noobs_container_searcher.villager_type",
				Component.translatable("entity.minecraft.villager." + path)
			);
		}
		Identifier blockId = Identifier.tryParse(storedType);
		if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
			return BuiltInRegistries.BLOCK.getValue(blockId).getName();
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
				default -> TEXT_PRIMARY;
			};
		}
		return item.enchanted ? ENCHANT_PURPLE : TEXT_PRIMARY;
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
			Tag tag = TagParser.create(ops).parseFully(record.stackData);
			return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
		} catch (Exception ignored) {
			return ItemStack.EMPTY;
		}
	}

	private static String itemName(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
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
		Identifier id = Identifier.tryParse(enchantmentId);
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
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return dimension;
		}
		String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
		String translated = Component.translatable(key).getString();
		return translated.equals(key) ? id.getPath() : translated;
	}


	private void positionWidgets() {
		int left = panelLeft() + SIDEBAR_WIDTH;
		int top = panelTop();
		int width = panelLeft() + panelWidth() - left;
		int filterWidth = Math.min(190, Math.max(140, width / 3));
		int resultLeft = left + filterWidth + 20;
		int resultWidth = left + width - resultLeft - 16;

		searchBox.setX(resultLeft);
		searchBox.setY(top + 20);
		searchBox.setWidth(resultWidth);
		searchBox.setHeight(20);

		filterSearchBox.setX(left + 16);
		filterSearchBox.setY(top + 8);
		filterSearchBox.setWidth(filterWidth - 8);
		filterSearchBox.setHeight(18);

		rerollSearchBox.setX(left + 16);
		rerollSearchBox.setY(top + 56);
		rerollSearchBox.setWidth(Math.min(300, width - 32));
		rerollSearchBox.setHeight(20);
	}

	private int filterListTop(int top) {
		return top + 44;
	}

	private int filterListHeight(int top, int panelHeight) {
		return Math.max(34, containerTypeTitleY(top, panelHeight) - filterListTop(top) - 6);
	}

	private int containerTypeTitleY(int top, int panelHeight) {
		return top + panelHeight - containerTypeSectionHeight(panelHeight);
	}

	private int containerTypeListTop(int top, int panelHeight) {
		return containerTypeTitleY(top, panelHeight) + 14;
	}

	private int containerTypeListHeight(int top, int panelHeight) {
		return Math.max(18, top + panelHeight - containerTypeListTop(top, panelHeight) - 10);
	}

	private int containerTypeSectionHeight(int panelHeight) {
		return Math.min(94, Math.max(62, panelHeight / 3));
	}

	private int resultsTop(int top) {
		return top + 118;
	}

	private int resultsHeight(int top, int panelHeight) {
		return panelHeight - 128;
	}

	private int resultsDistanceSliderY(int top) {
		return top + 86;
	}

	private static int distanceSliderX(int x) {
		return x;
	}

	private static int distanceSliderWidth(int width, int pillWidth) {
		return Math.max(60, width - pillWidth - 10);
	}

	private void renderDistanceSlider(Gfx graphics, int mouseX, int mouseY, int x, int y, int width) {
		String label = Component.translatable("screen.noobs_container_searcher.distance_filter").getString();
		String value = maxDistance == UNLIMITED_DISTANCE
			? Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString()
			: Component.translatable("screen.noobs_container_searcher.distance_value", maxDistance).getString();
		graphics.text(font, label, x, y, TEXT_SECONDARY);

		int pillWidth = distancePillWidth();
		int sliderX = distanceSliderX(x);
		int sliderWidth = distanceSliderWidth(width, pillWidth);
		int trackY = y + 15;
		boolean hovered = inside(mouseX, mouseY, sliderX, trackY, sliderWidth, 16);

		graphics.fill(sliderX, trackY + 6, sliderX + sliderWidth, trackY + 10, CONTROL_BACKGROUND);
		graphics.outline(sliderX, trackY + 6, sliderWidth, 4, CONTROL_BORDER);
		int knobX = distanceKnobX(sliderX, sliderWidth);
		graphics.fill(sliderX, trackY + 6, knobX, trackY + 10, ACCENT);
		graphics.fill(knobX - 3, trackY + 2, knobX + 4, trackY + 14, hovered ? TEXT_PRIMARY : ACCENT_HOVER);
		graphics.outline(knobX - 3, trackY + 2, 7, 12, 0xFF15161A);

		int pillX = sliderX + sliderWidth + 10;
		graphics.fill(pillX, trackY, pillX + pillWidth, trackY + 16, ACCENT_SOFT);
		graphics.text(font, value, pillX + 8, trackY + 4, TEXT_PRIMARY);
	}

	private void renderDistanceChips(Gfx graphics, int mouseX, int mouseY, int x, int y, int width) {
		int chipX = x;
		for (int preset : DISTANCE_PRESETS) {
			String label = preset == UNLIMITED_DISTANCE
				? Component.translatable("screen.noobs_container_searcher.distance_chip_unlimited").getString()
				: Integer.toString(preset);
			int chipWidth = font.width(label) + 16;
			boolean active = maxDistance == preset;
			boolean hovered = inside(mouseX, mouseY, chipX, y, chipWidth, 20);
			graphics.fill(chipX, y, chipX + chipWidth, y + 20, active ? ACCENT : hovered ? CARD_HOVER : CARD_BACKGROUND_ALT);
			graphics.text(font, label, chipX + 8, y + 6, active ? TEXT_PRIMARY : TEXT_SECONDARY);
			chipX += chipWidth + 8;
		}
	}

	private int distanceChipAt(double mouseX, double mouseY, int x, int y) {
		int chipX = x;
		for (int preset : DISTANCE_PRESETS) {
			String label = preset == UNLIMITED_DISTANCE
				? Component.translatable("screen.noobs_container_searcher.distance_chip_unlimited").getString()
				: Integer.toString(preset);
			int chipWidth = font.width(label) + 16;
			if (inside(mouseX, mouseY, chipX, y, chipWidth, 20)) {
				return preset;
			}
			chipX += chipWidth + 8;
		}
		return Integer.MIN_VALUE;
	}

	private int distanceKnobX(int sliderX, int sliderWidth) {
		if (maxDistance == UNLIMITED_DISTANCE) {
			return sliderX + sliderWidth;
		}
		double ratio = Math.max(0, Math.min(1, maxDistance / (double) MAX_DISTANCE));
		return sliderX + (int) Math.round(ratio * sliderWidth);
	}

	private void updateDistanceSlider(double mouseX, int x, int width, int pillWidth) {
		int sliderX = distanceSliderX(x);
		int sliderWidth = distanceSliderWidth(width, pillWidth);
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
		return (int) Math.round(width * 0.85D);
	}

	private int panelHeight() {
		return (int) Math.round(height * 0.85D);
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

	private record EnchantmentChoice(Identifier id, int level, String name) { }
}
