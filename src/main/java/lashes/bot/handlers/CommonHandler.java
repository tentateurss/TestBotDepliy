package lashes.bot.handlers;

import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.keyboards.ReplyKeyboardFactory;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import lashes.bot.utils.SubscriptionChecker;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

public class CommonHandler {

    public void handleStart(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String userName = msg.getFrom().getFirstName();

        BotStateManager.clear(chatId);

        // Используем новый метод isAdmin()
        boolean isAdmin = BotConfig.isAdmin(userId);

        // Получаем приветственное сообщение из настроек
        String welcomeText = DatabaseService.getSetting("welcome_message").orElse(
            String.format(
                "🌸 *Добро пожаловать, %s!*\n\n" +
                "Я бот для записи на наращивание ресниц.\n\n" +
                "📍 *Режим работы:* ежедневно с 10:00 до 19:00\n" +
                "📍 *Адрес:* ул. Примерная, д. 123\n\n" +
                "Выберите действие в меню:",
                userName
            )
        );
        
        // Заменяем %s на имя пользователя, если есть в тексте
        welcomeText = welcomeText.replace("%s", userName);
        
        bot.sendMessage(chatId, welcomeText, "Markdown",
                ReplyKeyboardFactory.getMainMenu(isAdmin, userId));

        BotStateManager.setState(chatId, UserState.IDLE);

        boolean subscribed = SubscriptionChecker.isSubscribed(bot, userId);
        DatabaseService.setUserSubscribed(userId, subscribed);
    }

    public void sendSubscriptionCheck(Long chatId, LashesBot bot) {
        String text = "⚠️ *Для записи необходимо подписаться на наш канал!*\n\n" +
                "Подпишитесь и нажмите кнопку \"Проверить подписку\"";

        bot.sendMessage(chatId, text, "Markdown",
                InlineKeyboardFactory.getSubscriptionCheckKeyboard());
    }

    public void handleSubscriptionCheck(CallbackQuery callback, LashesBot bot) {
        Long userId = callback.getFrom().getId();
        Long chatId = callback.getMessage().getChatId();

        boolean subscribed = SubscriptionChecker.isSubscribed(bot, userId);
        DatabaseService.setUserSubscribed(userId, subscribed);

        if (subscribed) {
            BotStateManager.setState(chatId, UserState.IDLE);

            bot.editMessageText(
                    "✅ *Подписка подтверждена!*\n\nТеперь вы можете записаться.",
                    chatId,
                    callback.getMessage().getMessageId(),
                    "Markdown"
            );

            boolean isAdmin = BotConfig.isAdmin(userId);
            bot.sendMessage(chatId, "Выберите действие:", null,
                    ReplyKeyboardFactory.getMainMenu(isAdmin, userId));
        } else {
            String text = "❌ *Вы ещё не подписаны!*\n\n" +
                    "Подпишитесь на канал и нажмите кнопку ниже.";

            bot.editMessageText(text, chatId, callback.getMessage().getMessageId(), "Markdown");
            bot.sendMessage(chatId, "Подпишитесь для продолжения:", null,
                    InlineKeyboardFactory.getSubscriptionCheckKeyboard());
        }
    }
}