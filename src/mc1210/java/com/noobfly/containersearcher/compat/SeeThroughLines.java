package com.noobfly.containersearcher.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

// lines that ignore the depth buffer so the highlight shows through walls
public final class SeeThroughLines extends RenderType {
	private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation("pipeline/noobs_container_searcher_lines")
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.build();

	public static final RenderType LINES = create(
		"noobs_container_searcher_lines",
		1536,
		false,
		false,
		PIPELINE,
		CompositeState.builder()
			.setLineState(new LineStateShard(OptionalDouble.empty()))
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.setOutputState(ITEM_ENTITY_TARGET)
			.createCompositeState(false)
	);

	private SeeThroughLines() {
		super("", 0, false, false, () -> { }, () -> { });
	}

	@Override
	public void draw(com.mojang.blaze3d.vertex.MeshData data) {
		throw new UnsupportedOperationException();
	}

	@Override
	public RenderPipeline pipeline() {
		return PIPELINE;
	}

	@Override
	public com.mojang.blaze3d.vertex.VertexFormat format() {
		return com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL;
	}

	@Override
	public com.mojang.blaze3d.vertex.VertexFormat.Mode mode() {
		return com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES;
	}
}
