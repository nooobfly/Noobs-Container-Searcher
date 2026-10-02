package com.noobfly.containersearcher.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class CompatScreen extends Screen {
	private GuiGraphics currentGraphics;

	protected CompatScreen(Component title) {
		super(title);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		currentGraphics = graphics;
		try {
			renderContent(new Gfx(graphics), mouseX, mouseY, partialTick);
		} finally {
			currentGraphics = null;
		}
	}

	protected final void renderWidgets(int mouseX, int mouseY, float partialTick) {
		if (currentGraphics != null) {
			super.render(currentGraphics, mouseX, mouseY, partialTick);
		}
	}

	protected abstract void renderContent(Gfx graphics, int mouseX, int mouseY, float partialTick);
}
