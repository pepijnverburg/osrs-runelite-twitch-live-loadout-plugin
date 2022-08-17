package com.twitchliveloadout.fights;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FightStateManager
{
	@Getter
	private ConcurrentHashMap<String, Fight> fights = new ConcurrentHashMap();
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchLiveLoadoutConfig config;
	private final Client client;

	private static final int ON_GRAPHIC_CHANGED_DELAY = 200; // ms
	private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);

	public static final String HIDDEN_PLAYER_ACTOR_NAME = "__self__";
	public static final float GAME_TICK_DURATION = 0.6f; // seconds
	public static final int DEATH_ANIMATION_ID = 836;
	public static final int MAX_FIGHT_AMOUNT = 15;
	public static final int MAX_FINISHED_FIGHT_SESSION_AMOUNT = 1000;
	public static final int MAX_FIGHT_AMOUNT_IN_MEMORY = 50;
	public static final int MAX_FIGHT_DISTANCE = 15; // above max fight range on purpose

	public static final int GRAPHIC_HITSPLAT_EXPIRY_TIME_BASE = 1600; // ms
	public static final int GRAPHIC_HITSPLAT_EXPIRY_TIME_PER_SQUARE = 160; // ms, this varies for spell and enemy distance, this is an approximate

	public static final int GRAPHIC_SKILL_XP_DROP_EXPIRY_TIME = ON_GRAPHIC_CHANGED_DELAY + 50; // ms, takes around 5ms
	private ConcurrentHashMap<Skill, Instant> lastSkillUpdates = new ConcurrentHashMap();
	private ConcurrentHashMap<Skill, Integer> lastSkillXp = new ConcurrentHashMap();

	public static final int GRAPHIC_ANIMATION_EXPIRY_TIME = ON_GRAPHIC_CHANGED_DELAY + 50; // ms, takes around 5ms
	private ConcurrentHashMap<Integer, Instant> lastAnimationUpdates = new ConcurrentHashMap();

	private static final int MAX_INTERACTING_ACTORS_HISTORY = 3;
	private static final int INTERACTING_ACTOR_EXPIRY_TIME = 3000; // ms
	private static final int DEATH_REGISTER_ACTOR_EXPIRY_TIME = 2 * 60000; // ms
	private static final boolean DEATH_REGISTER_MIN_DAMAGE_ENABLED = false;
	private static final float DEATH_REGISTER_MIN_DAMAGE_PERCENTAGE = 0.1f; // 0 to 1 scale
	private static final int INCOMING_FIGHT_SESSION_AUTO_EXPIRY_TIME = 60000; // ms
	private ConcurrentHashMap<Actor, Instant> lastInteractingActors = new ConcurrentHashMap();

	private static final String ACTOR_NAME_KEY = "actorNames";
	private static final String ACTOR_TYPE_KEY = "actorTypes";
	private static final String ACTOR_ID_KEY = "actorIds";
	private static final String ACTOR_COMBAT_LEVEL_KEY = "actorCombatLevels";
	private static final String TOTAL_INTERACTING_TICKS_KEY = "totalInteractingTicks";
	private static final String LAST_INTERACTING_TICKS_KEY = "lastInteractingTicks";
	private static final String TOTAL_DURATIONS_KEY = "totalDurations";
	private static final String LAST_DURATIONS_KEY = "lastDurations";
	private static final String UPDATED_ATS_KEY = "updatedAts";
	private static final String SESSION_COUNTERS_KEY = "sessionCounters";
	private static final String STATISTICS_KEY = "statistics";

	public static final Skill NO_SKILL = null;
	public static final int NO_ANIMATION_ID = -1;
	public static final int SINGLE_ANCIENT_ANIMATION_ID = 1978;
	public static final int MULTI_ANCIENT_ANIMATION_ID = 1979;
	public static final int ENTANGLE_ANIMATION_ID = 710;

	public enum FightGraphic {
		ICE_BARRAGE(369, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_BLITZ(367, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_BURST(363, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_RUSH(361, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),

		BLOOD_BARRAGE(377, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_BLITZ(375, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_BURST(376, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_RUSH(373, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),

		ENTANGLE(179, Skill.MAGIC, NO_SKILL, ENTANGLE_ANIMATION_ID, true, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
		SNARE(180, Skill.MAGIC, NO_SKILL, ENTANGLE_ANIMATION_ID, true, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
		BIND(181, Skill.MAGIC, Skill.HITPOINTS, ENTANGLE_ANIMATION_ID, true, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_COUNTERS), // no hitsplat

		// Note that with the interaction required boolean to true splashes on multi-target enemies will not register (e.g. while barraging).
		// However, this is needed because otherwise splashes from other actors have a very high change to trigger false positives.
		// No invalid skill as multi-target spells can both hit and splash on enemies in the same attack.
		SPLASH(85, Skill.MAGIC, NO_SKILL, NO_ANIMATION_ID, true, FightStatisticEntry.SPELL, FightStatisticProperty.MISS_COUNTERS); // no hitsplat

		private final int graphicId;
		private final Skill requiredSkill;
		private final Skill invalidSkill;
		private final int animationId;
		private final boolean interactionRequired;
		private final FightStatisticEntry entry;
		private final FightStatisticProperty property;

		FightGraphic(int graphicId, Skill requiredSkill, Skill invalidSkill, int animationId, boolean interactionRequired, FightStatisticEntry entry, FightStatisticProperty property) {
			this.graphicId = graphicId;
			this.requiredSkill = requiredSkill;
			this.invalidSkill = invalidSkill;
			this.animationId = animationId;
			this.interactionRequired = interactionRequired;
			this.entry = entry;
			this.property = property;
		}

		public int getGraphicId()
		{
			return graphicId;
		}

		public Skill getRequiredSkill()
		{
			return requiredSkill;
		}

		public Skill getInvalidSkill()
		{
			return invalidSkill;
		}

		public int getAnimationId()
		{
			return animationId;
		}

		public boolean isInteractionRequired()
		{
			return interactionRequired;
		}

		public FightStatisticEntry getEntry()
		{
			return entry;
		}

		public FightStatisticProperty getProperty()
		{
			return property;
		}
	}

	public enum ActorType {
		NPC("npc", "npc"),
		PLAYER("player", "player"),
		GAME_OBJECT("gameObject", "gameObject"),
		LOCAL_PLAYER("localPlayer", "self");

		private final String key;
		private final String name;

		ActorType(String key, String name) {
			this.key = key;
			this.name = name;
		}

		public String getKey()
		{
			return key;
		}

		public String getName()
		{
			return name;
		}
	}

	public FightStateManager(TwitchLiveLoadoutPlugin plugin, TwitchLiveLoadoutConfig config, Client client)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
	}

	public void shutDown()
	{
		clearScheduledUpdates();
		scheduledExecutor.shutdown();
	}

	public void onGraphicChanged(GraphicChanged event)
	{
		final Actor eventActor = event.getActor();
		final String eventActorName = getFormattedActorName(eventActor);
		final int graphicId = eventActor.getGraphic();

		// NOTE: collect this here to make sure the varbit and other things are fetched on the client thread
		final boolean isInMultiCombatArea = isInMultiCombatArea();
		final boolean otherPlayersPresent = otherPlayersPresent(eventActor);

		// guard: skip invalid graphics or actors
		if (graphicId < 0 || eventActorName == null)
		{
			return;
		}

		log.debug("Scheduling delayed onGraphicChanged, graphicId: {}", graphicId);

		// delay the handler to make sure other events have time to also be triggered.
		// For example some graphics are translated to statistics, but require a certain skill
		// to receive XP for prevent various false positives when other players are around.
		// However, the XP events come in after the graphic event.
		scheduledExecutor.schedule(new Runnable()
		{
			public void run()
			{
				try {
					onGraphicChangedDelayed(eventActor, graphicId, isInMultiCombatArea, otherPlayersPresent);
				} catch (Exception exception) {
					log.warn("Could not handle an delayed graphic on changed due to the following error: ", exception);
				}
			}
		}, ON_GRAPHIC_CHANGED_DELAY, TimeUnit.MILLISECONDS);
	}

	public void clearScheduledUpdates()
	{
		scheduledExecutor.getQueue().clear();
	}

	public void onGraphicChangedDelayed(Actor eventActor, int graphicId, boolean isInMultiCombatArea, boolean otherPlayersPresent)
	{
		final Player localPlayer = client.getLocalPlayer();
		final boolean isLocalPlayer = (eventActor == localPlayer);

		log.debug("Handling delayed onGraphicChanged, graphic ID: {}", graphicId);

		if (localPlayer == null)
		{
			return;
		}

		final Instant now = Instant.now();
		final Instant lastInteractedOn = lastInteractingActors.get(eventActor);
		final boolean lastInteractedWithExpired = (lastInteractedOn == null || lastInteractedOn.plusMillis(INTERACTING_ACTOR_EXPIRY_TIME).isBefore(now));
		final boolean validInteractingWith = !lastInteractedWithExpired;
		int distanceTo = localPlayer.getWorldLocation().distanceTo(eventActor.getWorldLocation());

		if (distanceTo > MAX_FIGHT_DISTANCE)
		{
			distanceTo = MAX_FIGHT_DISTANCE;
		}

		for (FightGraphic graphic : FightGraphic.values())
		{
			int fightGraphicId = graphic.getGraphicId();
			boolean interactionRequired = graphic.isInteractionRequired();
			FightStatisticProperty property = graphic.getProperty();
			FightStatisticEntry entry = graphic.getEntry();

			// Guard: check if this is the correct graphic
			if (fightGraphicId != graphicId)
			{
				continue;
			}

			log.debug("Detected fight graphic, now validating... Graphic ID: {}", fightGraphicId);
			log.debug("Required skill time until expiry: {}", (lastInteractedOn == null ? "N/A" : (now.toEpochMilli() - lastInteractedOn.plusMillis(INTERACTING_ACTOR_EXPIRY_TIME).toEpochMilli())));

			// In singles interacting is always required.
			if (!isInMultiCombatArea)
			{
				interactionRequired = true;
			}

			// In multi-combat when there are no players the interaction is not required.
			// The one situation where this goes wrong is with NPC's that are also triggering graphics, such as splashes on other NPC's.
			// An example of this are the Spiritual Mages in GWD.
			if (!otherPlayersPresent && isInMultiCombatArea)
			{
				interactionRequired = false;
			}

			// Most checks only apply when the event target is not the local player
			if (!isLocalPlayer)
			{
				// Guard: single target spells can check whether the local player interacted with the actor.
				// In single combat area's interactions are always required
				if (interactionRequired && !validInteractingWith)
				{
					continue;
				}

				boolean validSkillUpdates = verifySkillsForFightGraphic(graphic);
				boolean validAnimationUpdates = verifyAnimationForFightGraphic(graphic);

				// Guard: check if the required skills and animations were recently updated.
				// This is to prevent false positives where for example another player is splashing on the enemy
				// and the local player is interacting with that enemy. By checking a skill xp drop we can filter
				// these false positives partially.
				if (!validSkillUpdates || !validAnimationUpdates)
				{
					continue;
				}
			}

			// When all checks passed make sure the fight exists
			Fight fight = ensureValidFight(eventActor);

			if (property == FightStatisticProperty.MISS_COUNTERS || property == FightStatisticProperty.MISS_DAMAGES)
			{
				FightStatistic statistic = fight.ensureStatistic(eventActor, entry);
				statistic.registerMiss(0);
			}
			else if (property == FightStatisticProperty.HIT_COUNTERS)
			{
				FightStatistic statistic = fight.ensureStatistic(eventActor, entry);
				statistic.registerHit(0);
			}
			else if (property == FightStatisticProperty.HIT_DAMAGES)
			{
				log.debug("The distance to the enemy for the queue expiry time was: {}", distanceTo);
				final int expiryTimeMs = GRAPHIC_HITSPLAT_EXPIRY_TIME_BASE + GRAPHIC_HITSPLAT_EXPIRY_TIME_PER_SQUARE * distanceTo;
				fight.queueStatistic(eventActor, entry, property, expiryTimeMs);
			}
		}
	}

	private boolean verifySkillsForFightGraphic(FightGraphic graphic)
	{
		Instant now = Instant.now();
		Skill requiredSkill = graphic.getRequiredSkill();
		Skill invalidSkill = graphic.getInvalidSkill();

		// Guard: skip spell tracking when disabled
		if (requiredSkill == Skill.MAGIC && !config.fightStatisticsSpellsEnabled())
		{
			return false;
		}

		if (requiredSkill != null)
		{
			Instant requiredSkillUpdate = lastSkillUpdates.get(requiredSkill);

			// Guard: skip when there was no update at all
			if (requiredSkillUpdate == null)
			{
				return false;
			}

			Instant requiredSkillExpiryTime = requiredSkillUpdate.plusMillis(GRAPHIC_SKILL_XP_DROP_EXPIRY_TIME);
			boolean requiredSkillIsExpired = now.isAfter(requiredSkillExpiryTime);

			log.debug("Required skill time until expiry: {}", (requiredSkillExpiryTime.toEpochMilli() - now.toEpochMilli()));

			if (requiredSkillIsExpired)
			{
				return false;
			}
		}

		if (invalidSkill != null)
		{
			Instant invalidSkillUpdate = lastSkillUpdates.get(invalidSkill);

			// Guard: skip when there was no update at all
			if (invalidSkillUpdate == null)
			{
				return true;
			}

			Instant invalidSkillExpiryTime = invalidSkillUpdate.plusMillis(GRAPHIC_SKILL_XP_DROP_EXPIRY_TIME);
			boolean invalidSkillIsExpired = now.isAfter(invalidSkillExpiryTime);

			log.debug("Invalid skill time until expiry: {}", (invalidSkillExpiryTime.toEpochMilli() - now.toEpochMilli()));

			if (!invalidSkillIsExpired)
			{
				return false;
			}
		}

		return true;
	}

	private boolean verifyAnimationForFightGraphic(FightGraphic graphic)
	{
		Instant now = Instant.now();
		int requiredAnimationId = graphic.getAnimationId();
		Instant requiredSkillUpdate = lastAnimationUpdates.get(requiredAnimationId);

		// Guard: check if an animation should be checked
		if (requiredAnimationId < 0)
		{
			return true;
		}

		// Guard: skip when there was no update at all
		if (requiredSkillUpdate == null)
		{
			return false;
		}

		Instant requiredAnimationExpiryTime = requiredSkillUpdate.plusMillis(GRAPHIC_ANIMATION_EXPIRY_TIME);
		boolean requiredAnimationIsExpired = now.isAfter(requiredAnimationExpiryTime);

		log.debug("Animation time until expiry: {}", (requiredAnimationExpiryTime.toEpochMilli() - now.toEpochMilli()));

		if (requiredAnimationIsExpired)
		{
			return false;
		}

		return true;
	}

	public void onAnimationChanged(AnimationChanged event)
	{
		Actor eventActor = event.getActor();
		int animationId = eventActor.getAnimation();
		Player localPlayer = client.getLocalPlayer();

		// Handle animation updates
		if (eventActor == localPlayer)
		{
			lastAnimationUpdates.put(animationId, Instant.now());
		}

		// Handle local player deaths as we cannot use the despawned event
		if (eventActor == localPlayer && animationId == DEATH_ANIMATION_ID)
		{
			if (!hasFight(eventActor))
			{
				return;
			}

			Fight fight = getFight(eventActor);

			fight.finishSession(eventActor);
			fight.increaseSessionCounter();
		}
	}

	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor eventActor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		Player player = client.getLocalPlayer();
		HeadIcon headIcon = player.getOverheadIcon();
		int hitsplatType = hitsplat.getHitsplatType();
		boolean isOnSelf = isLocalPlayer(eventActor);

		// Guard: some hitsplats can come from other sources and we will only handle them
		// when there is already a fight to prevent random fights to appear out of nowhere
		// because of activity of others.
		if (hitsplatType == HitsplatTypeID.POISON || hitsplatType == HitsplatTypeID.VENOM)
		{
			registerExistingFightHitsplat(eventActor, FightStatisticEntry.POISON, hitsplat);
			return;
		}

		if (hitsplatType == HitsplatTypeID.HEAL)
		{
			registerExistingFightHitsplat(eventActor, FightStatisticEntry.HIT_HEAL, hitsplat);
			return;
		}

		if (hitsplatType == HitsplatTypeID.DISEASE)
		{
			// not worth tracking
			return;
		}

		// Guard: check if the hitsplat is damage of the local player
		// if not we will register it as a hit from an 'other' source that is also useful
		// when showing the combat statistics
		if (!hitsplat.isMine())
		{
			if (config.fightStatisticsOthersEnabled())
			{
				registerExistingFightHitsplat(eventActor, FightStatisticEntry.OTHER, hitsplat);
			}
			return;
		}

		// TODO: later recognize what damage type was done (magic, ranged or melee).
		registerEnsuredFightHitsplat(eventActor, FightStatisticEntry.TOTAL, hitsplat);

		// Register damage done while having smite up and dealing damage to other entity
		if (!isOnSelf && isPlayer(eventActor) && headIcon == HeadIcon.SMITE)
		{
			registerEnsuredFightHitsplat(eventActor, FightStatisticEntry.SMITE, hitsplat);
		}
	}

	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();
		final Actor eventActor = npcDespawned.getActor();

		if (!npc.isDead())
		{
			return;
		}

		onActorDespawned(eventActor);
	}

	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		final Player player = playerDespawned.getPlayer();
		final Actor eventActor = playerDespawned.getActor();

		// Guard: make sure the player died
		if (player.getHealthRatio() != 0)
		{
			return;
		}

		onActorDespawned(eventActor);
	}

	private void onActorDespawned(Actor eventActor)
	{

		if (!hasFight(eventActor))
		{
			return;
		}

		Instant now = Instant.now();
		Fight fight = getFight(eventActor);
		FightSession session = fight.getSession(eventActor);

		if (session == null)
		{
			return;
		}

		Instant lastUpdate = session.getLastUpdate(true);
		FightStatistic totalStatistic = session.getStatistic(FightStatisticEntry.TOTAL);
		FightStatistic otherStatistic = session.getStatistic(FightStatisticEntry.OTHER);
		double totalDamage = totalStatistic.getHitDamage();
		double otherDamage = otherStatistic.getHitDamage();
		double allDamage = totalDamage + otherDamage;
		boolean didEnoughDamage = allDamage > 0 && ((totalDamage / allDamage) > DEATH_REGISTER_MIN_DAMAGE_PERCENTAGE);

		if (DEATH_REGISTER_MIN_DAMAGE_ENABLED && !didEnoughDamage)
		{
			return;
		}

		// Guard: skip the register of the de-spawn if the local player activity was too long ago
		if (lastUpdate == null || lastUpdate.plusMillis(DEATH_REGISTER_ACTOR_EXPIRY_TIME).isBefore(now))
		{
			return;
		}

		fight.finishSession(eventActor);
		fight.increaseSessionCounter();
	}

	public void onInteractingChanged(InteractingChanged interactingChanged)
	{
		Actor source = interactingChanged.getSource();
		Actor target = interactingChanged.getTarget();
		Actor localPlayer = client.getLocalPlayer();

		if (source != localPlayer)
		{
			return;
		}

		if (target == null)
		{
			return;
		}

		log.debug("Adding last interacting target to {}", target.getName());

		lastInteractingActors.put(target, Instant.now());

		if (lastInteractingActors.size() > MAX_INTERACTING_ACTORS_HISTORY)
		{
			rotateOldestInteractingActor();
		}
	}

	private void rotateOldestInteractingActor()
	{
		Actor oldestActor = null;

		for (Actor interactingActor : lastInteractingActors.keySet())
		{
			Instant lastUpdate = lastInteractingActors.get(interactingActor);
			Instant oldestLastUpdate = (oldestActor == null ? null : lastInteractingActors.get(oldestActor));

			if (oldestLastUpdate == null || lastUpdate.isBefore(oldestLastUpdate))
			{
				oldestActor = interactingActor;
			}
		}

		lastInteractingActors.remove(oldestActor);
	}

	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		Integer newExperience = client.getSkillExperience(skill);
		Integer lastExperience = lastSkillXp.get(skill);

		// Guard: make sure experience was added
		// Note: we use the int object to allow for null
		if (newExperience.equals(lastExperience))
		{
			return;
		}

		lastSkillXp.put(skill, newExperience);
		registerSkillUpdate(skill);
	}

	public void onFakeXpDrop(FakeXpDrop event)
	{
		Skill skill = event.getSkill();

		registerSkillUpdate(skill);
	}

	public void onGameTick()
	{
		registerIdleGameTick();
		registerInteractingGameTick();
	}

	private void registerIdleGameTick()
	{
		if (!config.fightStatisticsAutoIdling())
		{
			return;
		}

		final boolean isLoggedIn = (client.getGameState() == GameState.LOGGED_IN);
		final CopyOnWriteArrayList<String> actorNames = getOtherActorNames();

		for (Fight fight : fights.values())
		{
			if (!fight.isIdling(actorNames) && isLoggedIn)
			{
				continue;
			}

			// add shared idle ticks for the total session
			fight.queueIdleTicks(1);

			// add a tick to all of the sessions that currently exist
			// to get the right duration of the last session
			for (FightSession session : fight.getOngoingSessions())
			{
				session.queueIdleTicks(1);
			}
		}
	}

	private void registerInteractingGameTick()
	{
		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null)
		{
			return;
		}

		Actor interactingActor = localPlayer.getInteracting();

		if (interactingActor == null)
		{
			return;
		}

		// Always update the current interacting actor to make sure it doesn't expire
		// while the local player is still interacting with them
		lastInteractingActors.put(interactingActor, Instant.now());

		// Guard: only handle game tick when a fight is initiated (which means one hitsplat was dealt).
		// This is to prevent non-attackable NPC's to also count interacting game ticks.
		if (!hasFight(interactingActor))
		{
			return;
		}

		Fight fight = getFight(interactingActor);

		if (!fight.hasSession(interactingActor))
		{
			return;
		}

		FightSession session = fight.getSession(interactingActor);
		session.addInteractingTicks(1);
	}

	private void registerExistingFightHitsplat(Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		Fight fight = getFight(actor);

		// Guard: check the fight is existing
		if (fight == null)
		{
			return;
		}

		// Guard: check if a session exists for this specific actor.
		// This will prevent hitsplats of others / poison / venom on actors
		// that were never attacked by the local player to be added
		if (!config.fightStatisticsUnattackedEnabled() && !fight.hasSession(actor))
		{
			return;
		}

		registerFightHitsplat(fight, actor, statisticEntry, hitsplat);
	}

	private void registerEnsuredFightHitsplat(Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		Fight fight = ensureValidFight(actor);

		registerFightHitsplat(fight, actor, statisticEntry, hitsplat);
	}

	private void registerFightHitsplat(Fight fight, Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		if (fight == null)
		{
			return;
		}

		Instant now = Instant.now();
		boolean isOnSelf = isLocalPlayer(actor);

		// check if we should automatically finish the last session for incoming damage
		// as it is timed out. This is because the incoming fight sessions are only finishing
		// when the local player died and this is not always indicating a fight ending.
		// An automatic timeout for this makes sense.
		if (isOnSelf)
		{
			Instant lastUpdate = fight.getLastUpdate();

			if (lastUpdate != null && lastUpdate.plusMillis(INCOMING_FIGHT_SESSION_AUTO_EXPIRY_TIME).isBefore(now))
			{
				fight.finishSession(actor);
			}
		}

		int amount = hitsplat.getAmount();
		int hitsplatType = hitsplat.getHitsplatType();

		// NOTE: get the statistic after the fight session was potentially ended!
		FightStatistic statistic = fight.ensureStatistic(actor, statisticEntry);

		// Only update the last actor when the damage is dealt by the local player
		// the other hitsplats are merely for statistic purposes
		if (hitsplat.isMine())
		{
			fight.setLastActor(actor);
		}

		// Handle this damage as being part of the queued statistics.
		// Note that only hitsplats by the local player are handled to
		// prevent other player hits to trigger the queueing
		if (hitsplat.isMine())
		{
			fight.registerQueuedStatistics(actor, amount);
		}

		// Check for block or damage
		// NOTE: we explicitly don't have a default
		// to make sure the behaviour is predictable after updates
		switch (hitsplatType)
		{
			case HitsplatTypeID.DISEASE:
				// not handled
				break;
			case HitsplatTypeID.BLOCK_ME:
			case HitsplatTypeID.BLOCK_OTHER:
				statistic.registerMiss(amount);
				break;
			case HitsplatTypeID.DAMAGE_ME:
			case HitsplatTypeID.DAMAGE_ME_CYAN:
			case HitsplatTypeID.DAMAGE_ME_ORANGE:
			case HitsplatTypeID.DAMAGE_ME_WHITE:
			case HitsplatTypeID.DAMAGE_ME_YELLOW:
			case HitsplatTypeID.DAMAGE_OTHER:
			case HitsplatTypeID.DAMAGE_OTHER_CYAN:
			case HitsplatTypeID.DAMAGE_OTHER_ORANGE:
			case HitsplatTypeID.DAMAGE_OTHER_WHITE:
			case HitsplatTypeID.DAMAGE_OTHER_YELLOW:
			case HitsplatTypeID.VENOM:
			case HitsplatTypeID.POISON:
			case HitsplatTypeID.HEAL:
			case HitsplatTypeID.DAMAGE_MAX_ME:
			case HitsplatTypeID.DAMAGE_MAX_ME_CYAN:
			case HitsplatTypeID.DAMAGE_MAX_ME_ORANGE:
			case HitsplatTypeID.DAMAGE_MAX_ME_WHITE:
			case HitsplatTypeID.DAMAGE_MAX_ME_YELLOW:
				statistic.registerHit(amount);
				break;
		}
	}

	private void registerSkillUpdate(Skill skill)
	{
		lastSkillUpdates.put(skill, Instant.now());
	}

	public Fight ensureValidFight(Actor actor)
	{
		if (!hasFight(actor))
		{
			createFight(actor);
		}

		Fight fight = getFight(actor);
		Instant now = Instant.now();
		Instant lastUpdate = fight.getLastUpdate();
		long expiryTime = config.fightStatisticsExpiryTime() * 60;

		// refresh when fight is expired and the statistics will be non-representative
		if (lastUpdate != null)
		{
			long lastUpdateDelta = now.getEpochSecond() - lastUpdate.getEpochSecond();

			if (lastUpdateDelta > expiryTime)
			{
				deleteFight(fight);
				createFight(actor);
			}
		}

		return getFight(actor);
	}

	public Fight getFight(Actor actor)
	{
		String actorName = getFormattedActorName(actor);

		// guard: make sure the actor is valid
		if (actorName == null)
		{
			return null;
		}

		return fights.get(actorName);
	}

	public boolean hasFight(Actor actor)
	{
		String actorName = getFormattedActorName(actor);

		// guard: make sure the actor is valid
		if (actorName == null)
		{
			return false;
		}

		return fights.containsKey(actorName);
	}

	public void createFight(Actor actor)
	{
		String localPlayerName = client.getLocalPlayer().getName();
		boolean isLocalPlayer = (actor instanceof Player) && localPlayerName.equals(actor.getName());
		String actorName = getFormattedActorName(actor);
		Fight fight = new Fight(actor, actorName, isLocalPlayer);

		// Rotate fights to prevent memory leaks when the client is on for a long time
		while (fights.size() >= MAX_FIGHT_AMOUNT_IN_MEMORY)
		{
			rotateOldestFight();
		}

		log.debug("Creating new fight for actor {}", actorName);

		fights.put(actorName, fight);
		plugin.updateCombatPanel();
	}

	public void deleteFight(Fight fight)
	{

		// guard: check if the fight is valid
		if (fight == null)
		{
			return;
		}

		log.debug("Removing a fight for actor {}", fight.getActorName());

		String actorName = fight.getActorName();
		fights.remove(actorName);
		plugin.updateCombatPanel();
	}

	public Fight rotateOldestFight()
	{
		Instant oldestLastUpdate = null;
		Fight oldestFight = null;

		for (Fight fight : fights.values())
		{
			Instant lastUpdate = fight.getLastUpdate();

			if (oldestLastUpdate == null || lastUpdate == null || lastUpdate.isBefore(oldestLastUpdate))
			{
				oldestLastUpdate = lastUpdate;
				oldestFight = fight;
			}
		}

		if (oldestFight == null)
		{
			return null;
		}

		deleteFight(oldestFight);

		return oldestFight;
	}

	public void deleteAllFights()
	{
		fights.clear();
		plugin.updateCombatPanel();
	}

	public JsonObject getFightStatisticsState()
	{
		CopyOnWriteArrayList<Fight> includedFights = new CopyOnWriteArrayList();
		CopyOnWriteArrayList<FightStatisticEntry> includedStatisticEntries = new CopyOnWriteArrayList();

		final JsonObject state = new JsonObject();
		JsonObject statistics = new JsonObject();
		JsonArray actorNames = new JsonArray();
		JsonArray actorTypes = new JsonArray();
		JsonArray actorIds = new JsonArray();
		JsonArray actorCombatLevels = new JsonArray();
		JsonArray totalInteractingTicks = new JsonArray();
		JsonArray lastInteractingTicks = new JsonArray();
		JsonArray totalDurations = new JsonArray();
		JsonArray lastDurations = new JsonArray();
		JsonArray sessionCounters = new JsonArray();
		JsonArray updatedAts = new JsonArray();

		// prepare the default included fights
		for (Fight fight : fights.values())
		{
			includedFights.add(fight);
		}

		// override the included fights when we want to stress test the state
		if (TwitchState.STATE_STRESS_TEST_ENABLED && fights.size() > 0)
		{
			final int maxFightAmount = getMaxFightAmountInState();
			final Fight firstFight = fights.values().iterator().next();
			includedFights.clear();

			for (int fightIndex = 0; fightIndex < maxFightAmount; fightIndex++)
			{
				includedFights.add(firstFight);
			}
		}

		int fightAmount = includedFights.size();
		int maxFightAmountInState = getMaxFightAmountInState();

		if (fightAmount > maxFightAmountInState) {
			fightAmount = maxFightAmountInState;
		}

		// order by last update time
		Collections.sort(includedFights, new FightSorter());

		// only send a specific maximum to Twitch
		CopyOnWriteArrayList<Fight> slicedFights = new CopyOnWriteArrayList();
		slicedFights.addAll(includedFights.subList(0, fightAmount));

		state.add(ACTOR_NAME_KEY, actorNames);
		state.add(ACTOR_TYPE_KEY, actorTypes);
		state.add(ACTOR_ID_KEY, actorIds);
		state.add(ACTOR_COMBAT_LEVEL_KEY, actorCombatLevels);

		state.add(TOTAL_INTERACTING_TICKS_KEY, totalInteractingTicks);
		state.add(LAST_INTERACTING_TICKS_KEY, lastInteractingTicks);

		state.add(TOTAL_DURATIONS_KEY, totalDurations);
		state.add(LAST_DURATIONS_KEY, lastDurations);

		state.add(SESSION_COUNTERS_KEY, sessionCounters);
		state.add(UPDATED_ATS_KEY, updatedAts);

		state.add(STATISTICS_KEY, statistics);

		for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
		{
			JsonObject fightStatistic = new JsonObject();

			for (FightStatisticProperty property : FightStatisticProperty.values())
			{
				fightStatistic.add(property.getKey(), new JsonArray());
			}

			statistics.add(statisticEntry.getKey(), fightStatistic);
		}

		for (Fight fight : slicedFights)
		{
			FightSession totalSession = fight.calculateTotalSession();
			FightSession lastSession = fight.getLastSession();
			String actorName = fight.getActorName();
			Instant lastUpdate = fight.getLastUpdate(true);

			// Hide display name when this is not allowed to be published due to the config
			if (fight.getActorType() == ActorType.LOCAL_PLAYER && !config.playerInfoEnabled())
			{
				actorName = HIDDEN_PLAYER_ACTOR_NAME;
			}

			actorNames.add(actorName);
			actorTypes.add(fight.getActorType().getKey());
			actorIds.add(fight.getActorId());
			actorCombatLevels.add(fight.getActorCombatLevel());

			totalInteractingTicks.add(totalSession.getInteractingTickCounter());
			lastInteractingTicks.add(lastSession.getInteractingTickCounter());

			totalDurations.add(totalSession.getDurationSeconds());
			lastDurations.add(lastSession.getDurationSeconds());

			sessionCounters.add(fight.getSessionCounter());
			updatedAts.add(lastUpdate == null ? 0 : lastUpdate.getEpochSecond());

			for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
			{
				FightStatistic totalStatistic = totalSession.getStatistic(statisticEntry);
				FightStatistic lastStatistic = lastSession.getStatistic(statisticEntry);
				JsonObject statisticState = statistics.getAsJsonObject(statisticEntry.getKey());

				for (FightStatisticProperty property : FightStatisticProperty.values())
				{
					long totalValue = totalStatistic.getValueByProperty(property);
					long lastValue = lastStatistic.getValueByProperty(property);
					JsonArray totalAndLastValue = new JsonArray();

					// check if we need to test the maximum state
					if (TwitchState.STATE_STRESS_TEST_ENABLED)
					{
						totalValue = (int) (Math.random() * TwitchState.MAX_FIGHT_STATISTIC_VALUE);
						lastValue = (int) (Math.random() * TwitchState.MAX_FIGHT_STATISTIC_VALUE);
					}

					if (totalStatistic.isEverUpdated() || lastStatistic.isEverUpdated())
					{
						if (!includedStatisticEntries.contains(statisticEntry))
						{
							includedStatisticEntries.add(statisticEntry);
						}
					}

					totalAndLastValue.add(totalValue);
					totalAndLastValue.add(lastValue);
					statisticState.getAsJsonArray(property.getKey()).add(totalAndLastValue);
				}
			}
		}

		// Save space by filtering out the statistics that have only 0 values.
		// This happens quite often as for example the majority of activities don't
		// have any freezes, heals, blood heals, smite drains etc.
		for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
		{
			if (!includedStatisticEntries.contains(statisticEntry))
			{
				statistics.remove(statisticEntry.getKey());
			}
		}

		return state;
	}

	private boolean isPlayer(Actor actor)
	{
		return actor instanceof Player;
	}

	private boolean isLocalPlayer(Actor actor)
	{
		if (!isPlayer(actor))
		{
			return false;
		}

		Player player = (Player) actor;
		Player localPlayer = client.getLocalPlayer();

		return player == localPlayer;
	}

	public String getFormattedActorName(Actor actor)
	{
		if (actor == null)
		{
			return null;
		}

		// Remove any HTML-like tags from the actor name, this is the case
		// for example with objects getting a <col=00ffff>name</col> tag
		String formattedActorName = actor.getName().replaceAll("\\<[^>]*>","");
		return formattedActorName;
	}

	public boolean isInMultiCombatArea()
	{
		int multiCombatVarBit = client.getVarbitValue(Varbits.MULTICOMBAT_AREA);

		return multiCombatVarBit == 1;
	}

	public boolean otherPlayersPresent(Actor allowedActor)
	{
		// one is to account for the local player themselves
		int allowedPlayerAmount = 1;

		// one for when the currently interacted actor is a player
		if (allowedActor instanceof Player) {
			allowedPlayerAmount += 1;
		}

		return client.getPlayers().size() > allowedPlayerAmount;
	}

	public CopyOnWriteArrayList<String> getOtherActorNames()
	{
		final CopyOnWriteArrayList<String> actorNames = new CopyOnWriteArrayList();
		final CopyOnWriteArrayList<Actor> actors = new CopyOnWriteArrayList();

		actors.addAll(client.getNpcs());
		actors.addAll(client.getPlayers());

		for (Actor actor : actors)
		{
			String actorName = getFormattedActorName(actor);
			actorNames.add(actorName);
		}

		return actorNames;
	}

	public int getMaxFightAmountInState()
	{
		int maxAmount = config.fightStatisticsMaxFightAmount();

		if (maxAmount > MAX_FIGHT_AMOUNT)
		{
			maxAmount = MAX_FIGHT_AMOUNT;
		}

		if (maxAmount < 0)
		{
			maxAmount = 0;
		}

		return maxAmount;
	}
}
