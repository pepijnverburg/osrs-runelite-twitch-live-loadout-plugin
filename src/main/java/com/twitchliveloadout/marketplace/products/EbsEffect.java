package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsEffect {
	public ArrayList<EbsCondition> conditions;
	public EbsRandomRange durationMs;
	public ArrayList<EbsSpawnOption> spawnOptions;
	public EbsAnimationFrame modelAnimation;
	public EbsAnimationFrame playerAnimation;
	public EbsMovementFrame playerMovement;
	public EbsGraphicFrame playerGraphic;
	public EbsEquipmentFrame playerEquipment;
	public ArrayList<EbsMenuOptionFrame> menuOptions;
	public ArrayList<EbsInterfaceWidgetFrame> interfaceWidgets;
	public EbsSoundEffectFrame soundEffect;
	public ArrayList<EbsNotification> notifications;
}
