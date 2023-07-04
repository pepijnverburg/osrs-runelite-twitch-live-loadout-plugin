package com.twitchliveloadout.marketplace.notifications;

import com.google.common.collect.EvictingQueue;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceMessages;
import com.twitchliveloadout.marketplace.products.EbsNotification;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class NotificationManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchLiveLoadoutConfig config;
	private final ChatMessageManager chatMessageManager;
	private final Client client;
	private Instant notificationsLockedUntil;
	private ScheduledFuture overheadResetTask;

	/**
	 * Queue of all the notifications that should be shown to the player
	 * Note that they can be queued per group of notifications that should trigger at the same time
	 * This is mainly used for triggering different types of notifications.
	 */
	private final EvictingQueue<ArrayList<Notification>> notificationGroupQueue = EvictingQueue.create(NOTIFICATION_QUEUE_MAX_SIZE);

	public NotificationManager(TwitchLiveLoadoutPlugin plugin, TwitchLiveLoadoutConfig config, ChatMessageManager chatMessageManager, Client client)
	{
		this.plugin = plugin;
		this.config = config;
		this.chatMessageManager = chatMessageManager;
		this.client = client;
	}

	public void onGameTick()
	{
		handleNotificationsQueue();
	}

	public void handleEbsNotifications(MarketplaceProduct marketplaceProduct, MarketplaceEffect marketplaceEffect, ArrayList<EbsNotification> ebsNotifications)
	{
		if (ebsNotifications == null)
		{
			return;
		}

		ArrayList<Notification> notificationGroup = new ArrayList<>();

		for (EbsNotification ebsNotification : ebsNotifications)
		{
			Notification notification = new Notification(marketplaceProduct, marketplaceEffect, ebsNotification);

			// guard: check if this is a notification that should be send immediately
			if (!ebsNotification.queue)
			{
				log.debug("Sending a notification instantly: "+ notification.ebsNotification.message);
				sendNotification(notification);
				return;
			}

			log.debug("Queueing a notification: "+ notification.ebsNotification.message);

			// otherwise add to the queued group
			notificationGroup.add(notification);
		}

		// guard: only add if the group is valid
		if (notificationGroup.size() <= 0)
		{
			return;
		}

		notificationGroupQueue.add(notificationGroup);
	}

	private void handleNotificationsQueue()
	{

		// guard: check if we can send a new notification
		if (!canSendNotification())
		{
			return;
		}

		// get the first group from the queue
		ArrayList<Notification> notificationGroup = notificationGroupQueue.poll();

		// guard: make sure we have a valid notification group
		if (notificationGroup == null)
		{
			return;
		}

		// handle all notifications
		for (Notification notification: notificationGroup)
		{
			sendNotification(notification);
		}
	}

	private void sendNotification(Notification notification)
	{
		EbsNotification ebsNotification = notification.ebsNotification;
		String messageType = ebsNotification.messageType;

		log.debug("Sending notification: "+ notification.ebsNotification.message);

		if (CHAT_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
		{
			sendChatNotification(notification);
		}
		else if (OVERHEAD_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
		{
			sendOverheadNotification(notification);
		}
	}

	private void sendChatNotification(Notification notification)
	{
		String message = getMessage(notification);

		final ChatMessageBuilder chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(message)
			.append(ChatColorType.NORMAL);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(chatMessage.build())
			.build());

		lockNotificationsUntil(CHAT_NOTIFICATION_LOCKED_MS);
	}

	private void sendOverheadNotification(Notification notification)
	{
		Player player = client.getLocalPlayer();
		String message = getMessage(notification);
		int overheadTextDurationMs = config.marketplaceOverheadTextDurationS() * 1000;

		// guard: skip on invalid player
		if (player == null)
		{
			return;
		}

		// make sure there is only one overhead reset task!
		if (overheadResetTask != null && !overheadResetTask.isDone())
		{
			overheadResetTask.cancel(false);
		}

		plugin.runOnClientThread(() -> {
			player.setOverheadText(message);
		});
		overheadResetTask = plugin.scheduleOnClientThread(() -> {
			player.setOverheadText("");
		}, overheadTextDurationMs);
		lockNotificationsUntil(overheadTextDurationMs + OVERHEAD_NOTIFICATION_PAUSE_MS);
	}

	public void forceHideOverheadText()
	{
		Player player = client.getLocalPlayer();

		// guard: skip on invalid player
		if (player == null)
		{
			return;
		}

		// guard: skip when there is no overhead text at the moment
		if (overheadResetTask == null || overheadResetTask.isDone())
		{
			return;
		}

		plugin.runOnClientThread(() -> {
			player.setOverheadText("");
		});
	}

	private String getMessage(Notification notification)
	{
		String message = notification.ebsNotification.message;
		final MarketplaceProduct marketplaceProduct = notification.marketplaceProduct;
		final MarketplaceEffect marketplaceEffect = notification.marketplaceEffect;

		// guard: make sure the product is valid
		if (marketplaceProduct == null)
		{
			return (message == null ? "Thank you for the donation!" : message);
		}

		final TwitchProduct twitchProduct = marketplaceProduct.getTwitchProduct();

		if (message == null)
		{
			if (twitchProduct == null) {
				message = "Thank you {viewerName} for your donation!";
			} else {
				message = config.marketplaceDefaultDonationMessage();
			}
		}

		String formattedMessage = MarketplaceMessages.formatMessage(message, marketplaceProduct, marketplaceEffect);

		return formattedMessage;
	}

	private boolean canSendNotification()
	{
		return notificationsLockedUntil == null || Instant.now().isAfter(notificationsLockedUntil);
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
