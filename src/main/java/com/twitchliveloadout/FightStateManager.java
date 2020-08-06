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
	private final TwitchState twitchState;
	private final Client client;

	private final String GAME_TICK_COUNTERS_PROPERTY = "gtc";

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

	public FightStateManager(TwitchState twitchState, Client client)
	{
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

		if (!hitsplat.isMine())
		{
			return;
		}

		if (isLocalPlayer(eventActor))
		{
			return;
		}

		if (hitsplatType == Hitsplat.HitsplatType.POISON || hitsplatType == Hitsplat.HitsplatType.VENOM)
		{
			registerFightHitsplat(eventActor, FightStatisticEntry.POISON, hitsplat);
			return;
		}

		FightStatisticEntry mainDamageName = FightStatisticEntry.MELEE;

		registerFightHitsplat(eventActor, mainDamageName, hitsplat);

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
		Fight fight = getFight(actor);
		fight.addGameTick();
	}

	public void registerFightHitsplat(Actor actor, FightStatisticEntry statisticEntry, Hitsplat hitsplat)
	{
		int damage = hitsplat.getAmount();
		Hitsplat.HitsplatType hitsplatType = hitsplat.getHitsplatType();
		Fight fight = getFight(actor);
		FightStatistic statistic = fight.getStatistic(statisticEntry);

		// check for block or hit
		switch (hitsplatType)
		{
			case DAMAGE_ME:
			case POISON:
			case VENOM:
				statistic.registerHit(damage);
				break;
			case BLOCK_ME:
				statistic.registerMiss(damage);
				break;
		}
	}

	public Fight getFight(Actor actor)
	{
		String actorName = actor.getName();

		if (!fights.containsKey(actorName))
		{
			fights.put(actorName, new Fight(actor));
		}

		return fights.get(actorName);
	}

	public JsonObject getFightStatisticsState()
	{
		JsonObject state = new JsonObject();
		state.add(GAME_TICK_COUNTERS_PROPERTY, new JsonArray());

		for (FightStatisticEntry statisticKey : FightStatisticEntry.values())
		{
			JsonObject fightStatistic = new JsonObject();

			for (FightStatisticProperty property : FightStatisticProperty.values())
			{
				fightStatistic.add(property.getKey(), new JsonArray());
			}

			state.add(statisticKey.getKey(), fightStatistic);
		}

		for (Fight fight : fights.values())
		{
			state.getAsJsonArray(GAME_TICK_COUNTERS_PROPERTY).add(fight.getGameTickCounter());

			for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
			{
				FightStatistic statistic = fight.getStatistic(statisticEntry);
				JsonObject statisticState = state.getAsJsonObject(statisticEntry.getKey());

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
}
