package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsEffect {
	public ArrayList<EbsCondition> conditions;
	public Boolean blockingConditions = false;
	public EbsRandomRange durationMs;
	public ArrayList<EbsSpawnOption> spawnOptions;
	public Boolean modelExpired = false;
	public EbsAnimationFrame modelAnimation;
	public EbsModelSet modelSet;
	public EbsAnimationFrame playerAnimation;
	public EbsMovementFrame playerMovement;
	public EbsGraphicFrame playerGraphic;
	public EbsEquipmentFrame playerEquipment;
	public ArrayList<EbsMenuOptionFrame> menuOptions;
	public ArrayList<EbsInterfaceWidgetFrame> interfaceWidgets;
	public EbsSoundEffectFrame soundEffect;
	public EbsStateFrame stateChange;
	public ArrayList<EbsNotification> notifications;
	public ArrayList<EbsProjectileFrame> projectiles;
}
