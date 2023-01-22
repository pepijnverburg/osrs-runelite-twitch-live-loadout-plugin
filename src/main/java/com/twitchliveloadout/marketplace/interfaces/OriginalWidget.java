package com.twitchliveloadout.marketplace.interfaces;

import lombok.Getter;
import net.runelite.api.widgets.Widget;

public class OriginalWidget {
	@Getter
	private final Widget widget;
	@Getter
	private final Boolean originalHidden;
	@Getter
	private final String originalText;
	@Getter
	private final Integer originalTextColor;
	@Getter
	private final Integer originalItemId;
	@Getter
	private final Integer originalItemQuantity;

	public OriginalWidget(Widget widget)
	{
		this.widget = widget;
		this.originalHidden = widget.isSelfHidden();
		this.originalText = widget.getText();
		this.originalTextColor = widget.getTextColor();
		this.originalItemId = widget.getItemId();
		this.originalItemQuantity = widget.getItemQuantity();
	}
}
