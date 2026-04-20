package lashes.bot.keyboards;

import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.models.Appointment;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReplyKeyboardFactory {

    public static ReplyKeyboardMarkup getMainMenu(boolean isAdmin) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Проверяем, включены ли отзывы
        boolean reviewsEnabled = DatabaseService.getSetting("reviews_enabled")
            .map(Boolean::parseBoolean).orElse(true);

        // Обычные кнопки
        KeyboardRow row1 = new KeyboardRow();
        row1.add(Constants.BTN_BOOK);
        row1.add(Constants.BTN_PRICE);
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(Constants.BTN_PORTFOLIO);
        
        // Добавляем кнопку отзывов только если функция включена
        if (reviewsEnabled) {
            row2.add(Constants.BTN_REVIEWS);
        }
        keyboard.add(row2);

        // Админ-панель (в боте)
        if (isAdmin) {
            KeyboardRow adminRow = new KeyboardRow();
            adminRow.add(Constants.BTN_ADMIN);
            keyboard.add(adminRow);
        }

        markup.setKeyboard(keyboard);
        return markup;
    }

    /**
     * Главное меню с учетом активной записи пользователя
     */
    public static ReplyKeyboardMarkup getMainMenu(boolean isAdmin, Long userId) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Проверяем, включены ли отзывы
        boolean reviewsEnabled = DatabaseService.getSetting("reviews_enabled")
            .map(Boolean::parseBoolean).orElse(true);

        // Проверяем наличие активной записи
        Optional<Appointment> activeAppointment = DatabaseService.getActiveAppointmentByUser(userId);

        // Обычные кнопки
        KeyboardRow row1 = new KeyboardRow();
        row1.add(Constants.BTN_BOOK);
        row1.add(Constants.BTN_PRICE);
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(Constants.BTN_PORTFOLIO);
        
        // Добавляем кнопку отзывов только если функция включена
        if (reviewsEnabled) {
            row2.add(Constants.BTN_REVIEWS);
        }
        keyboard.add(row2);

        // Третья строка: Мой профиль
        KeyboardRow row3 = new KeyboardRow();
        row3.add(Constants.BTN_MY_PROFILE);
        keyboard.add(row3);

        // Если есть активная запись - добавляем кнопку отмены
        if (activeAppointment.isPresent()) {
            KeyboardRow cancelRow = new KeyboardRow();
            cancelRow.add(Constants.BTN_CANCEL_BOOKING);
            keyboard.add(cancelRow);
        }

        // Админ-панель (в боте)
        if (isAdmin) {
            KeyboardRow adminRow = new KeyboardRow();
            adminRow.add(Constants.BTN_ADMIN);
            keyboard.add(adminRow);
        }

        markup.setKeyboard(keyboard);
        return markup;
    }

}
