package com.noobfly.containersearcher.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;

// only used on 1.21.1, kept empty here so the shared mixin config stays the same
@Mixin(MouseHandler.class)
public abstract class MouseScrollMixin {
}
