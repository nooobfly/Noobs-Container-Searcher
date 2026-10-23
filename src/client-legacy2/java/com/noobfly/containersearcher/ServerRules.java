package com.noobfly.containersearcher;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// server owners switch features off with a datapack: block tags get synced to the client,
// so a tag that exists (it lists minecraft:air) means "off"
public final class ServerRules {
	private static final TagKey<Block> DISABLE_MOD = tag("disable_mod");
	private static final TagKey<Block> DISABLE_REROLL = tag("disable_librarian_reroll");

	private ServerRules() {
	}

	private static TagKey<Block> tag(String name) {
		return TagKey.create(Registries.BLOCK, ResourceLocation.parse("noobs_container_searcher:" + name));
	}

	private static boolean on(TagKey<Block> tag) {
		return Minecraft.getInstance().level != null && Blocks.AIR.defaultBlockState().is(tag);
	}

	public static boolean modDisabled() {
		return on(DISABLE_MOD);
	}

	public static boolean rerollDisabled() {
		return on(DISABLE_MOD) || on(DISABLE_REROLL);
	}
}
