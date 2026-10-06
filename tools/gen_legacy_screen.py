#!/usr/bin/env python3
"""Regenerates the client-legacy* ContainerSearchScreen copies from src/client."""
import re, pathlib

root = pathlib.Path(__file__).resolve().parent.parent / "src"
rel = "java/com/noobfly/containersearcher/ContainerSearchScreen.java"
src = (root / "client" / rel).read_text()

OLD_INPUT = '''	// <<INPUT>>
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
	// <</INPUT>>'''


def common(s):
	s = s.replace("import net.minecraft.resources.Identifier;", "import net.minecraft.resources.ResourceLocation;")
	s = re.sub(r"\bIdentifier\b", "ResourceLocation", s)
	s = s.replace(".identifier()", ".location()")
	return s


def old_resize(s):
	s = s.replace("public void resize(int width, int height) {", "public void resize(Minecraft minecraft, int width, int height) {")
	return s.replace("super.resize(width, height);", "super.resize(minecraft, width, height);")


def old_input(s):
	s = s.replace("import net.minecraft.client.input.KeyEvent;\n", "").replace("import net.minecraft.client.input.MouseButtonEvent;\n", "")
	return re.sub(r"\t// <<INPUT>>.*?// <</INPUT>>", lambda m: OLD_INPUT, s, flags=re.S)


def old_tag_parser(s):
	return s.replace("TagParser.create(ops).parseFully(record.stackData)", "TagParser.parseTag(record.stackData)")


def old_registry(s):
	s = s.replace("BuiltInRegistries.ITEM.getValue(", "BuiltInRegistries.ITEM.get(")
	return s.replace("BuiltInRegistries.BLOCK.getValue(", "BuiltInRegistries.BLOCK.get(")


def old_enchant_loop(s):
	old = re.search(r"\t\tfor \(var entry : registry\.entrySet\(\)\) \{.*?\n\t\t\}\n\t\tallChoices\.sort", s, re.S).group(0)
	new = '''		for (var holder : registry.listElements().toList()) {
			if (!holder.is(EnchantmentTags.TRADEABLE)) {
				continue;
			}
			Enchantment enchantment = holder.value();
			ResourceLocation id = holder.key().location();
			for (int level = enchantment.getMinLevel(); level <= enchantment.getMaxLevel(); level++) {
				String name = Enchantment.getFullname(holder, level).getString();
				allChoices.add(new EnchantmentChoice(id, level, name));
			}
		}
		allChoices.sort'''
	return s.replace(old, new)


variants = {
	"client-legacy": [common, old_resize],                                                       # 1.21.10: event input API
	"client-legacy2": [common, old_resize, old_input],                                           # 1.21.8
	"client-legacy3": [common, old_resize, old_input, old_tag_parser],                           # 1.21.4
	"client-legacy4": [common, old_resize, old_input, old_tag_parser, old_registry, old_enchant_loop],  # 1.21.1
}
for name, steps in variants.items():
	out = src
	for step in steps:
		out = step(out)
	(root / name / rel).write_text(out)
	print("wrote", name)
