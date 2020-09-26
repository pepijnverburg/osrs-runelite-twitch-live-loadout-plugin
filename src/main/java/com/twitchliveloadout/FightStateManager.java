package net.runelite.client.plugins.twitchliveloadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FightStateManager
{
	private HashMap<String, Fight> fights = new HashMap();
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchLiveLoadoutConfig config;
	private final Client client;

	private static final int ON_GRAPHIC_CHANGED_DELAY = 200; // ms
	private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);

	public static final String HIDDEN_PLAYER_ACTOR_NAME = "__self__";
	public static final int MAX_FIGHT_AMOUNT = 10;
	public static final int GRAPHIC_HITSPLAT_EXPIRY_TIME = 2500; // ms, after testing a bit longer than 4 game ticks catches all hitsplats

	public static final int GRAPHIC_SKILL_XP_DROP_EXPIRY_TIME = 1000; // ms, after testing they can be either -1ms or 1ms apart from each other
	private HashMap<Skill, Instant> lastSkillUpdates = new HashMap();

	private static final int MAX_INTERACTING_ACTORS_HISTORY = 2;
	private static final int INTERACTING_ACTOR_EXPIRY_TIME = 5000; // ms
	private HashMap<Actor, Instant> lastInteractingActors = new HashMap();

	public static final boolean ENABLE_SESSION_IDLING = false; // TODO: finish session idling
	public static final int SESSION_IDLING_TIME = 60 * 1000; // ms
	public static final float GAME_TICK_DURATION = 0.6f; // seconds

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

	public enum FightGraphic {
		ICE_BARRAGE(369, Skill.MAGIC, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_BLITZ(367, Skill.MAGIC, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_BURST(363, Skill.MAGIC, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_RUSH(361, Skill.MAGIC, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),

		BLOOD_BARRAGE(377, Skill.MAGIC, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_BLITZ(375, Skill.MAGIC, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_BURST(376, Skill.MAGIC, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_RUSH(373, Skill.MAGIC, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),

		ENTANGLE(179, Skill.MAGIC, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
		SNARE(180, Skill.MAGIC, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
		BIND(181, Skill.MAGIC, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_COUNTERS), // no hitsplat

		SPLASH(85, Skill.MAGIC, FightStatisticEntry.SPELL, FightStatisticProperty.MISS_COUNTERS); // no hitsplat

		private final int graphicId;
		private final Skill xpDropSkill;
		private final FightStatisticEntry entry;
		private final FightStatisticProperty property;

		FightGraphic(int graphicId, Skill xpDropSkill, FightStatisticEntry entry, FightStatisticProperty property) {
			this.graphicId = graphicId;
			this.xpDropSkill = xpDropSkill;
			this.entry = entry;
			this.property = property;
		}

		public int getGraphicId()
		{
			return graphicId;
		}

		public Skill getXpDropSkill()
		{
			return xpDropSkill;
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

	public void onGraphicChanged(GraphicChanged event)
	{

		// delay the handler to make sure other events have time to also be triggered.
		// For example some graphics are translated to statistics, but require a certain skill
		// to receive XP for prevent various false positives when other players are around.
		// However, the XP events come in after the graphic event.
		scheduledExecutor.schedule(new Runnable()
		{
			public void run()
			{
				onGraphicChangedDelayed(event);
			}
		}, ON_GRAPHIC_CHANGED_DELAY, TimeUnit.MILLISECONDS);
	}

	public void onGraphicChangedDelayed(GraphicChanged event)
	{
		final Actor eventActor = event.getActor();
		final String eventActorName = eventActor.getName();
		final Player localPlayer = client.getLocalPlayer();
		final int graphicId = eventActor.getGraphic();
		boolean isLocalPlayer = false;

		if (graphicId < 0)
		{
			return;
		}

		if (localPlayer == null || eventActorName == null)
		{
			return;
		}

		if (eventActor instanceof Player)
		{
			isLocalPlayer = localPlayer.getName().equals(eventActorName);
		}

		final boolean hasInteractedWithActor = lastInteractingActors.containsKey(eventActor);
		final Actor interactingActor = localPlayer.getInteracting();
		final boolean isInteractingWithActor = (eventActor == interactingActor);
		final boolean otherPlayersPresent = otherPlayersPresent(eventActor);

		// Only allow tracking of graphic IDs for combat statistics in single combat areas or multi
		// when there are no other players. This is due to the fact that we cannot classify a certain
		// graphic to the local player. This would cause for example range hits to be classified as
		// a barrage when someone else triggered the barrage graphic on the same enemy.
		if (isInMultiCombatArea() && otherPlayersPresent)
		{
			return;
		}

		// Guard: make sure the actor is interacted with
		if (!isLocalPlayer && !hasInteractedWithActor)
		{
			return;
		}

		// Guard: filter out false positives where the local player is not interacting
		// with the enemy for a while where other players can potentially trigger the same graphics
		if (otherPlayersPresent && !isLocalPlayer && hasInteractedWithActor && !isInteractingWithActor)
		{
			final Instant now = Instant.now();
			final Instant lastInteractedOn = lastInteractingActors.get(eventActor);
			final boolean interactingIsExpired = now.isAfter(lastInteractedOn.plusMillis(INTERACTING_ACTOR_EXPIRY_TIME));

			if (interactingIsExpired)
			{
				return;
			}
		}

		for (FightGraphic graphic : FightGraphic.values())
		{
			int fightGraphicId = graphic.getGraphicId();
			FightStatisticProperty property = graphic.getProperty();
			FightStatisticEntry entry = graphic.getEntry();
			Skill xpDropSkill = graphic.getXpDropSkill();
			Instant xpDropSkillUpdate = lastSkillUpdates.get(xpDropSkill);

			if (fightGraphicId != graphicId)
			{
				continue;
			}

			log.error("Graphic ID changed: {}", graphicId);

			// Guard: check if the required skill has been updated recently.
			// This is to prevent false positives where for example another player is splashing on the enemy
			// and the local player is interacting with that enemy. By checking a skill xp drop we can filter
			// these false positives partially. It will not for example work when the local player is alching
			// and at the same time interacting with the enemy. These are edge-cases we accept.
			if (xpDropSkill != null && otherPlayersPresent)
			{
				if (xpDropSkillUpdate == null)
				{
					continue;
				}

				Instant now = Instant.now();
				Instant expiryTime = xpDropSkillUpdate.plusMillis(GRAPHIC_SKILL_XP_DROP_EXPIRY_TIME);
				boolean skillXpDropIsExpired = now.isAfter(expiryTime);

				log.error("Is Expired: "+ skillXpDropIsExpired);
				log.error("MS before expiry: "+ (expiryTime.toEpochMilli() - now.toEpochMilli()));

				if (skillXpDropIsExpired)
				{
					continue;
				}
			}

			// When all checks passed make sure the fight exists
			Fight fight = ensureFight(eventActor);

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
				fight.queueStatistic(eventActor, entry, property, GRAPHIC_HITSPLAT_EXPIRY_TIME);
			}
		}
	}

	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor eventActor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		Player player = client.getLocalPlayer();
		HeadIcon headIcon = player.getOverheadIcon();
		Hitsplat.HitsplatType hitsplatType = hitsplat.getHitsplatType();
		boolean isOnSelf = isLocalPlayer(eventActor);

		// Guard: some hitsplats can come from other sources and we will only handle them
		// when there is already a fight to prevent random fights to appear out of nowhere
		// because of activity of others.
		if (hitsplatType == Hitsplat.HitsplatType.POISON || hitsplatType == Hitsplat.HitsplatType.VENOM)
		{
			registerExistingFightHitsplat(eventActor, FightStatisticEntry.POISON, hitsplat);
			return;
		}

		if (hitsplatType == Hitsplat.HitsplatType.HEAL)
		{
			registerExistingFightHitsplat(eventActor, FightStatisticEntry.HIT_HEAL, hitsplat);
			return;
		}

		if (hitsplatType == Hitsplat.HitsplatType.DISEASE)
		{
			// not worth tracking
			return;
		}

		// Guard: check if the hitsplat is damage of the local player
		// if not we will register it as a hit from an 'other' source that is also useful
		// when showing the combat statistics
		if (!hitsplat.isMine())
		{
			registerExistingFightHitsplat(eventActor, FightStatisticEntry.OTHER, hitsplat);
			return;
		}

		// TODO: later recognize what damage type was done (magic, ranged or melee).
		registerEnsuredFightHitsplat(eventActor, FightStatisticEntry.TOTAL, hitsplat);

		// Register damage done while having smite up and dealing damage to other entity
		if (!isOnSelf && headIcon == HeadIcon.SMITE)
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

		Fight fight = getFight(eventActor);

		if (!fight.hasSession(eventActor))
		{
			return;
		}

		fight.finishSession(eventActor);
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
			lastInteractingActors.remove(0);
		}
	}

	public void onGameTick(GameTick tick)
	{
		registerIdleGameTick();
		registerInteractingGameTick();
	}

	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();

		registerSkillUpdate(skill);
	}

	public void onFakeXpDrop(FakeXpDrop event)
	{
		Skill skill = event.getSkill();

		registerSkillUpdate(skill);
	}

	public void registerIdleGameTick()
	{
		for (Fight fight : fights.values())
		{
			FightSession session = fight.getLastSession();

			if (session == null)
			{
				continue;
			}

			if (!session.isIdling())
			{
				return;
			}

			session.addIdleTicks(1);
		}
	}

	public void registerInteractingGameTick()
	{
		Actor interactingActor = client.getLocalPlayer().getInteracting();

		if (interactingActor == null)
		{
			return;
		}

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

	public void registerExistingFightHitsplat(Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		Fight fight = getFight(actor);

		registerFightHitsplat(fight, actor, statisticEntry, hitsplat);
	}

	public void registerEnsuredFightHitsplat(Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		Fight fight = ensureFight(actor);

		registerFightHitsplat(fight, actor, statisticEntry, hitsplat);
	}

	public void registerFightHitsplat(Fight fight, Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		if (fight == null)
		{
			return;
		}

		int amount = hitsplat.getAmount();
		Hitsplat.HitsplatType hitsplatType = hitsplat.getHitsplatType();
		Actor lastActor = fight.getLastActor();
		FightStatistic statistic = fight.ensureStatistic(actor, statisticEntry);

		// only update the last actor when the damage is dealt by the local player
		// the other hitsplats are merely for statistic purposes
		if (lastActor != actor && hitsplat.isMine())
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

		// check for block or damage
		// NOTE: we explicitly don't have a default
		// to make sure the behaviour is predictable after updates
		switch (hitsplatType)
		{
			case BLOCK_ME:
			case BLOCK_OTHER:
				statistic.registerMiss(amount);
				break;
			case DAMAGE_ME:
			case DAMAGE_ME_CYAN:
			case DAMAGE_ME_ORANGE:
			case DAMAGE_ME_WHITE:
			case DAMAGE_ME_YELLOW:
			case DAMAGE_OTHER:
			case DAMAGE_OTHER_CYAN:
			case DAMAGE_OTHER_ORANGE:
			case DAMAGE_OTHER_WHITE:
			case DAMAGE_OTHER_YELLOW:
				statistic.registerHit(amount);
				break;
		}
	}

	public void registerSkillUpdate(Skill skill)
	{
		log.error("skill update: "+ skill.getName());
		lastSkillUpdates.put(skill, Instant.now());
	}

	public boolean hasFight(Actor actor)
	{
		String actorName = actor.getName();

		return fights.containsKey(actorName);
	}

	public Fight ensureFight(Actor actor)
	{
		String actorName = actor.getName();

		if (!fights.containsKey(actorName))
		{
			createFight(actor);
		}

		return getFight(actor);
	}

	public Fight getFight(Actor actor)
	{
		String actorName = actor.getName();

		return fights.get(actorName);
	}

	public void createFight(Actor actor)
	{
		String localPlayerName = client.getLocalPlayer().getName();
		boolean isLocalPlayer = (actor instanceof Player) && localPlayerName.equals(actor.getName());
		Fight fight = new Fight(actor, isLocalPlayer);
		String actorName = fight.getActorName();

		while (fights.size() > 0 && fights.size() >= getMaxFightAmount())
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

		String actorName = fight.getActorName();
		fights.remove(actorName);
		plugin.updateCombatPanel();
	}

	public Fight rotateOldestFight()
	{
		long oldestLastUpdate = -1;
		Fight oldestFight = null;

		for (Fight fight : fights.values())
		{
			long lastUpdate = fight.getLastUpdate();

			if (oldestLastUpdate < 0 || oldestLastUpdate > lastUpdate)
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
		final ArrayList<Fight> includedFights = new ArrayList();

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
			final int maxFightAmount = getMaxFightAmount();
			final Fight firstFight = fights.values().iterator().next();
			includedFights.clear();

			for (int fightIndex = 0; fightIndex < maxFightAmount; fightIndex++)
			{
				includedFights.add(firstFight);
			}
		}

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

		for (FightStatisticEntry statisticKey : FightStatisticEntry.values())
		{
			JsonObject fightStatistic = new JsonObject();

			for (FightStatisticProperty property : FightStatisticProperty.values())
			{
				fightStatistic.add(property.getKey(), new JsonArray());
			}

			statistics.add(statisticKey.getKey(), fightStatistic);
		}

		for (Fight fight : includedFights)
		{
			FightSession totalSession = fight.calculateTotalSession();
			FightSession lastSession = fight.getLastSession();
			String actorName = fight.getActorName();

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

			sessionCounters.add(fight.getFinishedSessionCounter());
			updatedAts.add(fight.getLastUpdate(true));

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

					totalAndLastValue.add(totalValue);
					totalAndLastValue.add(lastValue);
					statisticState.getAsJsonArray(property.getKey()).add(totalAndLastValue);
				}
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

	public boolean isInMultiCombatArea()
	{
		int multiCombatVarBit = client.getVar(Varbits.MULTICOMBAT_AREA);

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

	public int getMaxFightAmount()
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

	public HashMap<String, Fight> getFights()
	{
		return fights;
	}
}
