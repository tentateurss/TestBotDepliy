package lashes.bot.keyboards;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.models.Appointment;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class InlineKeyboardFactory {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ===================== ПОЛЬЗОВАТЕЛЬСКИЙ КАЛЕНДАРЬ =====================

    public static InlineKeyboardMarkup getCalendar(YearMonth yearMonth, LocalDate selectedDate) {
        // Получаем рабочие дни для отображения индикаторов
        List<LocalDate> workingDays = DatabaseService.getWorkingDays(
                yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return buildCalendar(yearMonth, selectedDate, "user", workingDays);
    }

    // ===================== АДМИНСКИЕ КАЛЕНДАРИ =====================

    /**
     * Календарь для добавления рабочего дня (отмечает уже добавленные дни)
     */
    public static InlineKeyboardMarkup getAdminAddDayCalendar(YearMonth yearMonth) {
        List<LocalDate> workingDays = DatabaseService.getWorkingDays(
                yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return buildCalendar(yearMonth, null, "admin_add", workingDays);
    }

    /**
     * Календарь для закрытия дня (отмечает только рабочие дни)
     */
    public static InlineKeyboardMarkup getAdminCloseDayCalendar(YearMonth yearMonth) {
        List<LocalDate> workingDays = DatabaseService.getWorkingDays(
                yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return buildCalendar(yearMonth, null, "admin_close", workingDays);
    }

    /**
     * Календарь для управления слотами (все дни кликабельны)
     */
    public static InlineKeyboardMarkup getAdminSlotCalendar(YearMonth yearMonth, String slotAction) {
        List<LocalDate> workingDays = DatabaseService.getWorkingDays(
                yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return buildCalendar(yearMonth, null, "admin_slot_" + slotAction, workingDays);
    }

    /**
     * Календарь для просмотра расписания (только рабочие дни кликабельны)
     */
    public static InlineKeyboardMarkup getAdminScheduleCalendar(YearMonth yearMonth) {
        List<LocalDate> workingDays = DatabaseService.getWorkingDays(
                yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return buildCalendar(yearMonth, null, "admin_schedule", workingDays);
    }

    // ===================== ОБЩИЙ BUILDER КАЛЕНДАРЯ =====================

    private static InlineKeyboardMarkup buildCalendar(YearMonth yearMonth, LocalDate selectedDate,
                                                       String mode, List<LocalDate> highlightedDays) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        String prevCb, nextCb;
        if ("user".equals(mode)) {
            prevCb = "calendar_prev_" + yearMonth.minusMonths(1);
            nextCb = "calendar_next_" + yearMonth.plusMonths(1);
        } else {
            prevCb = Constants.ADMIN_CALENDAR_PREV + mode + "_" + yearMonth.minusMonths(1);
            nextCb = Constants.ADMIN_CALENDAR_NEXT + mode + "_" + yearMonth.plusMonths(1);
        }

        // Header — навигация по месяцам
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        headerRow.add(makeBtn("◀️", prevCb));
        headerRow.add(makeBtn(getMonthName(yearMonth.getMonthValue()) + " " + yearMonth.getYear(), "ignore"));
        headerRow.add(makeBtn("▶️", nextCb));
        keyboard.add(headerRow);

        // Дни недели
        String[] weekDays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        List<InlineKeyboardButton> weekDaysRow = new ArrayList<>();
        for (String day : weekDays) {
            weekDaysRow.add(makeBtn(day, "ignore"));
        }
        keyboard.add(weekDaysRow);

        LocalDate firstOfMonth = yearMonth.atDay(1);
        int firstDayOfWeek = firstOfMonth.getDayOfWeek().getValue();
        int daysInMonth = yearMonth.lengthOfMonth();

        int dayCounter = 1;
        int currentDayOfWeek = 1;
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        // Пустые ячейки в начале
        for (int i = 1; i < firstDayOfWeek; i++) {
            currentRow.add(makeBtn(" ", "ignore"));
            currentDayOfWeek++;
        }

        Set<LocalDate> highlighted = highlightedDays != null ? new HashSet<>(highlightedDays) : new HashSet<>();

        while (dayCounter <= daysInMonth) {
            if (currentDayOfWeek > 7) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
                currentDayOfWeek = 1;
            }

            LocalDate date = yearMonth.atDay(dayCounter);
            String label = String.valueOf(dayCounter);
            boolean isWorkingDay = highlighted.contains(date);
            boolean isPast = date.isBefore(LocalDate.now());

            String cbData;

            if ("user".equals(mode)) {
                // Пользовательский календарь с индикаторами
                if (isPast) {
                    // Прошедшие дни - чёрный кружок
                    label = "⚫ " + dayCounter;
                    cbData = "ignore";
                } else if (isWorkingDay) {
                    // Проверяем, есть ли свободные слоты
                    List<String> availableSlots = DatabaseService.getAvailableSlots(date);
                    if (availableSlots.isEmpty()) {
                        // Полностью занятый день - красный крестик
                        label = "❌ " + dayCounter;
                        cbData = "ignore";
                    } else {
                        // Рабочий день со свободными слотами - зелёная галочка
                        label = "✅ " + dayCounter;
                        Map<String, String> data = new HashMap<>();
                        data.put("action", "select_date");
                        data.put("date", date.toString());
                        try {
                            cbData = mapper.writeValueAsString(data);
                        } catch (JsonProcessingException e) {
                            cbData = "ignore";
                        }
                    }
                } else {
                    // Не рабочий день
                    cbData = "ignore";
                }
            } else if (mode.startsWith("admin_close")) {
                // Закрыть день — только рабочие дни активны
                if (isWorkingDay) {
                    label = "✅ " + dayCounter; // отмечаем рабочие дни
                    cbData = Constants.ADMIN_CLOSE_DATE_SELECT + date;
                } else {
                    cbData = "ignore";
                }
            } else if (mode.startsWith("admin_slot_")) {
                // Управление слотами — рабочие дни отмечены
                String action = mode.substring("admin_slot_".length());
                if (isWorkingDay) {
                    label = "✅ " + dayCounter;
                }
                if (isPast) {
                    cbData = "ignore";
                } else {
                    cbData = Constants.ADMIN_SLOT_DATE_SELECT + action + "_" + date;
                }
            } else if (mode.startsWith("admin_schedule")) {
                // Просмотр расписания — только рабочие дни активны, отмечены
                if (isWorkingDay) {
                    label = "📅 " + dayCounter;
                    cbData = Constants.ADMIN_SCHEDULE_DATE_SELECT + date;
                } else {
                    cbData = "ignore";
                }
            } else if (mode.startsWith("admin_add")) {
                // Добавить рабочий день с индикаторами
                if (isPast) {
                    // Прошедшие дни - чёрный кружок
                    label = "⚫ " + dayCounter;
                    cbData = "ignore";
                } else if (isWorkingDay) {
                    // Проверяем, есть ли свободные слоты
                    List<String> availableSlots = DatabaseService.getAvailableSlots(date);
                    if (availableSlots.isEmpty()) {
                        // Полностью занятый день - красный крестик
                        label = "❌ " + dayCounter;
                    } else {
                        // Рабочий день со свободными слотами - зелёная галочка
                        label = "✅ " + dayCounter;
                    }
                    cbData = Constants.ADMIN_DATE_SELECT + date;
                } else {
                    // Не рабочий день - можно добавить
                    cbData = isPast ? "ignore" : Constants.ADMIN_DATE_SELECT + date;
                }
            } else {
                cbData = "ignore";
            }

            currentRow.add(makeBtn(label, cbData));
            dayCounter++;
            currentDayOfWeek++;
        }

        // Пустые ячейки в конце
        while (currentDayOfWeek <= 7) {
            currentRow.add(makeBtn(" ", "ignore"));
            currentDayOfWeek++;
        }
        keyboard.add(currentRow);

        // Кнопка назад
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        if ("user".equals(mode)) {
            backRow.add(makeBtn("🔙 Назад", Constants.BACK_TO_MENU));
        } else {
            backRow.add(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU));
        }
        keyboard.add(backRow);

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== КНОПКИ ВРЕМЕНИ =====================

    public static InlineKeyboardMarkup getTimeSlots(LocalDate date, List<String> slots) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        int count = 0;

        for (String slot : slots) {
            Map<String, String> data = new HashMap<>();
            data.put("action", "select_time");
            data.put("date", date.toString());
            data.put("time", slot);
            String cb;
            try {
                cb = mapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                cb = "time_" + date + "_" + slot;
            }

            row.add(makeBtn(slot, cb));
            count++;

            if (count % 3 == 0) {
                keyboard.add(row);
                row = new ArrayList<>();
            }
        }

        if (!row.isEmpty()) {
            keyboard.add(row);
        }

        // Назад к календарю
        Map<String, String> backData = new HashMap<>();
        backData.put("action", Constants.BACK_TO_CALENDAR);
        String backCb;
        try {
            backCb = mapper.writeValueAsString(backData);
        } catch (JsonProcessingException e) {
            backCb = Constants.BACK_TO_CALENDAR;
        }
        keyboard.add(List.of(makeBtn("🔙 К календарю", backCb)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ПОДТВЕРЖДЕНИЯ =====================

    public static InlineKeyboardMarkup getConfirmationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(makeBtn("✅ Подтвердить запись", Constants.CONFIRM_BOOKING)));
        keyboard.add(List.of(makeBtn("❌ Отменить", Constants.CANCEL_BOOKING)));
        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getSubscriptionCheckKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Получаем ссылку на канал из настроек
        String channelLink = DatabaseService.getSetting("subscription_channel_link")
            .orElse(BotConfig.getChannelLink());

        InlineKeyboardButton subscribeBtn = new InlineKeyboardButton();
        subscribeBtn.setText("📢 Подписаться на канал");
        subscribeBtn.setUrl(channelLink);

        keyboard.add(List.of(subscribeBtn));
        keyboard.add(List.of(makeBtn("🔄 Проверить подписку", Constants.CHECK_SUBSCRIPTION)));
        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== АДМИН МЕНЮ =====================

    public static InlineKeyboardMarkup getAdminMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        String[][] buttons = {
                {"📅 Добавить рабочий день", Constants.ADMIN_ADD_DAY},
                {"⏰ Управление слотами", Constants.ADMIN_MANAGE_SLOTS},
                {"🗓 Настройка расписания шаблоном", Constants.ADMIN_SCHEDULE_TEMPLATE},
                {"🚫 Закрыть день", Constants.ADMIN_CLOSE_DAY},
                {"❌ Отменить запись клиента", Constants.ADMIN_CANCEL_APPOINTMENT},
                {"📋 Просмотр расписания", Constants.ADMIN_VIEW_SCHEDULE},
                {"📊 Статистика", Constants.ADMIN_STATISTICS},
                {"⭐️ Отзывы", Constants.ADMIN_REVIEWS},
                {"💬 Рассылка", Constants.ADMIN_BROADCAST},
                {"💰 Управление прайсом", Constants.ADMIN_PRICE},
                {"⚙️ Настройки бота", Constants.ADMIN_SETTINGS},
                {"🔙 Выйти из админки", Constants.ADMIN_EXIT}
        };

        for (String[] btnData : buttons) {
            keyboard.add(List.of(makeBtn(btnData[0], btnData[1])));
        }

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getSlotManagementMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("➕ Добавить слот", Constants.ADMIN_ADD_SLOTS)));
        keyboard.add(List.of(makeBtn("➖ Удалить слот", Constants.ADMIN_REMOVE_SLOTS)));
        keyboard.add(List.of(makeBtn("🔙 Назад", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ОТМЕНА ЗАПИСИ КЛИЕНТА =====================

    /**
     * Кнопки со списком активных записей
     */
    public static InlineKeyboardMarkup getAppointmentListKeyboard(List<Appointment> appointments) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM HH:mm");
        for (Appointment appt : appointments) {
            String label = "👤 " + appt.getUserName() + " — " + appt.getAppointmentTime().format(dtf);
            String cb = Constants.ADMIN_CANCEL_APPT_PREFIX + appt.getId();
            keyboard.add(List.of(makeBtn(label, cb)));
        }

        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));
        markup.setKeyboard(keyboard);
        return markup;
    }

    /**
     * Подтверждение отмены конкретной записи
     */
    public static InlineKeyboardMarkup getConfirmCancelAppointmentKeyboard(Long apptId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✅ Да, отменить", Constants.ADMIN_CONFIRM_CANCEL_APPT_PREFIX + apptId)));
        keyboard.add(List.of(makeBtn("❌ Нет, вернуться", Constants.ADMIN_CANCEL_APPOINTMENT)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ПРАЙС =====================

    public static InlineKeyboardMarkup getPriceAdminKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✏️ Изменить текст прайса", Constants.ADMIN_EDIT_PRICE)));
        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ОТМЕНА ЗАПИСИ (для пользователя, если ранее открыт диалог) =====================

    public static InlineKeyboardMarkup getCancelConfirmationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(List.of(makeBtn("\u2705 \u0414\u0430, \u043e\u0442\u043c\u0435\u043d\u0438\u0442\u044c \u0437\u0430\u043f\u0438\u0441\u044c", Constants.CONFIRM_CANCEL)));
        keyboard.add(List.of(makeBtn("\u274C \u041d\u0435\u0442, \u043e\u0441\u0442\u0430\u0432\u0438\u0442\u044c", Constants.CANCEL_BOOKING)));
        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ПОРТФОЛИО =====================

    public static InlineKeyboardMarkup getPortfolioKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText("🎨 Смотреть портфолио");
        btn.setUrl("https://ru.pinterest.com/crystalwithluv/_created/");

        markup.setKeyboard(List.of(List.of(btn)));
        return markup;
    }

    // ===================== СТАТИСТИКА =====================

    public static InlineKeyboardMarkup createStatisticsNavigation(int year, int month) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        
        // Предыдущий месяц
        int prevMonth = month - 1;
        int prevYear = year;
        if (prevMonth < 1) {
            prevMonth = 12;
            prevYear--;
        }
        navRow.add(makeBtn("◀️ Назад", Constants.STATS_PREV_MONTH + prevYear + ":" + prevMonth));

        // Следующий месяц
        int nextMonth = month + 1;
        int nextYear = year;
        if (nextMonth > 12) {
            nextMonth = 1;
            nextYear++;
        }
        navRow.add(makeBtn("Вперёд ▶️", Constants.STATS_NEXT_MONTH + nextYear + ":" + nextMonth));

        keyboard.add(navRow);
        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup createBackToAdminButton() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU))));
        return markup;
    }

    // ===================== ОТЗЫВЫ =====================

    public static InlineKeyboardMarkup createReviewKeyboard(Long appointmentId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> ratingRow = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ratingRow.add(makeBtn(i + " ⭐", Constants.REVIEW_RATING_PREFIX + appointmentId + ":" + i));
        }
        keyboard.add(ratingRow);
        keyboard.add(List.of(makeBtn("Пропустить", Constants.REVIEW_SKIP)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== СПИСОК ОЖИДАНИЯ =====================

    public static InlineKeyboardMarkup createWaitingListButton(LocalDate date, String timeSlot) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
            List.of(makeBtn("📝 Встать в очередь", Constants.WAITING_LIST_ADD + date + ":" + timeSlot)),
            List.of(makeBtn("🔙 Назад", Constants.BACK_TO_CALENDAR))
        ));
        return markup;
    }

    // ===================== ВЫБОР ПРОЦЕДУРЫ =====================

    public static InlineKeyboardMarkup getServiceSelectionKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Получаем услуги из БД
        List<lashes.bot.models.Service> services = lashes.bot.database.DatabaseService.getAllServices();
        
        if (services.isEmpty()) {
            // Если услуг нет, показываем дефолтные кнопки
            keyboard.add(List.of(makeBtn("💎 Классика — 1500₽", "service_1")));
            keyboard.add(List.of(makeBtn("✨ 2D объём — 1800₽", "service_2")));
            keyboard.add(List.of(makeBtn("💫 3D объём — 2100₽", "service_3")));
            keyboard.add(List.of(makeBtn("🌟 Голливуд — 2500₽", "service_4")));
            keyboard.add(List.of(makeBtn("🔄 Коррекция классика — 1000₽", "service_5")));
            keyboard.add(List.of(makeBtn("🔄 Коррекция объём — 1300₽", "service_6")));
            keyboard.add(List.of(makeBtn("🧼 Снятие — 400₽", "service_7")));
            keyboard.add(List.of(makeBtn("🎨 Окрашивание — 300₽", "service_8")));
            keyboard.add(List.of(makeBtn("✨ Ламинирование — 1800₽", "service_9")));
        } else {
            // Генерируем кнопки из БД
            for (lashes.bot.models.Service service : services) {
                String emoji = getServiceEmoji(service.getCategory());
                String buttonText = String.format("%s %s — %.0f₽", emoji, service.getName(), service.getPrice());
                keyboard.add(List.of(makeBtn(buttonText, "service_" + service.getId())));
            }
        }

        markup.setKeyboard(keyboard);
        return markup;
    }
    
    private static String getServiceEmoji(String category) {
        switch (category) {
            case "наращивание": return "💎";
            case "коррекция": return "🔄";
            case "снятие": return "🧼";
            case "дополнительно": return "✨";
            default: return "💅";
        }
    }

    public static InlineKeyboardMarkup getPrepaymentConfirmationKeyboard(Long appointmentId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✅ Подтвердить предоплату", Constants.CONFIRM_PREPAYMENT_PREFIX + appointmentId)));
        keyboard.add(List.of(makeBtn("❌ Отклонить", Constants.REJECT_PREPAYMENT_PREFIX + appointmentId)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ВСПОМОГАТЕЛЬНЫЕ =====================

    private static InlineKeyboardButton makeBtn(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private static String getMonthName(int month) {
        String[] months = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        return months[month - 1];
    }

    // ===================== ПРОФИЛЬ =====================

    public static InlineKeyboardMarkup getProfileEditKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✏️ Редактировать профиль", "edit_profile")));
        keyboard.add(List.of(makeBtn("🗑 Удалить профиль", "delete_profile")));
        keyboard.add(List.of(makeBtn("🔙 Главное меню", Constants.BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getProfileKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✏️ Редактировать профиль", "edit_profile")));
        keyboard.add(List.of(makeBtn("🗑 Удалить историю записей", "delete_appointment_history")));
        keyboard.add(List.of(makeBtn("❌ Удалить профиль", "delete_profile")));

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getDeleteProfileConfirmationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✅ Да, удалить", "confirm_delete_profile")));
        keyboard.add(List.of(makeBtn("❌ Отмена", "cancel_delete_profile")));

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getDeleteHistoryConfirmationKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✅ Да, удалить историю", "confirm_delete_history")));
        keyboard.add(List.of(makeBtn("❌ Отмена", "cancel_delete_history")));

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getCancelRequestConfirmationKeyboard(Long appointmentId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✅ Подтвердить отмену", "admin_confirm_cancel_" + appointmentId)));
        keyboard.add(List.of(makeBtn("❌ Отклонить", "admin_reject_cancel_" + appointmentId)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ШАБЛОН РАСПИСАНИЯ (ПОШАГОВЫЙ ВЫБОР) =====================

    public static InlineKeyboardMarkup getTemplateWeekdaysKeyboard(List<Integer> selectedDays) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        String[] weekdays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        int[] dayValues = {1, 2, 3, 4, 5, 6, 0}; // 0 = воскресенье

        // Первая строка: Пн, Вт, Ср, Чт
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String label = weekdays[i];
            if (selectedDays.contains(dayValues[i])) {
                label = "✅ " + label;
            }
            row1.add(makeBtn(label, Constants.TEMPLATE_WEEKDAY_PREFIX + dayValues[i]));
        }
        keyboard.add(row1);

        // Вторая строка: Пт, Сб, Вс
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        for (int i = 4; i < 7; i++) {
            String label = weekdays[i];
            if (selectedDays.contains(dayValues[i])) {
                label = "✅ " + label;
            }
            row2.add(makeBtn(label, Constants.TEMPLATE_WEEKDAY_PREFIX + dayValues[i]));
        }
        keyboard.add(row2);

        // Кнопка продолжить
        if (!selectedDays.isEmpty()) {
            keyboard.add(List.of(makeBtn("➡️ Продолжить", Constants.TEMPLATE_CONTINUE)));
        }
        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getTemplateTimeKeyboard(List<Integer> selectedHours) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Время с 8 до 23 (4 кнопки в ряд)
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int hour = 8; hour <= 23; hour++) {
            String label = String.format("%02d:00", hour);
            if (selectedHours.contains(hour)) {
                label = "✅ " + label;
            }
            currentRow.add(makeBtn(label, Constants.TEMPLATE_TIME_PREFIX + hour));

            if (currentRow.size() == 4) {
                keyboard.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            keyboard.add(currentRow);
        }

        // Кнопка продолжить
        if (!selectedHours.isEmpty()) {
            keyboard.add(List.of(makeBtn("➡️ Продолжить", Constants.TEMPLATE_CONTINUE)));
        }
        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    public static InlineKeyboardMarkup getTemplatePeriodKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("1 неделя", Constants.TEMPLATE_PERIOD_PREFIX + "week")));
        keyboard.add(List.of(makeBtn("2 недели", Constants.TEMPLATE_PERIOD_PREFIX + "2weeks")));
        keyboard.add(List.of(makeBtn("1 месяц", Constants.TEMPLATE_PERIOD_PREFIX + "month")));
        keyboard.add(List.of(makeBtn("2 месяца", Constants.TEMPLATE_PERIOD_PREFIX + "2months")));
        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }

    // ===================== ПРОСМОТР РАСПИСАНИЯ (СПИСОК) =====================

    public static InlineKeyboardMarkup getScheduleViewKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("➡️ Следующий день", Constants.SCHEDULE_VIEW_NEXT_DAY)));
        keyboard.add(List.of(makeBtn("📅 Выбрать день в календаре", Constants.SCHEDULE_VIEW_CALENDAR)));
        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }
    
    // ===================== НАСТРОЙКИ БОТА =====================
    
    public static InlineKeyboardMarkup getSettingsMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(makeBtn("✏️ Приветственное сообщение", Constants.ADMIN_EDIT_WELCOME)));
        keyboard.add(List.of(makeBtn("🔗 Управление ссылками", Constants.ADMIN_EDIT_LINKS)));
        keyboard.add(List.of(makeBtn("💰 Настройка предоплаты", Constants.ADMIN_EDIT_PREPAYMENT)));
        keyboard.add(List.of(makeBtn("📢 Тексты уведомлений", Constants.ADMIN_EDIT_NOTIFICATIONS)));
        keyboard.add(List.of(makeBtn("🔙 Назад в меню", Constants.ADMIN_BACK_TO_MENU)));

        markup.setKeyboard(keyboard);
        return markup;
    }
    
    public static InlineKeyboardMarkup getLinksMenu(boolean subscriptionEnabled, boolean reviewsEnabled) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Переключатель обязательной подписки
        String subscriptionToggle = subscriptionEnabled ? "✅ Подписка: ВКЛ" : "❌ Подписка: ВЫКЛ";
        keyboard.add(List.of(makeBtn(subscriptionToggle, Constants.ADMIN_TOGGLE_SUBSCRIPTION)));
        
        // Редактирование ссылки на канал подписки (только если включено)
        if (subscriptionEnabled) {
            keyboard.add(List.of(makeBtn("📢 Изменить канал подписки", Constants.ADMIN_EDIT_SUBSCRIPTION_CHANNEL)));
        }
        
        // Переключатель отзывов
        String reviewsToggle = reviewsEnabled ? "✅ Отзывы: ВКЛ" : "❌ Отзывы: ВЫКЛ";
        keyboard.add(List.of(makeBtn(reviewsToggle, Constants.ADMIN_TOGGLE_REVIEWS)));
        
        // Редактирование ссылки на канал отзывов (только если включено)
        if (reviewsEnabled) {
            keyboard.add(List.of(makeBtn("⭐️ Изменить канал отзывов", Constants.ADMIN_EDIT_REVIEWS_CHANNEL)));
        }
        
        keyboard.add(List.of(makeBtn("🔙 Назад", Constants.ADMIN_SETTINGS)));

        markup.setKeyboard(keyboard);
        return markup;
    }
    
    // ===================== НАСТРОЙКА ПРЕДОПЛАТЫ =====================
    
    public static InlineKeyboardMarkup getPrepaymentMenu(int currentPercent) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        keyboard.add(List.of(
            makeBtn("10%", Constants.ADMIN_SET_PREPAYMENT_PREFIX + "10"),
            makeBtn("20%", Constants.ADMIN_SET_PREPAYMENT_PREFIX + "20")
        ));
        keyboard.add(List.of(
            makeBtn("30%", Constants.ADMIN_SET_PREPAYMENT_PREFIX + "30"),
            makeBtn("40%", Constants.ADMIN_SET_PREPAYMENT_PREFIX + "40")
        ));
        keyboard.add(List.of(
            makeBtn("50%", Constants.ADMIN_SET_PREPAYMENT_PREFIX + "50")
        ));
        keyboard.add(List.of(makeBtn("🔙 Назад", Constants.ADMIN_SETTINGS)));
        
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    // ===================== НАСТРОЙКА УВЕДОМЛЕНИЙ =====================
    
    public static InlineKeyboardMarkup getNotificationsMenu() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        keyboard.add(List.of(makeBtn("⏰ Уведомление за 24 часа", Constants.ADMIN_EDIT_REMINDER_24H)));
        keyboard.add(List.of(makeBtn("⏰ Уведомление за 3 часа", Constants.ADMIN_EDIT_REMINDER_3H)));
        keyboard.add(List.of(makeBtn("👨‍💼 Уведомление админу", Constants.ADMIN_EDIT_ADMIN_REMINDER)));
        keyboard.add(List.of(makeBtn("🔙 Назад", Constants.ADMIN_SETTINGS)));
        
        markup.setKeyboard(keyboard);
        return markup;
    }
}


