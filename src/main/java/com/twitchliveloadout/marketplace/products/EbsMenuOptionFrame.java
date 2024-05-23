package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsMenuOptionFrame extends EbsEffectFrame {
	public String type;
	public ArrayList<String> matchedOptions;
	public ArrayList<String> matchedTargets;
	public ArrayList<String> matchedEntityTypes;
	public ArrayList<EbsEffect> onClickEffects;
	public Integer minClickRange;
	public Integer maxClickRange;
}
