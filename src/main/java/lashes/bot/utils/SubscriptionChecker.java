package lashes.bot.utils;

import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class SubscriptionChecker {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionChecker.class);

    // Включить/выключить проверку подписки (true = проверять, false = пропускать всех)
    private static final boolean CHECK_SUBSCRIPTION_ENABLED = true;

    public static boolean isSubscribed(LashesBot bot, Long userId) {
        if (!CHECK_SUBSCRIPTION_ENABLED) {
            log.debug("Subscription check disabled for user {}", userId);
            return true;
        }

        if (BotConfig.getChannelId() == null) {
            log.warn("CHANNEL_ID not configured, skipping subscription check");
            return true;
        }

        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(BotConfig.getChannelId().toString());
            getChatMember.setUserId(userId);

            ChatMember member = bot.execute(getChatMember);
            String status = member.getStatus();

            boolean isSubscribed = "member".equals(status)
                    || "administrator".equals(status)
                    || "creator".equals(status);

            if (!isSubscribed) {
                log.info("User {} is NOT subscribed (status: {})", userId, status);
            }

            return isSubscribed;

        } catch (TelegramApiException e) {
            log.error("Failed to check subscription for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}