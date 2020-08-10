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

	public static final int MAX_FIGHT_AMOUNT = 10;
	private static final String ACTOR_NAME_PROPERTY = "actorNames";
	private static final String ACTOR_TYPE_PROPERTY = "actorTypes";
	private static final String ACTOR_ID_PROPERTY = "actorIds";
	private static final String ACTOR_COMBAT_LEVEL_PROPERTY = "actorCombatLevels";
	private static final String GAME_TICK_COUNTERS_PROPERTY = "ticks";
	private static final String GAME_TICK_TOTAL_COUNTERS_PROPERTY = "ticksTotal";
	private static final String SESSION_COUNTERS_PROPERTY = "sessionCounters";
	private static final String STATISTICS_PROPERTY = "statistics";

	private static final int SPLASH_GRAPHIC_ID = 85;

	public enum ActorType {
		NPC("npc"),
		PLAYER("player");

		private final String key;

		ActorType(String key) {
			this.key = key;
		}

		public String getKey()
		{
			return key;
		}
	}

	private enum FightStatisticProperty
	{
		HIT_COUNTERS_TOTAL("hct"),
		MISS_COUNTERS_TOTAL("mct"),
		HIT_DAMAGES_TOTAL("hdt"),
		MISS_DAMAGES_TOTAL("mdt"),
		DURATION_SECONDS_TOTAL("dst"),

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
		int graphicId = eventActor.getGraphic();

		if (graphicId < 0)
		{
			return;
		}

		if (eventActor != interactingActor)
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

		while (fights.size() > 0 && fights.size() >= getMaxFightAmount())
		{
			rotateOldestFight();
		}

		log.debug("Creating new fight for actor {}", actorName);

		fights.put(actorName, new Fight(actor));
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
		JsonArray tickCounters = new JsonArray();
		JsonArray tickTotalCounters = new JsonArray();
		JsonArray sessionCounters = new JsonArray();

		state.add(ACTOR_NAME_PROPERTY, actorNames);
		state.add(ACTOR_TYPE_PROPERTY, actorTypes);
		state.add(ACTOR_ID_PROPERTY, actorIds);
		state.add(ACTOR_COMBAT_LEVEL_PROPERTY, actorCombatLevels);

		state.add(GAME_TICK_COUNTERS_PROPERTY, tickCounters);
		state.add(GAME_TICK_TOTAL_COUNTERS_PROPERTY, tickTotalCounters);
		state.add(SESSION_COUNTERS_PROPERTY, sessionCounters);

		state.add(STATISTICS_PROPERTY, statistics);

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

			actorNames.add(fight.getActorName());
			actorTypes.add(fight.getActorType().getKey());
			actorIds.add(fight.getActorId());
			actorCombatLevels.add(fight.getActorCombatLevel());

			tickCounters.add(lastSession.getGameTickCounter());
			tickTotalCounters.add(totalSession.getGameTickCounter());
			sessionCounters.add(fight.getFinishedSessionCounter());

			for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
			{
				FightStatistic totalStatistic = totalSession.getStatistic(statisticEntry);
				FightStatistic lastStatistic = lastSession.getStatistic(statisticEntry);
				JsonObject statisticState = statistics.getAsJsonObject(statisticEntry.getKey());

				statisticState.getAsJsonArray(FightStatisticProperty.HIT_DAMAGES.getKey()).add(lastStatistic.getHitDamage());
				statisticState.getAsJsonArray(FightStatisticProperty.HIT_COUNTERS.getKey()).add(lastStatistic.getHitCounter());
				statisticState.getAsJsonArray(FightStatisticProperty.MISS_DAMAGES.getKey()).add(lastStatistic.getMissDamage());
				statisticState.getAsJsonArray(FightStatisticProperty.MISS_COUNTERS.getKey()).add(lastStatistic.getMissCounter());
				statisticState.getAsJsonArray(FightStatisticProperty.DURATION_SECONDS.getKey()).add(lastStatistic.getDuration());

				statisticState.getAsJsonArray(FightStatisticProperty.HIT_DAMAGES_TOTAL.getKey()).add(totalStatistic.getHitDamage());
				statisticState.getAsJsonArray(FightStatisticProperty.HIT_COUNTERS_TOTAL.getKey()).add(totalStatistic.getHitCounter());
				statisticState.getAsJsonArray(FightStatisticProperty.MISS_DAMAGES_TOTAL.getKey()).add(totalStatistic.getMissDamage());
				statisticState.getAsJsonArray(FightStatisticProperty.MISS_COUNTERS_TOTAL.getKey()).add(totalStatistic.getMissCounter());
				statisticState.getAsJsonArray(FightStatisticProperty.DURATION_SECONDS_TOTAL.getKey()).add(totalStatistic.getDuration());
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
