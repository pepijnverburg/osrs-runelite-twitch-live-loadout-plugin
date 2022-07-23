package com.twitchliveloadout.marketplace;

import lombok.Getter;

public class MarketplaceModel {
	@Getter
	private final int modelId;
	@Getter
	private final int animationId;
	@Getter
	private final int animationDurationMs; // -1 is never ending animation

	public MarketplaceModel(int modelId, int animationId, int animationDurationMs)
	{
		this.modelId = modelId;
		this.animationId = animationId;
		this.animationDurationMs = animationDurationMs;
	}

	public MarketplaceModel(int modelId, int animationId)
	{
		this(modelId, animationId, -1);
	}

	public MarketplaceModel(int modelId)
	{
		this(modelId, -1, -1);
	}
}
