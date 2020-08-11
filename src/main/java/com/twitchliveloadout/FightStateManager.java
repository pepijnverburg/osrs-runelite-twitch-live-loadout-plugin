package net.runelite.client.plugins.twitchliveloadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;

import java.util.HashMap;

@Slf4j
public class FightStateManager
{
	private HashMap<String, Fight> fights = new HashMap();
	private final TwitchLiveLoadoutConfig config;
	private final TwitchState twitchState;
	private final Client client;

	public static final String HIDDEN_PLAYER_ACTOR_NAME = "__self__";
	public static final int MAX_FIGHT_AMOUNT = 10;
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

	private static final int SPLASH_GRAPHIC_ID = 85;

	public enum ActorType {
		NPC("npc"),
		PLAYER("player"),
		LOCAL_PLAYER("localPlayer");

		private final String key;

		ActorType(String key) {
			this.key = key;
		}

		public String getKey()
		{
			return key;
		}
	}

	public enum FightStatisticProperty
	{
		HIT_COUNTERS("hc"),
		MISS_COUNTERS("mc"),
		HIT_DAMAGES("hd"),
		MISS_DAMAGES("md"),
		DURATION_SECONDS("ds");

		private final String key;

		FightStatisticProperty(String key)
		{
			this.key = key;
		}

		public String getKey()
		{
			return key;
		}
	}

	public FightStateManager(TwitchLiveLoadoutConfig config, TwitchState twitchState, Client client)
	{
		this.config = config;
		this.twitchState = twitchState;
		this.client = client;
	}

	public void onGraphicChanged(GraphicChanged event)
	{
		Actor eventActor = event.getActor();
		Actor interactingActor = client.getLocalPlayer().getInteracting();
		Player localPlayer = client.getLocalPlayer();
		int graphicId = eventActor.getGraphic();
		boolean isLocalPlayer = false;

		if (eventActor instanceof Player)
		{
			isLocalPlayer = localPlayer.getName().equals(((Player) eventActor).getName());
		}

		if (graphicId < 0)
		{
			return;
		}

		if (eventActor != interactingActor && !isLocalPlayer)
		{
			return;
		}

		if (graphicId == SPLASH_GRAPHIC_ID)
		{
			Fight fight = ensureFight(eventActor);
			FightStatistic statistic = fight.ensureStatistic(eventActor, FightStatisticEntry.SPLASH);
			statistic.registerMiss(0);
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

		// Poison damage can come from different sources,
		// but will be attributed to the DPS.
		if (hasFight(eventActor)) {
			if (hitsplatType == Hitsplat.HitsplatType.POISON || hitsplatType == Hitsplat.HitsplatType.VENOM) {
				registerFightHitsplat(eventActor, FightStatisticEntry.POISON, hitsplat);
				return;
			}
		}

		// Guard: check if the hitsplat is the players damage.
		// NOTE: we will not guard against hitsplats that are on the player themselves,
		// because we would also like to track this!
		if (!hitsplat.isMine())
		{
			return;
		}

		// TODO: later recognize what damage type was done (magic, ranged or melee).
		FightStatisticEntry mainDamageName = FightStatisticEntry.SHARED;
		registerFightHitsplat(eventActor, mainDamageName, hitsplat);

		// Register damage done while having smite up and dealing damage to other entity
		if (!isOnSelf && headIcon == HeadIcon.SMITE)
		{
			registerFightHitsplat(eventActor, FightStatisticEntry.SMITE, hitsplat);
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

	public void registerFightHitsplat(Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		int damage = hitsplat.getAmount();
		Hitsplat.HitsplatType hitsplatType = hitsplat.getHitsplatType();
		Fight fight = ensureFight(actor);
		Actor lastActor = fight.getLastActor();
		FightStatistic statistic = fight.ensureStatistic(actor, statisticEntry);

		if (lastActor != actor)
		{
			fight.setLastActor(actor);
		}

		// check for block or hit
		switch (hitsplatType)
		{
			case BLOCK_ME:
				statistic.registerMiss(damage);
				break;
			default:
				statistic.registerHit(damage);
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
		String actorName = actor.getName();
		String localPlayerName = client.getLocalPlayer().getName();
		boolean isLocalPlayer = (actor instanceof Player) && localPlayerName.equals(actorName);

		while (fights.size() > 0 && fights.size() >= getMaxFightAmount())
		{
			rotateOldestFight();
		}

		log.debug("Creating new fight for actor {}", actorName);

		fights.put(actorName, new Fight(actor, isLocalPlayer));
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

		String actorName = oldestFight.getActorName();
		fights.remove(actorName);

		return oldestFight;
	}

	public JsonObject getFightStatisticsState()
	{
		JsonObject state = new JsonObject();
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

		for (Fight fight : fights.values())
		{
			FightSession totalSession = fight.calculateTotalSession();
			FightSession lastSession = fight.getLastSession();
			String actorName = fight.getActorName();

			// Hide display name so the front-end can
			// this as the local player and hide the data
			if (fight.getActorType() == ActorType.LOCAL_PLAYER)
			{
				actorName = HIDDEN_PLAYER_ACTOR_NAME;
			}

			actorNames.add(actorName);
			actorTypes.add(fight.getActorType().getKey());
			actorIds.add(fight.getActorId());
			actorCombatLevels.add(fight.getActorCombatLevel());

			totalInteractingTicks.add(totalSession.getGameTickCounter());
			lastInteractingTicks.add(lastSession.getGameTickCounter());
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

	public int getMaxFightAmount() {
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
