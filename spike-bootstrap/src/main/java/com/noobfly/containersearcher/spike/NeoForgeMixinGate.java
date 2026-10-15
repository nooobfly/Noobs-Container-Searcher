package com.noobfly.containersearcher.spike;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

// All 9 per-version mixin configs are registered, but only the one that belongs
// to the running Minecraft version is allowed to apply anything.
public final class NeoForgeMixinGate implements IMixinConfigPlugin {
	private boolean active;

	@Override
	public void onLoad(String mixinPackage) {
		try {
			String tag = McVersion.tag(NeoForgeMixinGate.class.getClassLoader());
			active = mixinPackage.contains(".nf." + tag + ".");
		} catch (RuntimeException unsupported) {
			active = false;
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return active;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
