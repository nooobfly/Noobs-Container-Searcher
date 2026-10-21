package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

// lines that ignore the depth buffer so the highlight shows through walls.
// the explicit texturing hook is what really turns depth testing off, NO_DEPTH_TEST alone left it on
// extends RenderType only to reach its protected state shards
public final class SeeThroughLines extends RenderType {
	public static final RenderType LINES = create(
		"noobs_container_searcher_lines",
		DefaultVertexFormat.POSITION_COLOR_NORMAL,
		VertexFormat.Mode.LINES,
		1536,
		false,
		false,
		CompositeState.builder()
			.setShaderState(RENDERTYPE_LINES_SHADER)
			.setLineState(new LineStateShard(OptionalDouble.empty()))
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
			.setOutputState(ITEM_ENTITY_TARGET)
			.setWriteMaskState(COLOR_WRITE)
			.setCullState(NO_CULL)
			.setTexturingState(new TexturingStateShard("noobs_container_searcher_no_depth", RenderSystem::disableDepthTest, RenderSystem::enableDepthTest))
			.setDepthTestState(NO_DEPTH_TEST)
			.createCompositeState(false)
	);

	private SeeThroughLines() {
		super("", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 0, false, false, () -> { }, () -> { });
	}
}
