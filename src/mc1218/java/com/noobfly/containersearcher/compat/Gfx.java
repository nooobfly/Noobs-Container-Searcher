package com.noobfly.containersearcher.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class Gfx {
	private final GuiGraphics graphics;

	public Gfx(GuiGraphics graphics) {
		this.graphics = graphics;
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1, y1, x2, y2, color);
	}

	public void outline(int x, int y, int width, int height, int color) {
		graphics.renderOutline(x, y, width, height, color);
	}

	public void text(Font font, Component text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color);
	}

	public void text(Font font, String text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color);
	}

	public void centeredText(Font font, Component text, int x, int y, int color) {
		graphics.drawCenteredString(font, text, x, y, color);
	}

	public void enableScissor(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	public void disableScissor() {
		graphics.disableScissor();
	}

	public void fakeItem(ItemStack stack, int x, int y) {
		graphics.renderFakeItem(stack, x, y);
	}

	public void itemDecorations(Font font, ItemStack stack, int x, int y, String label) {
		graphics.renderItemDecorations(font, stack, x, y, label);
	}

	public void tooltip(Font font, List<Component> lines, int mouseX, int mouseY) {
		graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
	}
}
