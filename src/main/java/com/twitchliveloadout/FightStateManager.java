package net.runelite.client.plugins.twitchliveloadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

@Slf4j
public class FightStateManager
{
	private HashMap<String, Fight> fights = new HashMap();
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchLiveLoadoutConfig config;
	private final Client client;

	public static final String HIDDEN_PLAYER_ACTOR_NAME = "__self__";
	public static final int MAX_FIGHT_AMOUNT = 10;
	public static final int GRAPHIC_HITSPLAT_EXPIRY_TIME = 2500; // ms
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

	private static final int MAX_INTERACTING_ACTORS_HISTORY = 3;
	private static final int INTERACTING_ACTOR_EXPIRY_TIME = 5000; // ms
	private HashMap<Actor, Instant> interactingActors = new HashMap();

	public enum FightGraphic {
		ICE_BARRAGE(369, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_BLITZ(367, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_BURST(363, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
		ICE_RUSH(361, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),

		BLOOD_BARRAGE(377, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_BLITZ(375, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_BURST(376, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
		BLOOD_RUSH(373, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),

		ENTANGLE(179, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
		SNARE(180, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
		BIND(181, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_COUNTERS), // no hitsplat

		SPLASH(85, FightStatisticEntry.SPELL, FightStatisticProperty.MISS_COUNTERS); // no hitsplat

		private final int graphicId;
		private final FightStatisticEntry entry;
		private final FightStatisticProperty property;

		FightGraphic(int graphicId, FightStatisticEntry entry, FightStatisticProperty property) {
			this.graphicId = graphicId;
			this.entry = entry;
			this.property = property;
		}

		public FightStatisticEntry getEntry()
		{
			return entry;
		}

		public FightStatisticProperty getProperty()
		{
			return property;
		}

		public int getGraphicId()
		{
			return graphicId;
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
		Actor eventActor = event.getActor();
		String eventActorName = eventActor.getName();
		Player localPlayer = client.getLocalPlayer();
		final boolean hasInteractedWithActor = interactingActors.containsKey(eventActor);
		final int graphicId = eventActor.getGraphic();
		boolean isLocalPlayer = false;

		// Only allow tracking of graphic IDs for combat statistics in single combat areas or multi when there are no other players.
		// This is due to the fact that we cannot classify a certain graphic to the local player.
		// This would cause for example range hits to be classified as a barrage when someone else
		// triggered the barrage graphic on the same enemy.
		if (isInMultiCombatArea() && otherPlayersPresent())
		{
			return;
		}

		if (localPlayer == null || eventActorName == null)
		{
			return;
		}

		if (graphicId < 0)
		{
			return;
		}

		if (eventActor instanceof Player)
		{
			isLocalPlayer = localPlayer.getName().equals(eventActorName);
		}

//		log.error("Graphic ID changed: {}", graphicId);

		// Guard: in complex fight situations it is possible that a graphic will be triggered
		// on a previous interacting actors, therefore we check all previous interacting actors.
		// When someone else is triggering the graphic ID it will cause the queue to expire (due to tinted hitsplat).
		// Graphics without a hitsplat are the exception here and might trigger faulty stats.
		if (!hasInteractedWithActor && !isLocalPlayer)
		{
			return;
		}

		// Guard: make sure the interacted actor is not expired.
		// The map does not automatically expire as we can use it for other purposes as well
		// that are not time dependent.
		if (hasInteractedWithActor)
		{
			final Instant now = Instant.now();
			final Instant lastInteractedOn = interactingActors.get(eventActor);
			final boolean isExpired = now.isAfter(lastInteractedOn.plusMillis(INTERACTING_ACTOR_EXPIRY_TIME));

			if (isExpired)
			{
				return;
			}

		}

		for (FightGraphic graphic : FightGraphic.values())
		{
			int fightGraphicId = graphic.getGraphicId();
			FightStatisticProperty property = graphic.getProperty();
			FightStatisticEntry entry = graphic.getEntry();

			if (fightGraphicId != graphicId)
			{
				continue;
			}

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
				// After testing a bit longer than 4 game ticks catches all hitsplats
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

		interactingActors.put(target, Instant.now());

		if (interactingActors.size() > MAX_INTERACTING_ACTORS_HISTORY)
		{
			interactingActors.remove(0);
		}
	}

	public void onGameTick(GameTick tick)
	{
		Actor interactingActor = client.getLocalPlayer().getInteracting();

		if (interactingActor == null)
		{
			return;
		}

		registerFightGameTick(interactingActor);
	}

	public void registerFightGameTick(Actor actor)
	{

		// Guard: only handle game tick when a fight is initiated (which means one hitsplat was dealt).
		// This is to prevent non-attackable NPC's to also count interacting game ticks.
		if (!hasFight(actor))
		{
			return;
		}

		Fight fight = getFight(actor);

		if (!fight.hasSession(actor))
		{
			return;
		}

		FightSession session = fight.getSession(actor);
		session.addGameTicks(1);
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

			totalInteractingTicks.add(totalSession.getGameTickCounter());
			lastInteractingTicks.add(lastSession.getGameTickCounter());

			totalDurations.add(totalSession.getDurationSeconds());
			lastDurations.add(lastSession.getDurationSeconds());

			sessionCounters.add(fight.getFinishedSessionCounter());
			updatedAts.add(fight.getLastUpdate());

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

	public boolean otherPlayersPresent()
	{
		// one is to account for the local player
		return client.getPlayers().size() > 1;
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
