package com.noobfly.containersearcher.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.List;

public final class Gfx {
	// enableScissor is made to follow the current translation in enableScissor below
	public static final boolean CLIPS = true;
	private static final String TEXTURES = "noobs_container_searcher:textures/gui/";
	private static final String LOGO = "noobs_container_searcher:textures/gui/logo_small.png";
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
		// the scissor here ignores the pose translation, so add it by hand
		Vector3f offset = graphics.pose().last().pose().getTranslation(new Vector3f());
		int ox = Math.round(offset.x);
		int oy = Math.round(offset.y);
		graphics.enableScissor(x1 + ox, y1 + oy, x2 + ox, y2 + oy);
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
		graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
	}

	public void logo(int x, int y, int size) {
		graphics.blit(ResourceLocation.parse(LOGO), x, y, size, size, 0f, 0f, 48, 48, 48, 48);
	}

	// 64x64 icon textures under textures/gui
	public void icon(String name, int x, int y, int size) {
		graphics.blit(ResourceLocation.parse(TEXTURES + name + ".png"), x, y, size, size, 0f, 0f, 64, 64, 64, 64);
	}
}
