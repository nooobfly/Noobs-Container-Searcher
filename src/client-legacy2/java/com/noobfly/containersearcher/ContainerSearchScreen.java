package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.CompatScreen;
import com.noobfly.containersearcher.compat.Gfx;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
import java.util.function.DoubleConsumer;

public final class ContainerSearchScreen extends CompatScreen {
	private enum Tab { SEARCH, REROLL, SETTINGS }

	private enum Sort { NAME, DISTANCE }

	private static final int RESULT_HEIGHT = 28;
	private static final int RESULT_STRIDE = 31;
	private static final int ENCHANT_ROW = 16;
	private static final int REROLL_CARD_HEIGHT = 26;
	private static final int REROLL_CARD_STRIDE = 29;
	private static final int HEADER_HEIGHT = 34;
	private static final int FOOTER_HEIGHT = 22;

	private static final int BACKDROP = 0xB0080910;
	private static final int PANEL = 0xFF111218;
	private static final int HEADER = 0xFF171820;
	private static final int SURFACE = 0xFF1C1D26;
	private static final int SURFACE_HI = 0xFF23242F;
	private static final int INPUT_BG = 0xFF0D0E13;
	private static final int BORDER = 0xFF2C2E3B;
	private static final int BORDER_HI = 0xFF3A3D4F;

	private static final int TEXT_PRIMARY = 0xFFF2F3F5;
	private static final int TEXT_SECONDARY = 0xFFA9ABB8;
	private static final int TEXT_MUTED = 0xFF666878;

	private static final int ACCENT = 0xFF6C7BFF;
	private static final int ACCENT_HOVER = 0xFF8C99FF;
	private static final int ACCENT_DARK = 0xFF4A56C8;
	private static final int ACCENT_BG = 0xFF23264A;
	private static final int DANGER = 0xFFED4245;
	private static final int DANGER_HOVER = 0xFFF26669;
	private static final int DANGER_DARK = 0xFFA82E31;
	private static final int DANGER_BG = 0xFF3A1D22;
	private static final int SUCCESS = 0xFF3BA55D;
	private static final int WARNING = 0xFFFAA61A;
	private static final int ENCHANT_PURPLE = 0xFFFF55FF;
	private static final int CONTAINER_CYAN = 0xFF55D6D6;

	private static final int MAX_DISTANCE = 1000;
	private static final int UNLIMITED_DISTANCE = -1;
	private static final int[] DISTANCE_PRESETS = {100, 250, 500, UNLIMITED_DISTANCE};

	private static final String[] ICON_SEARCH = {"..XXXX...", ".X....X..", "X......X.", "X......X.", "X......X.", ".X....X..", "..XXXXXX.", "......XXX", "........X"};
	private static final String[] ICON_X = {"X...X", ".X.X.", "..X..", ".X.X.", "X...X"};
	private static final String[] ICON_CHECK = {".....X", "....X.", "X..X..", ".XX...", "..X..."};
	private static final String[] ICON_CHEVRON = {"XXXXX", ".XXX.", "..X.."};
	private static final String[] ICON_ARROW = {"..X..", "...X.", "XXXXX", "...X.", "..X.."};
	private static final String[] ICON_PIN = {".XXX.", "XXXXX", "XXXXX", ".XXX.", "..X..", "..X.."};

	private record Hit(int x, int y, int w, int h, Runnable action) {
		private boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	private record ScrollArea(int x, int y, int w, int h, DoubleConsumer onScroll) { }

	private record ChipSpec(String label, int count, int dot, boolean active, boolean closable, Runnable click) { }

	private final SearchController controller;
	private final List<ContainerRecord> records;
	private final LibrarianRerollController reroll;
	private final List<ResultRow> rows = new ArrayList<>();
	private final List<FilterEntry> filters = new ArrayList<>();
	private final List<ContainerTypeEntry> containerTypes = new ArrayList<>();
	private final List<EnchantmentChoice> allChoices = new ArrayList<>();
	private final List<EnchantmentChoice> visibleChoices = new ArrayList<>();
	private final Set<EnchantmentChoice> selectedChoices = new LinkedHashSet<>();
	private final List<Hit> hits = new ArrayList<>();
	private final List<ScrollArea> scrollAreas = new ArrayList<>();
	private final Map<String, String> selectedTypeLabels = new LinkedHashMap<>();

	private Tab tab = Tab.SEARCH;
	private Sort sort = Sort.NAME;
	private EditBox searchBox;
	private EditBox filterSearchBox;
	private EditBox rerollSearchBox;
	private String selectedFilter;
	private String selectedFilterLabel = "";
	private final Set<String> selectedContainerTypeKeys = new LinkedHashSet<>();
	private int maxDistance = UNLIMITED_DISTANCE;
	private int resultContainerCount;
	private double resultScroll;
	private double filterScroll;
	private double containerTypeScroll;
	private double rerollScroll;
	private boolean draggingDistanceSlider;
	private int sliderX;
	private int sliderWidth;
	private int mx = -1;
	private int my = -1;
	private boolean ctrlDown;

	public ContainerSearchScreen(SearchController controller, List<ContainerRecord> records) {
		super(Component.translatable("screen.noobs_container_searcher.title"));
		this.controller = controller;
		this.records = records;
		ITEM_NAME_CACHE.clear();
		ENCHANTMENT_NAME_CACHE.clear();
		this.reroll = ContainerSearcherClient.librarianReroll();
		rebuild();
	}

	@Override
	protected void init() {
		searchBox = newBox(Component.translatable("screen.noobs_container_searcher.search"), "screen.noobs_container_searcher.search_hint");
		searchBox.setResponder(value -> {
			resultScroll = 0;
			rebuild();
		});
		filterSearchBox = newBox(Component.translatable("screen.noobs_container_searcher.filter_search"), "screen.noobs_container_searcher.filter_search_hint");
		filterSearchBox.setResponder(value -> {
			filterScroll = 0;
			containerTypeScroll = 0;
			rebuild();
		});
		rerollSearchBox = newBox(Component.translatable("screen.noobs_container_searcher.reroll_search"), "screen.noobs_container_searcher.reroll_search_hint");
		rerollSearchBox.setResponder(value -> {
			rerollScroll = 0;
			rebuildChoices();
		});

		loadEnchantments();
		switchTab(Tab.SEARCH);
	}

	private EditBox newBox(Component name, String hintKey) {
		EditBox box = new EditBox(font, 0, 0, 100, 10, name);
		box.setHint(Component.translatable(hintKey));
		box.setBordered(false);
		box.setTextColor(TEXT_PRIMARY);
		box.setMaxLength(128);
		addRenderableWidget(box);
		return box;
	}

	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		String value = searchBox == null ? "" : searchBox.getValue();
		String filterValue = filterSearchBox == null ? "" : filterSearchBox.getValue();
		String rerollValue = rerollSearchBox == null ? "" : rerollSearchBox.getValue();
		super.resize(minecraft, width, height);
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
		focusBox(tab == Tab.SEARCH ? searchBox : tab == Tab.REROLL ? rerollSearchBox : null);
	}

	private void focusBox(EditBox target) {
		searchBox.setFocused(target == searchBox);
		filterSearchBox.setFocused(target == filterSearchBox);
		rerollSearchBox.setFocused(target == rerollSearchBox);
		setFocused(target);
	}

	@Override
	protected void renderContent(Gfx g, int mouseX, int mouseY, float partialTick) {
		mx = mouseX;
		my = mouseY;
		hits.clear();
		scrollAreas.clear();
		g.fill(0, 0, width, height, BACKDROP);

		int pw = panelWidth();
		int ph = panelHeight();
		int left = panelLeft();
		int top = panelTop();
		rfill(g, left - 1, top - 1, pw + 2, ph + 2, 0xFF000000);
		rfill(g, left, top, pw, ph, BORDER);
		rfill(g, left + 1, top + 1, pw - 2, ph - 2, PANEL);

		renderHeader(g, left, top, pw);
		int footerY = top + ph - FOOTER_HEIGHT;
		g.fill(left + 1, footerY, left + pw - 1, footerY + 1, BORDER);
		g.fill(left + 1, footerY + 1, left + pw - 1, top + ph - 1, HEADER);

		int bx = left + 12;
		int by = top + HEADER_HEIGHT + 4;
		int bw = pw - 24;
		int bh = footerY - by - 6;
		g.enableScissor(left + 1, top + HEADER_HEIGHT, left + pw - 1, top + ph - 1);
		switch (tab) {
			case SEARCH -> renderSearchTab(g, bx, by, bw, bh, footerY);
			case REROLL -> renderRerollTab(g, bx, by, bw, bh, footerY);
			case SETTINGS -> renderSettingsTab(g, bx, by, bw, bh, footerY);
		}
		g.disableScissor();
		renderWidgets(mouseX, mouseY, partialTick);
	}

	private void renderHeader(Gfx g, int left, int top, int pw) {
		g.fill(left + 1, top + 1, left + pw - 1, top + HEADER_HEIGHT - 1, HEADER);
		g.fill(left + 1, top + HEADER_HEIGHT - 1, left + pw - 1, top + HEADER_HEIGHT, BORDER);
		g.fakeItem(new ItemStack(Items.SPYGLASS), left + 12, top + 9);
		Component bold = title.copy().withStyle(ChatFormatting.BOLD);
		g.text(font, bold, left + 33, top + 13, TEXT_PRIMARY);

		int tx = left + 33 + font.width(bold) + 24;
		tx = tabButton(g, Tab.SEARCH, Component.translatable("screen.noobs_container_searcher.tab_search"), tx, top, false);
		tx = tabButton(g, Tab.REROLL, Component.translatable("screen.noobs_container_searcher.tab_reroll"), tx, top,
			reroll.state() != LibrarianRerollController.State.IDLE);
		tabButton(g, Tab.SETTINGS, Component.translatable("screen.noobs_container_searcher.tab_settings"), tx, top, false);

		int cx = left + pw - 30;
		int cy = top + 7;
		boolean hover = hovered(cx, cy, 20, 20);
		card(g, cx, cy, 20, 20, hover ? SURFACE_HI : SURFACE, hover ? BORDER_HI : BORDER);
		icon(g, ICON_X, cx + 7, cy + 8, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
		hit(cx, cy, 20, 20, this::onClose);
	}

	private int tabButton(Gfx g, Tab target, Component label, int x, int top, boolean live) {
		int tw = font.width(label) + 24 + (live ? 8 : 0);
		boolean active = tab == target;
		boolean hover = hovered(x, top + 4, tw, HEADER_HEIGHT - 4);
		if (active) {
			g.fill(x, top + 8, x + tw, top + HEADER_HEIGHT, PANEL);
			g.fill(x, top + 8, x + tw, top + 10, ACCENT);
		} else if (hover) {
			g.fill(x, top + 10, x + tw, top + HEADER_HEIGHT - 1, SURFACE);
		}
		int textX = x + 12;
		if (live) {
			g.fill(textX, top + 19, textX + 4, top + 23, SUCCESS);
			textX += 8;
		}
		g.text(font, label, textX, top + 16, active ? TEXT_PRIMARY : TEXT_SECONDARY);
		hit(x, top + 4, tw, HEADER_HEIGHT - 4, () -> switchTab(target));
		return x + tw + 2;
	}

	private void footer(Gfx g, int footerY, String left, String right) {
		g.text(font, trim(left, panelWidth() - 40 - font.width(right)), panelLeft() + 12, footerY + 7, TEXT_MUTED);
		g.text(font, right, panelLeft() + panelWidth() - 12 - font.width(right), footerY + 7, TEXT_SECONDARY);
	}

	private void renderSearchTab(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.footer_search").getString(),
			Component.translatable("screen.noobs_container_searcher.footer_results", rows.size(), resultContainerCount).getString());

		int colW = Math.min(184, bw / 3);
		renderFilterColumn(g, bx, by, colW, bh);
		g.fill(bx + colW + 8, by, bx + colW + 9, by + bh, BORDER);

		int rx = bx + colW + 18;
		int rw = bx + bw - rx;
		searchField(g, searchBox, rx, by, rw, 22, true);

		int ty = by + 28;
		g.text(font, Component.translatable("screen.noobs_container_searcher.try"), rx, ty + 2, TEXT_MUTED);
		int tagX = rx + font.width(Component.translatable("screen.noobs_container_searcher.try")) + 6;
		for (String snippet : new String[] {"name:", "lore:", "durability:<50", "enchanted"}) {
			if (tagX + font.width(snippet) + 8 > rx + rw) {
				break;
			}
			final String insert = snippet;
			tagX += tag(g, tagX, ty, snippet, TEXT_SECONDARY, SURFACE, 12, () -> {
				String current = searchBox.getValue().trim();
				searchBox.setValue(current.isEmpty() ? insert : current + " " + insert);
				focusBox(searchBox);
			}) + 4;
		}

		renderDistanceRow(g, rx, by + 46, rw);
		renderActiveFilters(g, rx, by + 74, rw);

		int sy = by + 94;
		g.fill(rx, sy + 11, rx + rw, sy + 12, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.results", rows.size()), rx, sy + 2, TEXT_SECONDARY);
		String sortLabel = Component.translatable(sort == Sort.NAME
			? "screen.noobs_container_searcher.sort_name" : "screen.noobs_container_searcher.sort_distance").getString();
		int sortW = font.width(sortLabel) + 22;
		boolean sortHover = hovered(rx + rw - sortW, sy - 2, sortW, 14);
		card(g, rx + rw - sortW, sy - 2, sortW, 14, sortHover ? SURFACE_HI : SURFACE, BORDER);
		g.text(font, sortLabel, rx + rw - sortW + 6, sy + 1, TEXT_SECONDARY);
		icon(g, ICON_CHEVRON, rx + rw - 12, sy + 3, TEXT_MUTED);
		hit(rx + rw - sortW, sy - 2, sortW, 14, () -> {
			sort = sort == Sort.NAME ? Sort.DISTANCE : Sort.NAME;
			resultScroll = 0;
			rebuild();
		});

		int ly = by + 112;
		renderResults(g, rx, ly, rw, Math.max(RESULT_HEIGHT, by + bh - ly));
	}

	private void renderFilterColumn(Gfx g, int x, int by, int colW, int bh) {
		searchField(g, filterSearchBox, x, by, colW, 18, false);

		int y = by + 26;
		caption(g, "screen.noobs_container_searcher.quick_filters", x, y);
		y += 12;
		List<ChipSpec> quick = new ArrayList<>();
		for (FilterEntry filter : filters) {
			if (!filter.id.startsWith("ench:")) {
				quick.add(new ChipSpec(filter.name, filter.count, filterColor(filter), filter.id.equals(selectedFilter), false, () -> toggleFilter(filter)));
			}
		}
		y += flowChips(g, quick, x, y, colW, true) + 6;

		List<ChipSpec> types = new ArrayList<>();
		for (ContainerTypeEntry type : containerTypes) {
			types.add(new ChipSpec(type.name, type.count, containerColor(type.name), selectedContainerTypeKeys.contains(type.key), false, () -> toggleContainerType(type)));
		}
		int typesAreaH = Math.min(flowChips(g, types, x, 0, colW, false), 54);
		int typesTitleY = by + bh - typesAreaH - 14;

		caption(g, "screen.noobs_container_searcher.enchantments", x, y);
		y += 12;
		int listH = Math.max(ENCHANT_ROW, typesTitleY - y - 6);
		renderEnchantmentList(g, x, y, colW, listH);

		caption(g, "screen.noobs_container_searcher.container_types", x, typesTitleY);
		int chipsY = typesTitleY + 12;
		if (types.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_container_types"), x + 2, chipsY + 3, TEXT_MUTED);
			return;
		}
		g.enableScissor(x, chipsY, x + colW, chipsY + typesAreaH);
		int total = flowChips(g, types, x, chipsY - (int) containerTypeScroll, colW, true);
		g.disableScissor();
		containerTypeScroll = clampScroll(containerTypeScroll, total, typesAreaH);
		scrollArea(x, chipsY, colW, typesAreaH, amount -> containerTypeScroll = clampScroll(containerTypeScroll - amount * 18, total, typesAreaH));
	}

	private void renderEnchantmentList(Gfx g, int x, int y, int colW, int listH) {
		List<FilterEntry> enchants = new ArrayList<>();
		for (FilterEntry filter : filters) {
			if (filter.id.startsWith("ench:")) {
				enchants.add(filter);
			}
		}
		int content = enchants.size() * ENCHANT_ROW;
		filterScroll = clampScroll(filterScroll, content, listH);
		boolean bar = content > listH;
		int rowW = colW - (bar ? 6 : 0);
		g.enableScissor(x, y, x + colW, y + listH);
		int start = Math.max(0, (int) (filterScroll / ENCHANT_ROW));
		int offset = y - (int) (filterScroll % ENCHANT_ROW);
		for (int i = start; i < enchants.size(); i++) {
			int ry = offset + (i - start) * ENCHANT_ROW;
			if (ry >= y + listH) {
				break;
			}
			FilterEntry filter = enchants.get(i);
			boolean selected = filter.id.equals(selectedFilter);
			boolean hover = hovered(x, Math.max(ry, y), rowW, ENCHANT_ROW - 1) && my < y + listH;
			rfill(g, x, ry, rowW, ENCHANT_ROW - 1, selected ? ACCENT_BG : hover ? SURFACE_HI : i % 2 == 0 ? SURFACE : PANEL, 1);
			String count = Integer.toString(filter.count);
			int nameX = x + 8;
			if (selected) {
				g.fill(x, ry + 2, x + 2, ry + ENCHANT_ROW - 3, ACCENT);
				icon(g, ICON_CHECK, x + 7, ry + 5, ACCENT_HOVER);
				nameX = x + 18;
			}
			g.text(font, trim(filter.name, rowW - (nameX - x) - font.width(count) - 12), nameX, ry + 4, selected ? TEXT_PRIMARY : ENCHANT_PURPLE);
			g.text(font, count, x + rowW - 6 - font.width(count), ry + 4, TEXT_MUTED);
			hitClipped(x, ry, rowW, ENCHANT_ROW - 1, x, y, colW, listH, () -> toggleFilter(filter));
		}
		g.disableScissor();
		if (enchants.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_filters"), x + 2, y + 3, TEXT_MUTED);
		}
		if (bar) {
			scrollbar(g, x + colW - 3, y, listH, filterScroll, content);
		}
		scrollArea(x, y, colW, listH, amount -> filterScroll = clampScroll(filterScroll - amount * ENCHANT_ROW, content, listH));
	}

	private void renderActiveFilters(Gfx g, int x, int y, int width) {
		boolean any = selectedFilter != null || !selectedContainerTypeKeys.isEmpty();
		String label = Component.translatable("screen.noobs_container_searcher.active").getString();
		g.text(font, label, x, y + 3, TEXT_MUTED);
		int cx = x + font.width(label) + 6;
		if (!any) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.active_none"), cx, y + 3, TEXT_MUTED);
			return;
		}
		if (selectedFilter != null) {
			cx += chip(g, cx, y, selectedFilterLabel, -1, selectedFilter.startsWith("ench:") ? ENCHANT_PURPLE : TEXT_SECONDARY, true, true, () -> {
				selectedFilter = null;
				resultScroll = 0;
				rebuild();
			}) + 4;
		}
		for (Map.Entry<String, String> type : new ArrayList<>(selectedTypeLabels.entrySet())) {
			if (!selectedContainerTypeKeys.contains(type.getKey()) || cx > x + width - 80) {
				continue;
			}
			cx += chip(g, cx, y, type.getValue(), -1, containerColor(type.getValue()), true, true, () -> {
				selectedContainerTypeKeys.remove(type.getKey());
				resultScroll = 0;
				rebuild();
			}) + 4;
		}
		String clear = Component.translatable("screen.noobs_container_searcher.clear_all").getString();
		int clearW = font.width(clear);
		boolean hover = hovered(cx + 4, y, clearW, 14);
		g.text(font, clear, cx + 4, y + 3, hover ? TEXT_PRIMARY : ACCENT_HOVER);
		hit(cx + 4, y, clearW, 14, () -> {
			selectedFilter = null;
			selectedContainerTypeKeys.clear();
			selectedTypeLabels.clear();
			resultScroll = 0;
			rebuild();
		});
	}

	private void toggleFilter(FilterEntry filter) {
		if (filter.id.equals(selectedFilter)) {
			selectedFilter = null;
		} else {
			selectedFilter = filter.id;
			selectedFilterLabel = filter.name;
		}
		resultScroll = 0;
		rebuild();
	}

	private void toggleContainerType(ContainerTypeEntry type) {
		if (!selectedContainerTypeKeys.remove(type.key)) {
			selectedContainerTypeKeys.add(type.key);
			selectedTypeLabels.put(type.key, type.name);
		}
		resultScroll = 0;
		rebuild();
	}

	private void renderResults(Gfx g, int x, int y, int width, int height) {
		int content = rows.size() * RESULT_STRIDE;
		resultScroll = clampScroll(resultScroll, content, height);
		boolean bar = content > height;
		int rowW = width - (bar ? 8 : 0);
		g.enableScissor(x, y, x + width, y + height);
		int start = Math.max(0, (int) (resultScroll / RESULT_STRIDE));
		int offset = y - (int) (resultScroll % RESULT_STRIDE);
		for (int i = start; i < rows.size(); i++) {
			int ry = offset + (i - start) * RESULT_STRIDE;
			if (ry >= y + height) {
				break;
			}
			renderResultRow(g, rows.get(i), x, ry, rowW, y, height);
		}
		g.disableScissor();
		if (rows.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), x + 4, y + 6, TEXT_MUTED);
		}
		if (bar) {
			scrollbar(g, x + width - 3, y, height, resultScroll, content);
		}
		scrollArea(x, y, width, height, amount -> resultScroll = clampScroll(resultScroll - amount * RESULT_STRIDE, content, height));
	}

	private void renderResultRow(Gfx g, ResultRow row, int x, int y, int w, int viewY, int viewH) {
		boolean inView = my >= viewY && my < viewY + viewH;
		boolean hover = inView && hovered(x, y, w, RESULT_HEIGHT);
		card(g, x, y, w, RESULT_HEIGHT, hover ? SURFACE_HI : SURFACE, hover ? ACCENT : BORDER);

		g.fill(x + 5, y + 4, x + 25, y + 24, INPUT_BG);
		g.outline(x + 5, y + 4, 20, 20, row.item.enchanted ? BORDER_HI : BORDER);
		Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(row.itemId));
		ItemStack stack = ItemStack.EMPTY;
		if (item != null) {
			stack = displayStack(item, row.item);
			g.fakeItem(stack, x + 7, y + 6);
			g.itemDecorations(font, stack, x + 7, y + 6, "");
			if (inView && hovered(x + 5, y + 4, 20, 20)) {
				g.tooltip(font, tooltip(stack), mx, my);
			}
		}

		int rightX = x + w - 8;
		if (hover) {
			String locate = Component.translatable("screen.noobs_container_searcher.locate").getString();
			int bw = font.width(locate) + 28;
			drawButton(g, rightX - bw, y + 4, bw, 20, locate, ACCENT, ACCENT_HOVER, ACCENT_DARK, true, ICON_ARROW);
			rightX -= bw + 8;
		} else {
			double distance = distanceTo(row.record);
			if (distance != -2) {
				String badge;
				int fg;
				int bg;
				if (distance < 0) {
					badge = Component.translatable("screen.noobs_container_searcher.other_dimension").getString();
					fg = 0xFFFFB3B3;
					bg = DANGER_BG;
				} else {
					badge = Component.translatable("screen.noobs_container_searcher.distance_short", Math.round(distance)).getString();
					boolean near = distance < 100;
					fg = near ? 0xFF8CE6A8 : 0xFFFFD98C;
					bg = near ? 0xFF1C3326 : 0xFF3A2F18;
				}
				int bw = font.width(badge) + 8;
				tag(g, rightX - bw, y + 8, badge, fg, bg, 12, null);
				rightX -= bw + 8;
			}
		}

		int countW = row.count > 1 ? font.width("x" + row.count) + 5 : 0;
		int nameMax = Math.max(40, rightX - x - 36 - countW);
		String name = trim(row.name, nameMax);
		g.text(font, name, x + 32, y + 4, itemNameColor(stack, row.item));
		if (row.count > 1) {
			g.text(font, "x" + row.count, x + 32 + font.width(name) + 5, y + 4, TEXT_SECONDARY);
		}

		int cx = x + 32;
		String container = containerName(row.record.containerType).getString();
		g.fill(cx, y + 17, cx + 4, y + 20, containerColor(container));
		String where = container + "  " + row.record.x + " " + row.record.y + " " + row.record.z;
		int whereMax = Math.max(40, rightX - cx - 8);
		String detail = trim(where, whereMax);
		g.text(font, detail, cx + 7, y + 15, TEXT_SECONDARY);
		cx += 7 + font.width(detail) + 8;
		String dimension = shortDimension(row.record.dimension);
		String more = dimension + durabilitySuffix(row.item);
		if (cx + font.width(more) < rightX) {
			g.text(font, more, cx, y + 15, TEXT_MUTED);
		}
		hitClipped(x, y, w, RESULT_HEIGHT, x, viewY, w, viewH, () -> {
			controller.focusItem(row.itemId, recordsFor(row), selectedFilter);
			onClose();
		});
	}

	private static String durabilitySuffix(ContainerItemRecord item) {
		return item.damageable && item.maxDamage > 0 ? "  " + durabilityPercent(item) + "%" : "";
	}

	private double distanceTo(ContainerRecord record) {
		if (minecraft == null || minecraft.player == null || minecraft.level == null) {
			return -2;
		}
		if (!ContainerSearcherClient.dimensionKey(minecraft).equals(record.dimension)) {
			return -1;
		}
		double dx = record.x + 0.5D - minecraft.player.getX();
		double dy = record.y + 0.5D - minecraft.player.getY();
		double dz = record.z + 0.5D - minecraft.player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private void renderDistanceRow(Gfx g, int x, int y, int width) {
		String label = Component.translatable("screen.noobs_container_searcher.distance_filter").getString();
		g.text(font, label, x, y + 6, TEXT_SECONDARY);

		int cx = x + width;
		for (int i = DISTANCE_PRESETS.length - 1; i >= 0; i--) {
			int preset = DISTANCE_PRESETS[i];
			String text = preset == UNLIMITED_DISTANCE
				? Component.translatable("screen.noobs_container_searcher.distance_any").getString()
				: Integer.toString(preset);
			int cw = font.width(text) + 14;
			cx -= cw;
			boolean active = maxDistance == preset;
			boolean hover = hovered(cx, y, cw, 20);
			card(g, cx, y, cw, 20, active ? ACCENT : hover ? SURFACE_HI : SURFACE, active ? ACCENT_HOVER : BORDER);
			g.centeredText(font, Component.literal(text), cx + cw / 2, y + 6, active ? TEXT_PRIMARY : TEXT_SECONDARY);
			hit(cx, y, cw, 20, () -> {
				maxDistance = preset;
				resultScroll = 0;
				rebuild();
			});
			cx -= 3;
		}

		int pillW = distancePillWidth();
		sliderX = x + font.width(label) + 10;
		sliderWidth = Math.max(40, cx - sliderX - pillW - 8);
		int trackY = y + 10;
		g.fill(sliderX, trackY - 2, sliderX + sliderWidth, trackY + 2, INPUT_BG);
		g.outline(sliderX, trackY - 2, sliderWidth, 4, BORDER);
		int knob = distanceKnobX();
		g.fill(sliderX + 1, trackY - 1, knob, trackY + 1, ACCENT);
		boolean hover = hovered(sliderX, y + 2, sliderWidth, 16) || draggingDistanceSlider;
		rfill(g, knob - 3, trackY - 6, 7, 12, INPUT_BG, 1);
		rfill(g, knob - 2, trackY - 5, 5, 10, hover ? TEXT_PRIMARY : ACCENT_HOVER, 1);
		hit(sliderX - 3, y + 2, sliderWidth + 7, 16, () -> {
			draggingDistanceSlider = true;
			updateDistanceSlider(mx);
		});

		int pillX = sliderX + sliderWidth + 8;
		rfill(g, pillX, y + 2, pillW, 16, ACCENT_BG, 1);
		g.centeredText(font, Component.literal(distanceValueText()), pillX + pillW / 2, y + 6, TEXT_PRIMARY);
	}

	private String distanceValueText() {
		return maxDistance == UNLIMITED_DISTANCE
			? Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString()
			: Component.translatable("screen.noobs_container_searcher.distance_value", maxDistance).getString();
	}

	private int distancePillWidth() {
		String longest = Component.translatable("screen.noobs_container_searcher.distance_value", MAX_DISTANCE).getString();
		String unlimited = Component.translatable("screen.noobs_container_searcher.distance_unlimited").getString();
		return Math.max(font.width(longest), font.width(unlimited)) + 14;
	}

	private int distanceKnobX() {
		if (maxDistance == UNLIMITED_DISTANCE) {
			return sliderX + sliderWidth;
		}
		double ratio = Math.max(0, Math.min(1, maxDistance / (double) MAX_DISTANCE));
		return sliderX + (int) Math.round(ratio * sliderWidth);
	}

	private void updateDistanceSlider(double mouseX) {
		double ratio = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderWidth));
		int newDistance = ratio >= 0.97D ? UNLIMITED_DISTANCE : Math.max(25, (int) Math.round(ratio * MAX_DISTANCE / 25.0D) * 25);
		if (newDistance != maxDistance) {
			maxDistance = newDistance;
			resultScroll = 0;
			rebuild();
		}
	}

	private void renderRerollTab(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		if (seenSelectionRevision != ModSettings.get().selectionRevision()) {
			syncSelection();
		}
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.footer_reroll").getString(),
			Component.translatable("screen.noobs_container_searcher.reroll_book_count", visibleChoices.size()).getString());

		int sideW = Math.min(190, bw / 3);
		int sideX = bx + bw - sideW;
		int listW = sideX - bx - 14;

		searchField(g, rerollSearchBox, bx, by, Math.min(300, listW), 22, true);
		String multi = Component.translatable("screen.noobs_container_searcher.reroll_multi_hint").getString();
		g.text(font, multi, bx + listW - font.width(multi), by + 7, TEXT_MUTED);

		int gy = by + 30;
		int gh = by + bh - gy;
		int cols = Math.max(1, Math.min(3, (listW + 5) / 160));
		int cardW = (listW - (cols - 1) * 5) / cols;
		int content = ((visibleChoices.size() + cols - 1) / cols) * REROLL_CARD_STRIDE;
		rerollScroll = clampScroll(rerollScroll, content, gh);
		boolean bar = content > gh;
		g.enableScissor(bx, gy, bx + listW + 6, gy + gh);
		for (int i = 0; i < visibleChoices.size(); i++) {
			int row = i / cols;
			int cardY = gy + row * REROLL_CARD_STRIDE - (int) rerollScroll;
			if (cardY + REROLL_CARD_HEIGHT < gy) {
				continue;
			}
			if (cardY >= gy + gh) {
				break;
			}
			int cardX = bx + (i % cols) * (cardW + 5);
			EnchantmentChoice choice = visibleChoices.get(i);
			boolean selected = selectedChoices.contains(choice);
			boolean hover = my >= gy && my < gy + gh && hovered(cardX, cardY, cardW, REROLL_CARD_HEIGHT);
			card(g, cardX, cardY, cardW, REROLL_CARD_HEIGHT, selected ? ACCENT_BG : hover ? SURFACE_HI : SURFACE, selected ? ACCENT : BORDER);
			g.fill(cardX + 6, cardY + 6, cardX + 8, cardY + 20, selected ? ENCHANT_PURPLE : BORDER_HI);
			g.text(font, trim(choice.name, cardW - 30), cardX + 13, cardY + 4, selected ? TEXT_PRIMARY : TEXT_SECONDARY);
			g.text(font, trim(choice.id.getPath(), cardW - 22), cardX + 13, cardY + 14, TEXT_MUTED);
			if (selected) {
				icon(g, ICON_CHECK, cardX + cardW - 12, cardY + 5, ACCENT_HOVER);
			}
			hitClipped(cardX, cardY, cardW, REROLL_CARD_HEIGHT, bx, gy, listW, gh, () -> {
				if (ctrlDown) {
					if (!selectedChoices.remove(choice)) {
						selectedChoices.add(choice);
					}
				} else if (selectedChoices.size() == 1 && selected) {
					selectedChoices.clear();
				} else {
					selectedChoices.clear();
					selectedChoices.add(choice);
				}
				persistSelection();
			});
		}
		g.disableScissor();
		if (visibleChoices.isEmpty()) {
			g.text(font, Component.translatable("screen.noobs_container_searcher.no_results"), bx + 4, gy + 6, TEXT_MUTED);
		}
		if (bar) {
			scrollbar(g, bx + listW + 4, gy, gh, rerollScroll, content);
		}
		scrollArea(bx, gy, listW + 6, gh, amount -> rerollScroll = clampScroll(rerollScroll - amount * REROLL_CARD_STRIDE, content, gh));

		renderRerollSide(g, sideX, by, sideW, bh);
	}

	private void renderRerollSide(Gfx g, int x, int by, int w, int bh) {
		g.fill(x - 8, by, x - 7, by + bh, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.selected_books_title").withStyle(ChatFormatting.BOLD), x, by + 2, TEXT_PRIMARY);

		int y = by + 18;
		if (selectedChoices.isEmpty()) {
			g.text(font, trim(Component.translatable("screen.noobs_container_searcher.no_book_selected").getString(), w), x, y + 3, TEXT_MUTED);
			y += 18;
		}
		int shown = 0;
		for (EnchantmentChoice choice : new ArrayList<>(selectedChoices)) {
			if (shown == 4) {
				g.text(font, Component.translatable("screen.noobs_container_searcher.more", selectedChoices.size() - 4), x, y + 2, TEXT_MUTED);
				y += 14;
				break;
			}
			card(g, x, y, w, 18, SURFACE, BORDER);
			g.fill(x + 5, y + 5, x + 7, y + 13, ENCHANT_PURPLE);
			g.text(font, trim(choice.name, w - 30), x + 12, y + 5, TEXT_PRIMARY);
			boolean xHover = hovered(x + w - 16, y, 16, 18);
			icon(g, ICON_X, x + w - 12, y + 8, xHover ? TEXT_PRIMARY : TEXT_MUTED);
			hit(x + w - 16, y, 16, 18, () -> {
				selectedChoices.remove(choice);
				persistSelection();
			});
			y += 21;
			shown++;
		}
		if (selectedChoices.size() > 1) {
			String clear = Component.translatable("screen.noobs_container_searcher.clear_all").getString();
			boolean hover = hovered(x, y, font.width(clear), 12);
			g.text(font, clear, x, y + 2, hover ? TEXT_PRIMARY : ACCENT_HOVER);
			hit(x, y, font.width(clear), 12, () -> {
				selectedChoices.clear();
				persistSelection();
			});
			y += 16;
		}
		y += 6;
		caption(g, "screen.noobs_container_searcher.requirements", x, y);
		y += 12;
		y = requirement(g, x, y, w, "screen.noobs_container_searcher.req_lectern", hotbarHas(Items.LECTERN));
		y = requirement(g, x, y, w, "screen.noobs_container_searcher.req_axe", hotbarHas(Items.DIAMOND_AXE) || hotbarHas(Items.NETHERITE_AXE));
		y += 6;
		card(g, x, y, w, 36, SURFACE, BORDER);
		caption(g, "screen.noobs_container_searcher.status", x + 8, y + 6);
		g.text(font, trim(rerollStatusLine().getString(), w - 16), x + 8, y + 19,
			reroll.state() == LibrarianRerollController.State.IDLE ? TEXT_PRIMARY : SUCCESS);

		boolean canStart = !selectedChoices.isEmpty();
		drawButton(g, x, by + bh - 52, w, 22, Component.translatable("screen.noobs_container_searcher.select_villager").getString(),
			ACCENT, ACCENT_HOVER, ACCENT_DARK, canStart, ICON_PIN);
		if (canStart) {
			hit(x, by + bh - 52, w, 22, this::beginSelection);
		}
		boolean running = reroll.state() != LibrarianRerollController.State.IDLE;
		drawButton(g, x, by + bh - 26, w, 22, Component.translatable("screen.noobs_container_searcher.stop_reroll").getString(),
			DANGER, DANGER_HOVER, DANGER_DARK, running, null);
		if (running) {
			hit(x, by + bh - 26, w, 22, reroll::stopByUser);
		}
	}

	private int requirement(Gfx g, int x, int y, int w, String key, boolean ok) {
		icon(g, ok ? ICON_CHECK : ICON_X, x, y + 1, ok ? SUCCESS : DANGER);
		g.text(font, trim(Component.translatable(key).getString(), w - 12), x + 11, y, TEXT_SECONDARY);
		return y + 12;
	}

	private boolean hotbarHas(Item item) {
		if (minecraft == null || minecraft.player == null) {
			return false;
		}
		for (int slot = 0; slot < 9; slot++) {
			if (minecraft.player.getInventory().getItem(slot).getItem() == item) {
				return true;
			}
		}
		return false;
	}

	private Component rerollStatusLine() {
		return switch (reroll.state()) {
			case IDLE -> Component.translatable("screen.noobs_container_searcher.reroll_status_idle");
			case SELECT_VILLAGER -> Component.translatable("screen.noobs_container_searcher.status_select_villager");
			case SELECT_LECTERN_POSITION -> Component.translatable("screen.noobs_container_searcher.status_select_block");
			case SUCCESS -> Component.translatable("screen.noobs_container_searcher.reroll_status_success");
			default -> Component.translatable("screen.noobs_container_searcher.reroll_status_running", reroll.attempts());
		};
	}

	private void renderSettingsTab(Gfx g, int bx, int by, int bw, int bh, int footerY) {
		footer(g, footerY, Component.translatable("screen.noobs_container_searcher.footer_settings").getString(), "");
		int cw = Math.min(520, bw);

		card(g, bx, by + 4, cw, 72, SURFACE, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.settings_distance_title").withStyle(ChatFormatting.BOLD), bx + 14, by + 14, TEXT_PRIMARY);
		g.text(font, trim(Component.translatable("screen.noobs_container_searcher.settings_distance_hint").getString(), cw - 28), bx + 14, by + 26, TEXT_MUTED);
		renderDistanceRow(g, bx + 14, by + 46, cw - 28);

		int yd = by + 4 + 72 + 10;
		card(g, bx, yd, cw, 44, SURFACE, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.settings_item_display_title").withStyle(ChatFormatting.BOLD), bx + 14, yd + 10, TEXT_PRIMARY);
		g.text(font, trim(Component.translatable("screen.noobs_container_searcher.settings_item_display_hint").getString(), cw - 28 - 44), bx + 14, yd + 23, TEXT_MUTED);
		boolean displayOn = ModSettings.itemDisplayEnabled();
		int tx = bx + cw - 14 - 30;
		int ty = yd + 14;
		boolean toggleHover = hovered(tx, ty, 30, 16);
		rfill(g, tx, ty, 30, 16, displayOn ? (toggleHover ? ACCENT_HOVER : ACCENT) : (toggleHover ? BORDER_HI : BORDER), 1);
		rfill(g, displayOn ? tx + 16 : tx + 2, ty + 2, 12, 12, TEXT_PRIMARY, 1);
		hit(tx, ty, 30, 16, () -> ModSettings.get().setItemDisplay(!ModSettings.itemDisplayEnabled()));

		int y2 = yd + 44 + 10;
		card(g, bx, y2, cw, 70, SURFACE, BORDER);
		g.text(font, Component.translatable("screen.noobs_container_searcher.clear_data_title").withStyle(ChatFormatting.BOLD), bx + 14, y2 + 12, TEXT_PRIMARY);
		int items = 0;
		for (ContainerRecord record : records) {
			items += itemCount(record);
		}
		g.text(font, trim(Component.translatable("screen.noobs_container_searcher.settings_data_summary", records.size(), items).getString(), cw - 28),
			bx + 14, y2 + 24, TEXT_MUTED);
		String delete = Component.translatable("screen.noobs_container_searcher.clear_data").getString();
		int dw = font.width(delete) + 28;
		drawButton(g, bx + 14, y2 + 40, dw, 20, delete, DANGER, DANGER_HOVER, DANGER_DARK, true, null);
		hit(bx + 14, y2 + 40, dw, 20, this::confirmClearData);
		g.text(font, Component.translatable("screen.noobs_container_searcher.settings_irreversible"), bx + 14 + dw + 10, y2 + 46, TEXT_MUTED);
	}

	private void caption(Gfx g, String key, int x, int y) {
		g.text(font, Component.translatable(key).getString().toUpperCase(Locale.ROOT), x, y, TEXT_MUTED);
	}

	private boolean hovered(int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private void hit(int x, int y, int w, int h, Runnable action) {
		hits.add(new Hit(x, y, w, h, action));
	}

	private void hitClipped(int x, int y, int w, int h, int vx, int vy, int vw, int vh, Runnable action) {
		int x1 = Math.max(x, vx);
		int y1 = Math.max(y, vy);
		int x2 = Math.min(x + w, vx + vw);
		int y2 = Math.min(y + h, vy + vh);
		if (x2 > x1 && y2 > y1) {
			hit(x1, y1, x2 - x1, y2 - y1, action);
		}
	}

	private void scrollArea(int x, int y, int w, int h, DoubleConsumer onScroll) {
		scrollAreas.add(new ScrollArea(x, y, w, h, onScroll));
	}

	private static void rfill(Gfx g, int x, int y, int w, int h, int color) {
		rfill(g, x, y, w, h, color, 2);
	}

	private static void rfill(Gfx g, int x, int y, int w, int h, int color, int radius) {
		if (radius >= 2) {
			g.fill(x + 2, y, x + w - 2, y + h, color);
			g.fill(x + 1, y + 1, x + 2, y + h - 1, color);
			g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, color);
			g.fill(x, y + 2, x + 1, y + h - 2, color);
			g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
		} else {
			g.fill(x + 1, y, x + w - 1, y + h, color);
			g.fill(x, y + 1, x + w, y + h - 1, color);
		}
	}

	private static void card(Gfx g, int x, int y, int w, int h, int fill, int border) {
		rfill(g, x, y, w, h, border);
		rfill(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	private static void icon(Gfx g, String[] bitmap, int x, int y, int color) {
		for (int row = 0; row < bitmap.length; row++) {
			String line = bitmap[row];
			int run = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean on = col < line.length() && line.charAt(col) == 'X';
				if (on && run < 0) {
					run = col;
				} else if (!on && run >= 0) {
					g.fill(x + run, y + row, x + col, y + row + 1, color);
					run = -1;
				}
			}
		}
	}

	private void drawButton(Gfx g, int x, int y, int w, int h, String label, int base, int hover, int dark, boolean enabled, String[] icon) {
		if (!enabled) {
			card(g, x, y, w, h, SURFACE, BORDER);
			g.centeredText(font, Component.literal(label), x + w / 2, y + (h - 2 - 8) / 2 + 1, TEXT_MUTED);
			return;
		}
		boolean isHover = hovered(x, y, w, h);
		rfill(g, x, y, w, h, dark);
		rfill(g, x, y, w, h - 2, isHover ? hover : base);
		g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x30FFFFFF);
		int textW = font.width(label) + (icon == null ? 0 : icon[0].length() + 4);
		int tx = x + (w - textW) / 2;
		if (icon != null) {
			icon(g, icon, tx, y + (h - 2 - icon.length) / 2 + 1, 0xFFFFFFFF);
			tx += icon[0].length() + 4;
		}
		g.text(font, label, tx, y + (h - 2 - 8) / 2 + 1, 0xFFFFFFFF);
	}

	private int tag(Gfx g, int x, int y, String label, int fg, int bg, int h, Runnable click) {
		int w = font.width(label) + 8;
		boolean hover = click != null && hovered(x, y, w, h);
		rfill(g, x, y, w, h, hover ? SURFACE_HI : bg, 1);
		g.text(font, label, x + 4, y + (h - 8) / 2 + 1, hover ? TEXT_PRIMARY : fg);
		if (click != null) {
			hit(x, y, w, h, click);
		}
		return w;
	}

	private String chipLabel(ChipSpec spec) {
		return trim(spec.label, 120);
	}

	private int chipWidth(ChipSpec spec) {
		return font.width(chipLabel(spec)) + 12 + (spec.dot != 0 ? 7 : 0) + (spec.count >= 0 ? font.width(Integer.toString(spec.count)) + 4 : 0) + (spec.closable ? 8 : 0);
	}

	private int chip(Gfx g, int x, int y, String label, int count, int dot, boolean active, boolean closable, Runnable click) {
		return drawChip(g, new ChipSpec(label, count, dot, active, closable, click), x, y);
	}

	private int drawChip(Gfx g, ChipSpec spec, int x, int y) {
		int w = chipWidth(spec);
		boolean hover = hovered(x, y, w, 14);
		card(g, x, y, w, 14, spec.active ? ACCENT_BG : hover ? SURFACE_HI : SURFACE, spec.active ? ACCENT : BORDER);
		int cx = x + 6;
		if (spec.dot != 0) {
			g.fill(cx, y + 6, cx + 3, y + 9, spec.dot);
			cx += 7;
		}
		String label = chipLabel(spec);
		g.text(font, label, cx, y + 3, spec.active ? TEXT_PRIMARY : TEXT_SECONDARY);
		cx += font.width(label);
		if (spec.count >= 0) {
			g.text(font, Integer.toString(spec.count), cx + 4, y + 3, TEXT_MUTED);
		}
		if (spec.closable) {
			icon(g, ICON_X, x + w - 10, y + 5, ACCENT_HOVER);
		}
		hit(x, y, w, 14, spec.click);
		return w;
	}

	private int flowChips(Gfx g, List<ChipSpec> specs, int x, int y, int maxWidth, boolean draw) {
		int cx = x;
		int cy = y;
		for (ChipSpec spec : specs) {
			int w = chipWidth(spec);
			if (cx > x && cx + w > x + maxWidth) {
				cx = x;
				cy += 18;
			}
			if (draw) {
				drawChip(g, spec, cx, cy);
			}
			cx += w + 4;
		}
		return specs.isEmpty() ? 0 : cy - y + 14;
	}

	private void searchField(Gfx g, EditBox box, int x, int y, int w, int h, boolean clearable) {
		boolean focused = box.isFocused();
		card(g, x, y, w, h, INPUT_BG, focused ? ACCENT : BORDER);
		icon(g, ICON_SEARCH, x + 7, y + (h - 9) / 2, focused ? ACCENT_HOVER : TEXT_MUTED);
		boolean showClear = clearable && !box.getValue().isEmpty();
		box.setX(x + 22);
		box.setY(y + (h - 8) / 2);
		box.setWidth(w - 22 - (showClear ? 18 : 8));
		box.setHeight(10);
		hit(x, y, w, h, () -> focusBox(box));
		if (showClear) {
			boolean hover = hovered(x + w - 16, y, 16, h);
			icon(g, ICON_X, x + w - 12, y + h / 2 - 2, hover ? TEXT_PRIMARY : TEXT_SECONDARY);
			hit(x + w - 16, y, 16, h, () -> {
				box.setValue("");
				focusBox(box);
			});
		}
	}

	private void scrollbar(Gfx g, int x, int y, int h, double scroll, int content) {
		g.fill(x, y, x + 3, y + h, 0xFF15161C);
		int thumb = Math.max(12, (int) (h * (h / (double) content)));
		int range = Math.max(1, content - h);
		int thumbY = y + (int) ((h - thumb) * (scroll / range));
		g.fill(x, thumbY, x + 3, thumbY + thumb, BORDER_HI);
	}

	private static int containerColor(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("shulker")) {
			return 0xFFC090E0;
		}
		if (lower.contains("barrel")) {
			return 0xFFD09060;
		}
		if (lower.contains("chest")) {
			return 0xFFE0B070;
		}
		if (lower.contains("villager")) {
			return 0xFF90D0A0;
		}
		return 0xFF9AA0B0;
	}

	private boolean click(double mouseX, double mouseY, int button, boolean ctrl) {
		if (!Compat.isLeftButton(button)) {
			return false;
		}
		ctrlDown = ctrl;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(mouseX, mouseY)) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	private boolean drag(double mouseX) {
		if (draggingDistanceSlider) {
			updateDistanceSlider(mouseX);
			return true;
		}
		return false;
	}

	private void release() {
		draggingDistanceSlider = false;
	}

	private boolean editKey(java.util.function.Predicate<EditBox> press) {
		if (tab == Tab.SEARCH && (press.test(searchBox) || press.test(filterSearchBox))) {
			return true;
		}
		return tab == Tab.REROLL && press.test(rerollSearchBox);
	}

	// <<INPUT>>
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return click(mouseX, mouseY, button, hasControlDown());
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		return drag(mouseX) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		release();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return editKey(box -> box.keyPressed(keyCode, scanCode, modifiers)) || super.keyPressed(keyCode, scanCode, modifiers);
	}
	// <</INPUT>>

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		for (ScrollArea area : scrollAreas) {
			if (mouseX >= area.x && mouseY >= area.y && mouseX < area.x + area.w && mouseY < area.y + area.h) {
				area.onScroll.accept(verticalAmount);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
			ResourceLocation id = entry.getKey().location();
			for (int level = entry.getValue().getMinLevel(); level <= entry.getValue().getMaxLevel(); level++) {
				String name = Enchantment.getFullname(holder, level).getString();
				allChoices.add(new EnchantmentChoice(id, level, name));
			}
		}
		allChoices.sort(Comparator.comparing(choice -> normalizedSearch(choice.name)));
		rebuildChoices();
		syncSelection();
	}

	private int seenSelectionRevision = -1;

	private void persistSelection() {
		List<String> entries = new ArrayList<>();
		for (EnchantmentChoice choice : selectedChoices) {
			entries.add(choice.id + "|" + choice.level);
		}
		ModSettings settings = ModSettings.get();
		settings.setRerollSelection(entries);
		seenSelectionRevision = settings.selectionRevision();
	}

	private void syncSelection() {
		ModSettings settings = ModSettings.get();
		seenSelectionRevision = settings.selectionRevision();
		Set<String> saved = new java.util.HashSet<>(settings.rerollSelection());
		selectedChoices.clear();
		for (EnchantmentChoice choice : allChoices) {
			if (saved.contains(choice.id + "|" + choice.level)) {
				selectedChoices.add(choice);
			}
		}
	}

	private void rebuildChoices() {
		String query = rerollSearchBox == null ? "" : normalizedSearch(rerollSearchBox.getValue());
		visibleChoices.clear();
		for (EnchantmentChoice choice : allChoices) {
			if (query.isEmpty() || normalizedSearch(choice.name + " " + choice.id).contains(query)) {
				visibleChoices.add(choice);
			}
		}
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
		Comparator<ResultRow> byName = Comparator
			.comparing((ResultRow row) -> row.name.toLowerCase(Locale.ROOT))
			.thenComparing(row -> row.record.dimension)
			.thenComparingInt(row -> row.record.x)
			.thenComparingInt(row -> row.record.y)
			.thenComparingInt(row -> row.record.z);
		rows.sort(sort == Sort.DISTANCE
			? Comparator.comparingDouble((ResultRow row) -> {
				double distance = distanceTo(row.record);
				return distance < 0 ? Double.MAX_VALUE : distance;
			}).thenComparing(byName)
			: byName);
		Set<String> containerIds = new LinkedHashSet<>();
		for (ResultRow row : rows) {
			containerIds.add(recordIdentity(row.record));
		}
		resultContainerCount = containerIds.size();
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
		ResourceLocation id = ResourceLocation.tryParse(itemId);
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			return item.getName(new ItemStack(item)).getString();
		}
		return itemId;
	}

	private static final Map<ContainerItemRecord, String> ITEM_NAME_CACHE = new java.util.IdentityHashMap<>();
	private static final Map<String, String> ENCHANTMENT_NAME_CACHE = new HashMap<>();

	private static String localizedItemName(ContainerItemRecord record) {
		String cached = ITEM_NAME_CACHE.get(record);
		if (cached != null) {
			return cached;
		}
		ItemStack stack = decodeStack(record);
		String name = !stack.isEmpty() ? stack.getHoverName().getString() : itemName(record.itemId);
		ITEM_NAME_CACHE.put(record, name);
		return name;
	}

	private static String localizedEnchantmentName(String enchantmentId) {
		return ENCHANTMENT_NAME_CACHE.computeIfAbsent(enchantmentId, ContainerSearchScreen::computeEnchantmentName);
	}

	private static String computeEnchantmentName(String enchantmentId) {
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

	private int panelLeft() {
		return width / 2 - panelWidth() / 2;
	}

	private int panelTop() {
		return height / 2 - panelHeight() / 2;
	}

	private int panelWidth() {
		return Math.min(width - 8, Math.max((int) Math.round(width * 0.85D), 520));
	}

	private int panelHeight() {
		return Math.min(height - 8, Math.max((int) Math.round(height * 0.85D), 300));
	}

	private boolean passesContainerType(ContainerRecord record, Map<String, ContainerTypeEntry> containerTypeMap) {
		if (selectedContainerTypeKeys.isEmpty()) {
			return true;
		}
		ContainerTypeEntry entry = containerTypeMap.get(containerTypeKey(record.containerType));
		return entry != null && selectedContainerTypeKeys.contains(entry.key);
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

	private record EnchantmentChoice(ResourceLocation id, int level, String name) { }
}