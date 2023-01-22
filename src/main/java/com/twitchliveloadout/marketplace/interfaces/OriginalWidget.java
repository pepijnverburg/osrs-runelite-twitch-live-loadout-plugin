package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.marketplace.products.EbsInterfaceWidgetFrame;
import lombok.Getter;
import net.runelite.api.widgets.Widget;

import java.time.Instant;

public class OriginalWidget {
	@Getter
	private final Widget widget;
	@Getter
	private final Boolean originalHidden;
	@Getter
	private final String originalText;
	@Getter
	private final Integer originalTextColor;

	public OriginalWidget(Widget widget)
	{
		this.widget = widget;
		this.originalHidden = widget.isSelfHidden();
		this.originalText = widget.getText();
		this.originalTextColor = widget.getTextColor();
	}
}
