package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsEffect {
	public ArrayList<EbsCondition> conditions;
	public Boolean blockingConditions = false; // TMP: for backwards compatibility, remove after full update
	public Boolean breakOnInvalidConditions = false;
	public Boolean breakOnValidConditions = false;
	public EbsRandomRange durationMs;
	public ArrayList<EbsSpawnOption> spawnOptions;
	public Boolean modelExpired = false;
	public Boolean productExpired = false;
	public EbsAnimationFrame modelAnimation;
	public EbsModelOverheadFrame modelOverhead;
	public EbsModelSet modelSet;
	public EbsAnimationFrame playerAnimation;
	public EbsMovementFrame playerMovement;
	public EbsGraphicFrame playerGraphic;
	public EbsEquipmentFrame playerEquipment;
	public ArrayList<EbsMenuOptionFrame> menuOptions;
	public ArrayList<EbsInterfaceWidgetFrame> interfaceWidgets;
	public EbsDrawFrame drawEffect;
	public EbsSoundEffectFrame soundEffect;
	public EbsStateFrame stateChange;
	public ArrayList<EbsNotification> notifications;
	public ArrayList<EbsProjectileFrame> projectiles;
	public ArrayList<ArrayList<EbsEffect>> effectsOptions;
}
