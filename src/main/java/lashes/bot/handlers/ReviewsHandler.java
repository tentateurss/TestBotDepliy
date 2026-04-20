package lashes.bot.handlers;

import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class ReviewsHandler {
    private static final Logger log = LoggerFactory.getLogger(ReviewsHandler.class);

    // Показать меню отзывов клиенту
    public void handleViewReviews(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        
        String reviewsLink = BotConfig.getReviewsChannelLink();
        
        if (reviewsLink == null || reviewsLink.isEmpty()) {
            bot.sendMessage(chatId, "⭐️ Раздел отзывов временно недоступен");
            return;
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Кнопка для перехода в канал с отзывами
        InlineKeyboardButton viewButton = new InlineKeyboardButton();
        viewButton.setText("📖 Посмотреть отзывы");
        viewButton.setUrl(reviewsLink);
        keyboard.add(List.of(viewButton));

        markup.setKeyboard(keyboard);

        bot.sendMessage(chatId, 
            "⭐️ *ОТЗЫВЫ КЛИЕНТОВ*\n\n" +
            "Здесь вы можете посмотреть отзывы наших клиентов о работе мастера.\n\n" +
            "После вашей процедуры бот попросит вас оставить отзыв, " +
            "который будет опубликован в нашем канале с отзывами.",
            "Markdown", markup);
    }

    // Обработка запроса отзыва после процедуры
    public void handleReviewRequest(CallbackQuery callback, LashesBot bot, String data) {
        Long chatId = callback.getMessage().getChatId();
        String[] parts = data.substring(Constants.REVIEW_RATING_PREFIX.length()).split(":");
        
        if (parts.length != 2) return;

        Long appointmentId = Long.parseLong(parts[0]);
        int rating = Integer.parseInt(parts[1]);

        BotStateManager.setData(chatId, "review_appointment_id", appointmentId);
        BotStateManager.setData(chatId, "review_rating", rating);
        BotStateManager.setState(chatId, UserState.AWAITING_REVIEW_COMMENT);

        bot.editMessageText(
            "Спасибо за оценку! " + getStars(rating) + "\n\n" +
            "Теперь напишите ваш отзыв о процедуре.\n" +
            "Он будет опубликован в нашем канале с отзывами.\n\n" +
            "Или отправьте /skip чтобы пропустить.",
            chatId, callback.getMessage().getMessageId());
    }

    // Обработка текста отзыва от клиента
    public void handleReviewComment(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String userName = msg.getFrom().getFirstName();
        String comment = msg.getText();

        if ("/skip".equals(comment)) {
            BotStateManager.clear(chatId);
            bot.sendMessage(chatId, "Спасибо! Будем рады видеть вас снова! 💖");
            return;
        }

        Long appointmentId = BotStateManager.getData(chatId, "review_appointment_id", Long.class);
        Integer rating = BotStateManager.getData(chatId, "review_rating", Integer.class);

        if (appointmentId != null && rating != null) {
            // Сохраняем отзыв в базу данных
            DatabaseService.saveReview(appointmentId, userId, userName, rating, comment);
            
            // Отправляем отзыв в канал с отзывами
            Long reviewsChannelId = BotConfig.getReviewsChannelId();
            
            if (reviewsChannelId != null) {
                String reviewMessage = String.format(
                    "⭐️ *Новый отзыв*\n\n" +
                    "👤 %s\n" +
                    "%s\n\n" +
                    "💬 _%s_",
                    userName,
                    getStars(rating),
                    comment
                );

                SendMessage channelMsg = new SendMessage();
                channelMsg.setChatId(reviewsChannelId.toString());
                channelMsg.setText(reviewMessage);
                channelMsg.setParseMode("Markdown");

                try {
                    bot.execute(channelMsg);
                    log.info("Review sent to reviews channel from user {}", userId);
                } catch (Exception e) {
                    log.warn("Failed to send review to channel (not critical, review saved): {}", e.getMessage());
                }
            }

            bot.sendMessage(chatId, "✅ Спасибо за ваш отзыв! Он опубликован в нашем канале! 💖");
        }

        BotStateManager.clear(chatId);
    }

    // Пропустить отзыв
    public void handleReviewSkip(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        BotStateManager.clear(chatId);
        bot.editMessageText("Спасибо! Будем рады видеть вас снова! 💖", 
            chatId, callback.getMessage().getMessageId());
    }

    private String getStars(int rating) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            stars.append(i < rating ? "⭐️" : "☆");
        }
        return stars.toString();
    }
}
