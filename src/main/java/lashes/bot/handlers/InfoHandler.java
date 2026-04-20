package lashes.bot.handlers;

import lashes.LashesBot;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Optional;

public class InfoHandler {
    private static final Logger log = LoggerFactory.getLogger(InfoHandler.class);

    public void handlePrice(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        
        // Проверяем, есть ли сохранённое фото прайса
        Optional<String> photoIdOpt = DatabaseService.getSetting("price_photo_id");
        if (photoIdOpt.isPresent() && !photoIdOpt.get().isBlank()) {
            bot.sendPhoto(chatId, photoIdOpt.get(), "💰 Прайс-лист");
            return;
        }
        
        // Получаем услуги из БД
        List<lashes.bot.models.Service> services = lashes.bot.database.DatabaseService.getAllServices();
        
        String priceText;
        if (services.isEmpty()) {
            // Если услуг нет, показываем дефолтный текст
            priceText = DatabaseService.getSetting("price_text").orElse(
                "💰 *ПРАЙС-ЛИСТ*\n\n" +
                "👁 Наращивание ресниц:\n" +
                "• Классика — 1500₽\n" +
                "• 2D объём — 1800₽\n" +
                "• 3D объём — 2100₽\n" +
                "• Голливуд — 2500₽\n\n" +
                "🔄 Коррекция:\n" +
                "• Классика — 1000₽\n" +
                "• Объём — 1300₽\n\n" +
                "🧼 Снятие ресниц:\n" +
                "• Снятие (свои) — 400₽\n\n" +
                "✨ Дополнительно:\n" +
                "• Окрашивание ресниц — 300₽\n" +
                "• Ламинирование ресниц — 1800₽"
            );
        } else {
            // Формируем прайс из БД
            priceText = lashes.bot.utils.PriceParser.formatPrice(services);
        }
        
        bot.sendMessage(chatId, priceText, "Markdown");
    }

    public void handlePortfolio(Message msg, LashesBot bot) {
        String text = "🎨 *Мои работы*\n\n" +
                "Нажмите на кнопку ниже, чтобы посмотреть портфолио на Pinterest:";

        bot.sendMessage(msg.getChatId(), text, "Markdown",
                InlineKeyboardFactory.getPortfolioKeyboard());
    }
}
