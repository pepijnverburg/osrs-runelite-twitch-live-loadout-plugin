package com.twitchliveloadout.marketplace.notifications;

import com.google.common.collect.EvictingQueue;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.MarketplaceMessages;
import com.twitchliveloadout.marketplace.products.EbsNotification;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import com.twitchliveloadout.twitch.eventsub.messages.BaseMessage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WidgetNode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class NotificationManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchLiveLoadoutConfig config;
	private final ChatMessageManager chatMessageManager;
	private final Client client;
	private final TwitchApi twitchApi;
	private final MarketplaceManager manager;
	private Instant notificationsLockedUntil;
	private ScheduledFuture overheadResetTask;
	private final CopyOnWriteArrayList<String> twitchChatNotifiedTransactionIds = new CopyOnWriteArrayList<>();

	/**
	 * Queue of all the notifications that should be shown to the player
	 * Note that they can be queued per group of notifications that should trigger at the same time
	 * This is mainly used for triggering different types of notifications.
	 */
	private final EvictingQueue<ArrayList<Notification>> notificationGroupQueue = EvictingQueue.create(NOTIFICATION_QUEUE_MAX_SIZE);

	public NotificationManager(TwitchLiveLoadoutPlugin plugin, TwitchLiveLoadoutConfig config, ChatMessageManager chatMessageManager, Client client, TwitchApi twitchApi, MarketplaceManager manager)
	{
		this.plugin = plugin;
		this.config = config;
		this.chatMessageManager = chatMessageManager;
		this.client = client;
		this.twitchApi = twitchApi;
		this.manager = manager;
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

			// guard: check if this is a notification that should be sent immediately
			if (!ebsNotification.queue)
			{
				log.debug("Sending a notification instantly: "+ notification.ebsNotification.message);
				sendNotification(notification);
				continue;
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

		// guard: make sure the marketplace is active
		if (!manager.isActive())
		{
			return;
		}

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

		plugin.logSupport("Sending notification with message: "+ notification.ebsNotification.message);
		plugin.logSupport("Sending notification with type: "+ notification.ebsNotification.messageType);

		try {
			if (CHAT_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
			{
				sendChatNotification(notification);
			}
			else if (OVERHEAD_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
			{
				sendOverheadNotification(notification);
			}
			else if (POPUP_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
			{
				sendPopupNotification(notification);
			}
			else if (TWITCH_CHAT_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
			{
				sendTwitchChatNotification(notification);
			}

		} catch (Exception exception) {
			plugin.logSupport("Could not send notification due to an error: ", exception);
		}
	}

	private void sendChatNotification(Notification notification)
	{

		// guard: skip when the chat donation message is disabled
		if (notification.isDonationMessage() && !config.chatMessagesEnabled())
		{
			return;
		}

		String message = getMessage(notification);

		// guard: check if the message is valid
		if (message.isEmpty())
		{
			return;
		}

		final Color chatColor = config.chatMessageColor();
		final ChatMessageBuilder chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(chatColor, message)
			.append(ChatColorType.NORMAL);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(chatMessage.build())
			.build());

		lockNotificationsUntil(CHAT_NOTIFICATION_LOCKED_MS);
	}

	private void sendOverheadNotification(Notification notification)
	{

		// guard: skip when the overhead donation message is disabled
		if (notification.isDonationMessage() && !config.overheadMessagesEnabled())
		{
			return;
		}

		Player player = client.getLocalPlayer();
		String message = getMessage(notification);
		int overheadTextDurationMs = config.overheadMessageDurationS() * 1000;

		// guard: check if the message is valid
		if (message.isEmpty())
		{
			return;
		}

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

	private void sendPopupNotification(Notification notification)
	{

		// guard: skip when the popup donation message is disabled
		if (notification.isDonationMessage() && !config.popupMessagesEnabled())
		{
			return;
		}

		boolean hasCustomTitle = null != notification.ebsNotification.popupTitle;
		plugin.runOnClientThread(() -> {
			try {
				String message = getMessage(notification);

				// guard: check if the message is valid
				if (message.isEmpty())
				{
					return;
				}

				WidgetNode widgetNode = client.openInterface((161 << 16) | 13, 660, WidgetModalMode.MODAL_CLICKTHROUGH);
				client.runScript(3343, hasCustomTitle ? notification.ebsNotification.popupTitle : POPUP_NOTIFICATION_TITLE, message, -1);

				plugin.runOnClientThread(() -> {
					Widget w = client.getWidget(660, 1);
					if (w.getWidth() > 0) {
						return;
					}

					client.closeInterface(widgetNode, true);
				});
			} catch (Exception exception) {
				// empty
			}
		});
	}

	private void sendTwitchChatNotification(Notification notification)
	{

		// guard: skip when the Twitch chat messages are disabled
		if (!config.twitchChatBitsDonationMessageEnabled())
		{
			return;
		}

		try {
			String transactionId = notification.marketplaceProduct.getTransaction().id;
			String streamerProductName = notification.marketplaceProduct.getStreamerProduct().name;

			// guard: skip when message was already sent for this transaction
			// NOTE: this means each transaction can only send ONE message to avoid spam
			// we cannot think of a use-case right now when multiple messages would be nice
			if (twitchChatNotifiedTransactionIds.contains(transactionId))
			{
				return;
			}

			// register this transaction ID as being handled
			twitchChatNotifiedTransactionIds.add(transactionId);

			// send the message to the twitch api
			String message = "["+ streamerProductName +"] "+ getMessage(notification);
			twitchApi.sendChatMessage(message);
		} catch (Exception exception) {
			// empty
		}
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
		EbsNotification ebsNotification = notification.ebsNotification;
		String message = ebsNotification.message;
		String messageType = ebsNotification.messageType;
		final MarketplaceProduct marketplaceProduct = notification.marketplaceProduct;
		final MarketplaceEffect marketplaceEffect = notification.marketplaceEffect;

		// guard: make sure the product is valid
		if (marketplaceProduct == null)
		{
			return (message == null ? "Thank you!" : message);
		}

		final TwitchTransaction twitchTransaction = marketplaceProduct.getTransaction();
		final TwitchEventSubType eventSubType = twitchTransaction.eventSubType;
		final BaseMessage eventSubMessage = twitchTransaction.eventSubMessage;
		final boolean isEventSubTransaction = twitchTransaction.isEventSubTransaction();
		final boolean isCurrencyTransaction = twitchTransaction.isCurrencyTransaction();

		// ensure there is a message when it is not set
		if (message == null)
		{

			// get the message from the channel event sub type
			// or use the default bits donation message when this is an EBS bits transaction
			if (isEventSubTransaction) {

				// NOTE: don't check whether the default message is enabled or not via the RuneLite settings
				// this is because you could disable the event in RuneLite, but have it configured in the Twitch Extension.
				// in this scenario we would still like to show the correct message!
				// This avoids confusion when configuring a random event to the event, while disabling the default.
				message = eventSubType.getMessageGetter().execute(config);
			} else if (isCurrencyTransaction) {

				// override the default bits message when its the Twitch chat notification
				if (TWITCH_CHAT_NOTIFICATION_MESSAGE_TYPE.equals(messageType)) {
					message = config.twitchChatBitsDonationMessage();
				} else if (manager.isFreeModeActive()) {
					message = config.defaultFreeModeActivationMessage();
				} else {
					message = config.defaultBitsDonationMessage();
				}
			} else {
				message = "Thank you {viewerName}!";
			}

			// when default chat messages are sent prefix them with the name of the event
			if (message != null && CHAT_NOTIFICATION_MESSAGE_TYPE.equals(notification.ebsNotification.messageType))
			{
				String name = marketplaceProduct.getStreamerProduct().name;
				message = "["+ name +"] "+ message;
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
