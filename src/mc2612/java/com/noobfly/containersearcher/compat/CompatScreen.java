package com.noobfly.containersearcher.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
	private GuiGraphicsExtractor currentGraphics;

	protected CompatScreen(Component title) {
		super(title);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		currentGraphics = graphics;
		try {
			renderContent(new Gfx(graphics), mouseX, mouseY, partialTick);
		} finally {
			currentGraphics = null;
		}
	}

	protected final void renderWidgets(int mouseX, int mouseY, float partialTick) {
		if (currentGraphics != null) {
			super.extractRenderState(currentGraphics, mouseX, mouseY, partialTick);
		}
	}

	protected abstract void renderContent(Gfx graphics, int mouseX, int mouseY, float partialTick);
}
