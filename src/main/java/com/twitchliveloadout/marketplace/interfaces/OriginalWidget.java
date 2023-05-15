package com.twitchliveloadout.marketplace.interfaces;

import lombok.Getter;
import net.runelite.api.widgets.Widget;

public class OriginalWidget {
	@Getter
	private final Widget widget;
	@Getter
	private final Boolean hidden;
	@Getter
	private final Integer type;
	@Getter
	private final Integer contentType;
	@Getter
	private final String text;
	@Getter
	private final Integer textColor;
	@Getter
	private final Integer opacity;
	@Getter
	private final Integer itemId;
	@Getter
	private final Integer itemQuantity;
	@Getter
	private final String name;
	@Getter
	private final Integer spriteId;
	@Getter
	private final Integer modelId;
	@Getter
	private final Integer modelZoom;
	@Getter
	private final Integer animationId;

	public OriginalWidget(Widget widget)
	{
		this.widget = widget;

		// initialize all originals
		this.hidden = widget.isSelfHidden();
		this.type = widget.getType();
		this.contentType = widget.getContentType();
		this.text = widget.getText();
		this.textColor = widget.getTextColor();
		this.opacity = widget.getOpacity();
		this.itemId = widget.getItemId();
		this.itemQuantity = widget.getItemQuantity();
		this.name = widget.getName();
		this.spriteId = widget.getSpriteId();
		this.modelId = widget.getModelId();
		this.modelZoom = widget.getModelZoom();
		this.animationId = widget.getAnimationId();
	}
}
