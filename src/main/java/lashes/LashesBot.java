package lashes;

import lashes.bot.config.BotConfig;
import lashes.bot.database.DatabaseManager;
import lashes.bot.handlers.MessageRouter;
import lashes.bot.utils.ReminderScheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class LashesBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(LashesBot.class);
    private final MessageRouter router;

    public LashesBot() {
        this.router = new MessageRouter();

        try {
            ReminderScheduler.init(this);
        } catch (SchedulerException e) {
            log.error("Failed to initialize reminder scheduler", e);
        }
    }

    @Override
    public String getBotUsername() {
        return BotConfig.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return BotConfig.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            router.route(update, this);
        } catch (Exception e) {
            log.error("Error processing update", e);
        }
    }

    // ============ Вспомогательные методы отправки ============

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null, null);
    }

    public void sendMessage(Long chatId, String text, String parseMode) {
        sendMessage(chatId, text, parseMode, null);
    }

    public void sendMessage(Long chatId, String text, String parseMode, ReplyKeyboard replyMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        if (parseMode != null) {
            message.setParseMode(parseMode);
        }

        if (replyMarkup != null) {
            message.setReplyMarkup(replyMarkup);
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            // Если это ошибка прав в канале - логируем как warning (не критично)
            if (e.getMessage() != null && e.getMessage().contains("administrator rights")) {
                log.warn("Cannot send to channel {} (no admin rights, not critical): {}", chatId, e.getMessage());
            } else {
                log.error("Failed to send message to {}", chatId, e);
            }
        }
    }

    public void editMessageText(String text, Long chatId, Integer messageId) {
        editMessageText(text, chatId, messageId, null);
    }

    public void editMessageText(String text, Long chatId, Integer messageId, String parseMode) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(text);

        if (parseMode != null) {
            edit.setParseMode(parseMode);
        }

        try {
            execute(edit);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message", e);
        }
    }

    public void editMessageReplyMarkup(InlineKeyboardMarkup markup, Long chatId, Integer messageId) {
        EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        // markup может быть null — тогда клавиатура просто убирается
        if (markup != null) {
            edit.setReplyMarkup(markup);
        }

        try {
            execute(edit);
        } catch (TelegramApiException e) {
            log.error("Failed to edit reply markup", e);
        }
    }

    public void sendPhoto(Long chatId, String fileId, String caption) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId.toString());
        sendPhoto.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        if (caption != null) {
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("Markdown");
        }
        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo to {}", chatId, e);
        }
    }

        public void answerCallback(String callbackId) {
        answerCallback(callbackId, null, false);
    }

    public void answerCallback(String callbackId, String text, boolean showAlert) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);

        if (text != null) {
            answer.setText(text);
            answer.setShowAlert(showAlert);
        }

        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback", e);
        }
    }

    // ============ Диагностические методы ============

    public void checkBotAdminStatus() {
        try {
            Long channelId = BotConfig.getChannelId();
            
            // Если канал не настроен - пропускаем проверку
            if (channelId == null) {
                log.info("ℹ️ Channel ID не настроен, пропускаем проверку админ-статуса");
                return;
            }
            
            String botToken = getBotToken();
            String botIdStr = botToken.split(":")[0];
            Long botId = Long.parseLong(botIdStr);

            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(channelId.toString());
            getChatMember.setUserId(botId);

            ChatMember member = execute(getChatMember);
            String status = member.getStatus();

            if ("administrator".equals(status) || "creator".equals(status)) {
                log.info("✅ Бот является администратором канала! Статус: {}", status);
            } else {
                log.warn("❌ Бот НЕ является администратором канала! Статус: {}", status);
            }
        } catch (TelegramApiException e) {
            log.error("❌ Не удалось проверить статус бота в канале: {}", e.getMessage());
        }
    }

    @Override
    public void onClosing() {
        ReminderScheduler.shutdown();
        DatabaseManager.close();
        super.onClosing();
    }
}