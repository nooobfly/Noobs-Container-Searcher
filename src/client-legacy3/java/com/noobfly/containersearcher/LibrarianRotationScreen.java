package com.noobfly.containersearcher;

import com.noobfly.containersearcher.compat.Compat;
import com.noobfly.containersearcher.compat.CompatScreen;
import com.noobfly.containersearcher.compat.Gfx;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

public final class LibrarianRotationScreen extends CompatScreen {
	private static final int PANEL_BACKGROUND = 0xF016181C;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_BACKGROUND = 0x88313338;
	private static final int ROW_HOVER = 0xAA3A3D45;
	private static final int ROW_SELECTED = 0xAA5865F2;

	private final SearchController searchController;
	private final List<ContainerRecord> records;
	private final LibrarianRerollController reroll;
	private final List<EnchantmentChoice> allChoices = new ArrayList<>();
	private final List<EnchantmentChoice> visibleChoices = new ArrayList<>();

	private EditBox searchBox;
	private Button selectVillagerButton;
	private final Set<EnchantmentChoice> selected = new LinkedHashSet<>();
	private double scroll;

	public LibrarianRotationScreen(SearchController searchController, List<ContainerRecord> records) {
		super(Component.translatable("screen.noobs_container_searcher.reroll_title"));
		this.searchController = searchController;
		this.records = records;
		this.reroll = ContainerSearcherClient.librarianReroll();
	}

	@Override
	protected void init() {
		loadEnchantments();
		searchBox = new EditBox(font, panelLeft() + 12, panelTop() + 42, Math.min(300, panelWidth() - 24), 20,
			Component.translatable("screen.noobs_container_searcher.reroll_search"));
		searchBox.setHint(Component.translatable("screen.noobs_container_searcher.reroll_search_hint"));
		searchBox.setResponder(value -> rebuildChoices());
		addRenderableWidget(searchBox);

		addRenderableWidget(Button.builder(
			Component.translatable("screen.noobs_container_searcher.back_to_search"),
			button -> Compat.setScreen(minecraft, new ContainerSearchScreen(searchController, records))
		).bounds(panelLeft() + 12, panelTop() + 12, 150, 20).build());

		selectVillagerButton = addRenderableWidget(Button.builder(
			Component.translatable("screen.noobs_container_searcher.select_villager"),
			button -> beginSelection()
		).bounds(panelLeft() + panelWidth() - 172, panelTop() + panelHeight() - 32, 160, 20).build());

		addRenderableWidget(Button.builder(
			Component.translatable("screen.noobs_container_searcher.stop_reroll"),
			button -> reroll.stopByUser()
		).bounds(panelLeft() + 12, panelTop() + panelHeight() - 32, 120, 20).build());
		updateButtons();
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
		allChoices.sort(Comparator.comparing(choice -> normalized(choice.name)));
		rebuildChoices();
	}

	private void rebuildChoices() {
		String query = searchBox == null ? "" : normalized(searchBox.getValue());
		visibleChoices.clear();
		for (EnchantmentChoice choice : allChoices) {
			if (query.isEmpty() || normalized(choice.name + " " + choice.id).contains(query)) {
				visibleChoices.add(choice);
			}
		}
		scroll = clampScroll(scroll);
	}

	private void beginSelection() {
		if (selected.isEmpty()) {
			return;
		}
		reroll.beginSelection(selected.stream()
			.map(choice -> new LibrarianRerollController.TargetBook(choice.id, choice.level))
			.toList());
		onClose();
	}

	@Override
	protected void renderContent(Gfx graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0x991E1F22);
		graphics.fill(panelLeft(), panelTop(), panelLeft() + panelWidth(), panelTop() + panelHeight(), PANEL_BACKGROUND);
		graphics.outline(panelLeft(), panelTop(), panelWidth(), panelHeight(), 0xFF4E5058);
		graphics.centeredText(font, title, width / 2, panelTop() + 18, 0xFFDCDDDE);
		renderWidgets(mouseX, mouseY, partialTick);

		int listX = panelLeft() + 12;
		int listY = panelTop() + 72;
		int listWidth = panelWidth() - 24;
		int listHeight = panelHeight() - 116;
		graphics.text(font, Component.translatable("screen.noobs_container_searcher.reroll_book_count", visibleChoices.size()),
			listX, listY - 10, 0xFFB5BAC1);
		String multiHint = Component.translatable("screen.noobs_container_searcher.reroll_multi_hint").getString();
		graphics.text(font, multiHint, listX + listWidth - font.width(multiHint), listY - 10, 0xFFB5BAC1);
		graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
		int start = Math.max(0, (int) (scroll / ROW_HEIGHT));
		int offset = listY - (int) (scroll % ROW_HEIGHT);
		for (int i = start; i < visibleChoices.size(); i++) {
			int rowY = offset + (i - start) * ROW_HEIGHT;
			if (rowY >= listY + listHeight) {
				break;
			}
			EnchantmentChoice choice = visibleChoices.get(i);
			boolean hovered = inside(mouseX, mouseY, listX, rowY, listWidth, ROW_HEIGHT - 1);
			int color = selected.contains(choice) ? ROW_SELECTED : hovered ? ROW_HOVER : ROW_BACKGROUND;
			graphics.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT - 1, color);
			graphics.text(font, choice.name, listX + 6, rowY + 6, 0xFFFFFFFF);
			graphics.text(font, choice.id.toString(), listX + listWidth - font.width(choice.id.toString()) - 6, rowY + 6, 0xFF777777);
		}
		graphics.disableScissor();

		String selection = selected.isEmpty()
			? Component.translatable("screen.noobs_container_searcher.no_book_selected").getString()
			: selected.size() == 1
				? Component.translatable("screen.noobs_container_searcher.selected_book", selected.iterator().next().name).getString()
				: Component.translatable("screen.noobs_container_searcher.selected_books", selected.size()).getString();
		graphics.text(font, selection, panelLeft() + 144, panelTop() + panelHeight() - 26, 0xFFB5BAC1);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0) {
			return false;
		}
		int listX = panelLeft() + 12;
		int listY = panelTop() + 72;
		int listWidth = panelWidth() - 24;
		int listHeight = panelHeight() - 116;
		if (inside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
			int index = (int) ((mouseY - listY + scroll) / ROW_HEIGHT);
			if (index >= 0 && index < visibleChoices.size()) {
				EnchantmentChoice choice = visibleChoices.get(index);
				if (hasControlDown()) {
					if (!selected.remove(choice)) {
						selected.add(choice);
					}
				} else {
					selected.clear();
					selected.add(choice);
				}
				updateButtons();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = clampScroll(scroll - verticalAmount * ROW_HEIGHT);
		return true;
	}

	@Override
	public void onClose() {
		Compat.setScreen(minecraft, null);
	}

	private void updateButtons() {
		if (selectVillagerButton != null) {
			selectVillagerButton.active = !selected.isEmpty();
		}
	}

	private double clampScroll(double value) {
		int listHeight = panelHeight() - 116;
		return Math.max(0, Math.min(value, Math.max(0, visibleChoices.size() * ROW_HEIGHT - listHeight)));
	}

	private int panelLeft() {
		return width / 10;
	}

	private int panelTop() {
		return height / 10;
	}

	private int panelWidth() {
		return width - panelLeft() * 2;
	}

	private int panelHeight() {
		return height - panelTop() * 2;
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
	}

	private static String normalized(String value) {
		String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private record EnchantmentChoice(ResourceLocation id, int level, String name) { }
}
