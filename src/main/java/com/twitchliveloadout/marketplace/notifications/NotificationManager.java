package com.twitchliveloadout.marketplace.notifications;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import java.time.Instant;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

public class NotificationManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final ChatMessageManager chatMessageManager;
	private final Client client;
	private Instant notificationsLockedUntil;

	public NotificationManager(TwitchLiveLoadoutPlugin plugin, ChatMessageManager chatMessageManager, Client client)
	{
		this.plugin = plugin;
		this.chatMessageManager = chatMessageManager;
		this.client = client;
	}

	public void sendChatNotification(MarketplaceProduct marketplaceProduct)
	{
		String message = getDefaultMessage(marketplaceProduct);
		final ChatMessageBuilder chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(message)
			.append(ChatColorType.NORMAL);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.ITEM_EXAMINE)
			.runeLiteFormattedMessage(chatMessage.build())
			.build());

		lockNotificationsUntil(CHAT_NOTIFICATION_LOCKED_MS);
	}

	public void sendOverheadNotification(MarketplaceProduct marketplaceProduct)
	{
		Player player = client.getLocalPlayer();
		String message = getDefaultMessage(marketplaceProduct);

		if (player == null)
		{
			return;
		}

		plugin.runOnClientThread(() -> {
			player.setOverheadText(message);
		});
		plugin.scheduleOnClientThread(() -> {
			player.setOverheadText("");
		}, OVERHEAD_NOTIFICATION_DURATION_MS);
		lockNotificationsUntil(OVERHEAD_NOTIFICATION_LOCKED_MS);
	}

	public boolean canSendNotification()
	{
		return notificationsLockedUntil == null || Instant.now().isAfter(notificationsLockedUntil);
	}

	private String getDefaultMessage(MarketplaceProduct marketplaceProduct)
	{
		final TwitchTransaction transaction = marketplaceProduct.getTransaction();
		final TwitchProduct twitchProduct = marketplaceProduct.getTwitchProduct();
		String username = "viewer";
		String donationName = "your donation";

		if (transaction != null)
		{
			username = transaction.user_name;
		}

		if (twitchProduct != null)
		{
			final int costAmount = twitchProduct.cost.amount;
			final String costType = twitchProduct.cost.type;
			donationName = "donating "+ costAmount +" "+ costType;
		}

		String message = "Thank you "+ username +" for "+ donationName +"!";

		return message;
	}

	private void lockNotificationsUntil(int durationMs)
	{
		Instant newLockedUntil = Instant.now().plusMillis(durationMs);

		// guard: skip new locked when not after old lock
		if (notificationsLockedUntil != null && newLockedUntil.isBefore(notificationsLockedUntil))
		{
			return;
		}

		notificationsLockedUntil = newLockedUntil;
	}
}
