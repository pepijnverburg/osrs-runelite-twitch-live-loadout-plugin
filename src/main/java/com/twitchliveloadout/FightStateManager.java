package net.runelite.client.plugins.twitchliveloadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;

import java.util.HashMap;

public class FightStateManager
{
	private HashMap<String, Fight> fights = new HashMap();
	private final TwitchLiveLoadoutConfig config;
	private final TwitchState twitchState;
	private final Client client;

	public static final int MAX_FIGHT_AMOUNT = 10;
	private static final String GAME_TICK_COUNTERS_PROPERTY = "ticks";
	private static final String ACTOR_NAME_PROPERTY = "actorNames";
	private static final String ACTOR_TYPE_PROPERTY = "actorTypes";
	private static final String ACTOR_ID_PROPERTY = "actorIds";
	private static final String STATISTICS_PROPERTY = "statistics";

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
		HIT_COUNTERS("hc"),
		MISS_COUNTERS("mc"),
		HIT_DAMAGES("hd"),
		MISS_DAMAGES("md"),
		EPOCH_SECONDS("es");

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
	}

	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor eventActor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		Player player = client.getLocalPlayer();
		HeadIcon headIcon = player.getOverheadIcon();
		Hitsplat.HitsplatType hitsplatType = hitsplat.getHitsplatType();

		// Guard: check if the damage is on the player themselves.
		if (isLocalPlayer(eventActor))
		{
			return;
		}

		// Poison damage can come from different sources,
		// but will be attributed to the DPS.
		if (hasFight(eventActor)) {
			if (hitsplatType == Hitsplat.HitsplatType.POISON || hitsplatType == Hitsplat.HitsplatType.VENOM) {
				registerFightHitsplat(eventActor, FightStatisticEntry.POISON, hitsplat);
				return;
			}
		}

		// Guard: check if the hitsplat is the players damage.
		if (!hitsplat.isMine())
		{
			return;
		}

		// TODO: later recognize what damage type was done (magic, ranged or melee).
		FightStatisticEntry mainDamageName = FightStatisticEntry.SHARED;
		registerFightHitsplat(eventActor, mainDamageName, hitsplat);

		// Register damage done while having smite up
		if (headIcon == HeadIcon.SMITE)
		{
			registerFightHitsplat(eventActor, FightStatisticEntry.SMITE, hitsplat);
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
		Fight fight = ensureFight(actor);
		fight.addGameTick();
	}

	public void registerFightHitsplat(Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		int damage = hitsplat.getAmount();
		Hitsplat.HitsplatType hitsplatType = hitsplat.getHitsplatType();
		Fight fight = ensureFight(actor);
		FightStatistic statistic = fight.getStatistic(statisticEntry);

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

		return fights.get(actorName);
	}

	public void createFight(Actor actor)
	{
		String actorName = actor.getName();

		while (fights.size() > 0 && fights.size() >= getMaxFightAmount())
		{
			rotateOldestFight();
		}

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
		JsonArray tickCounters = new JsonArray();
		JsonArray actorNames = new JsonArray();
		JsonArray actorTypes = new JsonArray();
		JsonArray actorIds = new JsonArray();

		state.add(GAME_TICK_COUNTERS_PROPERTY, tickCounters);
		state.add(ACTOR_NAME_PROPERTY, actorNames);
		state.add(ACTOR_TYPE_PROPERTY, actorTypes);
		state.add(ACTOR_ID_PROPERTY, actorIds);
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
			tickCounters.add(fight.getGameTickCounter());
			actorNames.add(fight.getActorName());
			actorTypes.add(fight.getActorType().getKey());
			actorIds.add(fight.getActorId());

			for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
			{
				FightStatistic statistic = fight.getStatistic(statisticEntry);
				JsonObject statisticState = statistics.getAsJsonObject(statisticEntry.getKey());

				statisticState.getAsJsonArray(FightStatisticProperty.HIT_DAMAGES.getKey()).add(statistic.getHitDamage());
				statisticState.getAsJsonArray(FightStatisticProperty.HIT_COUNTERS.getKey()).add(statistic.getHitCounter());
				statisticState.getAsJsonArray(FightStatisticProperty.MISS_DAMAGES.getKey()).add(statistic.getMissDamage());
				statisticState.getAsJsonArray(FightStatisticProperty.MISS_COUNTERS.getKey()).add(statistic.getMissCounter());
				statisticState.getAsJsonArray(FightStatisticProperty.EPOCH_SECONDS.getKey()).add(statistic.getEpochSeconds());
			}
		}

		return state;
	}

	private boolean isPlayer(Actor actor)
	{
		return actor instanceof Player;
	}

	private boolean isNpc(Actor actor)
	{
		return actor instanceof NPC;
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
