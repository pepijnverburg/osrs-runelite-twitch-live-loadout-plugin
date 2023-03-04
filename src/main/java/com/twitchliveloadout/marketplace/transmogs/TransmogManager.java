package com.twitchliveloadout.marketplace.transmogs;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.products.EbsEquipmentFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.PlayerChanged;
import net.runelite.client.game.ItemManager;

import java.util.concurrent.ConcurrentHashMap;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.TRANSMOG_EFFECT_MAX_SIZE;

@Slf4j
public class TransmogManager extends MarketplaceEffectManager<EbsEquipmentFrame> {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;
	private final ItemManager itemManager;

	/**
	 * Lookup map for which original equipment Ids were present before applying the transmog.
	 * This is used to revert back to the original equipment when the effect expires.
	 * This can be done for multiple players to support transforming other players as well in the future.
	 */
	private final ConcurrentHashMap<String, int[]> originalEquipmentIdsLookup = new ConcurrentHashMap<>();

	public TransmogManager(TwitchLiveLoadoutPlugin plugin, Client client, ItemManager itemManager)
	{
		super(TRANSMOG_EFFECT_MAX_SIZE);
		this.plugin = plugin;
		this.client = client;
		this.itemManager = itemManager;
	}

	public void onGameTick()
	{
		cleanInactiveEffects();
	}

	public void onPlayerChanged(PlayerChanged playerChanged)
	{
		Player player = playerChanged.getPlayer();
		Player localPlayer = client.getLocalPlayer();
		boolean isLocalPlayer = (localPlayer == player);

		// guard: for now we only support changing local players
		if (!isLocalPlayer)
		{
			return;
		}

		// after a change always record the original equipment to revert to after effect are done
		registerOriginalEquipment(player);

		// only after registering the original equipment we apply all the effects
		applyActiveEffects();
	}

	@Override
	protected void applyEffect(MarketplaceEffect<EbsEquipmentFrame> effect)
	{
		Player player = client.getLocalPlayer();

		// guard: check if the player is valid and if we are logged in
		if (player == null || !plugin.isLoggedIn())
		{
			return;
		}

		// fetch the current equipment IDs so we can selectively transmog them
		// only when they are set in the effect, this allows us to have multiple
		// effects influencing different equipment slots, for example one that does a
		// head transmog and another doing the cape transmog
		PlayerComposition composition = player.getPlayerComposition();
		int[] currentEquipmentIds = composition.getEquipmentIds();
		EbsEquipmentFrame equipmentFrame = effect.getFrame();

		// make sure the original is known for this player, if not
		// then the current equipment is considered the original
		if (!hasOriginalEquipment(player))
		{
			registerOriginalEquipment(player);
		}

		// update all the slots that the effect has a transmog available for
		for (int slotId = 0; slotId < currentEquipmentIds.length; slotId++)
		{
			Integer newEquipmentId = equipmentFrame.getEquipmentIdBySlotId(slotId);

			// guard: skip override when equipment is not valid
			if (newEquipmentId == null || newEquipmentId < 0)
			{
				continue;
			}

			currentEquipmentIds[slotId] = newEquipmentId;
		}

		// apply the new equipment
		composition.setHash();
	}

	@Override
	protected void restoreEffect(MarketplaceEffect<EbsEquipmentFrame> effect)
	{
		Player player = client.getLocalPlayer();

		// guard: make sure the player is valid
		if (player == null)
		{
			return;
		}

		String playerName = player.getName();

		// guard: make sure the player name is valid
		if (playerName == null)
		{
			return;
		}

		int[] originalEquipmentIds = originalEquipmentIdsLookup.get(playerName);
		PlayerComposition composition = player.getPlayerComposition();
		int[] currentEquipmentIds = composition.getEquipmentIds();

		System.arraycopy(originalEquipmentIds, 0, currentEquipmentIds, 0, currentEquipmentIds.length);

		// apply the original equipment
		composition.setHash();

		// after the original is restored there might be another one right up in the effect queue
		// for this reason after each restore we will trigger an update to apply a transmog of something that was
		// overridden by this effect
		applyActiveEffects();
	}

	private void registerOriginalEquipment(Player player)
	{

		// guard: make sure the player is valid
		if (player == null)
		{
			return;
		}

		String playerName = player.getName();
		int[] equipmentIds = player.getPlayerComposition().getEquipmentIds();

		// guard: make sure the properties are valid
		if (playerName == null || equipmentIds == null)
		{
			return;
		}

		originalEquipmentIdsLookup.put(playerName, equipmentIds.clone());
	}

	private boolean hasOriginalEquipment(Player player)
	{
		// guard: make sure the player is valid
		if (player == null)
		{
			return false;
		}

		String playerName = player.getName();

		// guard: make sure the name is valid
		if (playerName == null)
		{
			return false;
		}

		return originalEquipmentIdsLookup.containsKey(playerName);
	}

	@Override
	protected void onAddEffect(MarketplaceEffect effect)
	{
		// update immediately when effect is added
		applyActiveEffects();
	}

	@Override
	protected void onDeleteEffect(MarketplaceEffect effect)
	{
		// empty
	}
}
