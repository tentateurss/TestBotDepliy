package lashes.bot.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.models.Appointment;
import lashes.bot.models.UserProfile;
import lashes.bot.models.WorkDay;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import lashes.bot.utils.ReminderScheduler;
import lashes.bot.utils.ResponseFormatter;
import lashes.bot.utils.SubscriptionChecker;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BookingHandler {
    private static final Logger log = LoggerFactory.getLogger(BookingHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(Constants.DATE_PATTERN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(Constants.TIME_PATTERN);

    // ============ НАЧАЛО ЗАПИСИ ============

    public void handleBook(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        log.info("=== НАЧАЛО ЗАПИСИ ===");
        log.info("User ID: {}", userId);

        // Проверка подписки (только если включена обязательная подписка)
        boolean subscriptionRequired = DatabaseService.getSetting("subscription_required")
            .map(Boolean::parseBoolean).orElse(false);
        
        if (subscriptionRequired) {
            boolean subscribed = SubscriptionChecker.isSubscribed(bot, userId);
            DatabaseService.setUserSubscribed(userId, subscribed);

            if (!subscribed) {
                log.info("User {} not subscribed", userId);
                BotStateManager.setState(chatId, UserState.AWAITING_SUBSCRIPTION);
                bot.sendMessage(chatId, "⚠️ *Для записи необходимо подписаться на наш канал!*",
                        "Markdown", InlineKeyboardFactory.getSubscriptionCheckKeyboard());
                return;
            }
        }

        // Проверка существующей активной записи
        Optional<Appointment> existing = DatabaseService.getActiveAppointmentByUser(userId);
        if (existing.isPresent()) {
            Appointment appt = existing.get();
            String dateStr = appt.getAppointmentTime().format(DATE_FORMATTER);
            String timeStr = appt.getAppointmentTime().format(TIME_FORMATTER);

            bot.sendMessage(chatId, String.format(
                    "⚠️ У вас уже есть активная запись:\n📅 %s в %s\n\nСначала отмените её через кнопку \"❌ Отменить запись\"",
                    dateStr, timeStr));
            return;
        }

        // Начинаем процесс записи
        BotStateManager.setState(chatId, UserState.AWAITING_DATE);
        BotStateManager.clearData(chatId);

        log.info("State set to AWAITING_DATE for user {}", userId);

        bot.sendMessage(chatId, "📅 *Выберите дату:*", "Markdown",
                InlineKeyboardFactory.getCalendar(YearMonth.now(), null));
    }

    // ============ ОБРАБОТКА КАЛЕНДАРЯ ============

    public void handleCalendarCallback(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        String data = callback.getData();

        log.info("=== КАЛЕНДАРЬ ===");
        log.info("Callback data: {}", data);

        YearMonth yearMonth;
        if (data.startsWith("calendar_prev_")) {
            yearMonth = YearMonth.parse(data.substring(14));
        } else if (data.startsWith("calendar_next_")) {
            yearMonth = YearMonth.parse(data.substring(14));
        } else {
            yearMonth = YearMonth.now();
        }

        bot.editMessageReplyMarkup(
                InlineKeyboardFactory.getCalendar(yearMonth, BotStateManager.getSelectedDate(chatId)),
                chatId,
                callback.getMessage().getMessageId()
        );
        bot.answerCallback(callback.getId());
    }

    // ============ ОБРАБОТКА JSON CALLBACK (ВЫБОР ДАТЫ/ВРЕМЕНИ) ============

    public void handleJsonCallback(CallbackQuery callback, LashesBot bot, String jsonData) {
        Long chatId = callback.getMessage().getChatId();
        UserState state = BotStateManager.getState(chatId);

        log.info("=== JSON CALLBACK ===");
        log.info("JSON data: {}", jsonData);
        log.info("Current state: {}", state);

        try {
            Map<String, String> data = mapper.readValue(jsonData, new TypeReference<Map<String, String>>() {});
            String action = data.get("action");

            log.info("Action: {}", action);

            // ============ ВЫБОР ДАТЫ ============
            if ("select_date".equals(action)) {
                log.info("Processing select_date");

                if (state != UserState.AWAITING_DATE) {
                    log.warn("Wrong state for date selection: {}", state);
                    bot.answerCallback(callback.getId(), "Пожалуйста, начните запись заново с кнопки 📝 Записаться", true);
                    return;
                }

                LocalDate selectedDate = LocalDate.parse(data.get("date"));
                log.info("Selected date: {}", selectedDate);

                if (selectedDate.isBefore(LocalDate.now())) {
                    bot.answerCallback(callback.getId(), "❌ Нельзя записаться на прошедшую дату", true);
                    return;
                }

                Optional<WorkDay> workDayOpt = DatabaseService.getWorkDay(selectedDate);
                log.info("WorkDay present: {}", workDayOpt.isPresent());

                if (workDayOpt.isEmpty()) {
                    bot.answerCallback(callback.getId(), "❌ Этот день не добавлен в расписание. Обратитесь к администратору.", true);
                    return;
                }

                if (!workDayOpt.get().isWorking()) {
                    bot.answerCallback(callback.getId(), "❌ Этот день выходной", true);
                    return;
                }

                List<String> availableSlots = DatabaseService.getAvailableSlots(selectedDate);
                log.info("Available slots: {}", availableSlots);

                if (availableSlots.isEmpty()) {
                    bot.answerCallback(callback.getId(), "❌ На эту дату нет свободных слотов", true);
                    return;
                }

                BotStateManager.setSelectedDate(chatId, selectedDate);
                BotStateManager.setState(chatId, UserState.AWAITING_TIME);
                log.info("State changed to AWAITING_TIME");

                bot.editMessageText("⏰ *Выберите удобное время:*", chatId, callback.getMessage().getMessageId(), "Markdown");
                bot.editMessageReplyMarkup(
                        InlineKeyboardFactory.getTimeSlots(selectedDate, availableSlots),
                        chatId,
                        callback.getMessage().getMessageId()
                );
                bot.answerCallback(callback.getId());
            }

            // ============ ВЫБОР ВРЕМЕНИ ============
            else if ("select_time".equals(action)) {
                log.info("Processing select_time");

                if (state != UserState.AWAITING_TIME) {
                    log.warn("Wrong state for time selection: {}", state);
                    bot.answerCallback(callback.getId(), "Пожалуйста, начните запись заново", true);
                    return;
                }

                LocalDate date = LocalDate.parse(data.get("date"));
                String time = data.get("time");
                LocalDateTime appointmentTime = LocalDateTime.of(date, LocalTime.parse(time));

                List<String> availableSlots = DatabaseService.getAvailableSlots(date);
                if (!availableSlots.contains(time)) {
                    log.info("Slot {} is no longer available", time);
                    bot.answerCallback(callback.getId(), "❌ Это время уже занято. Выберите другое.", true);
                    return;
                }

                BotStateManager.setAppointmentTime(chatId, appointmentTime);
                BotStateManager.setState(chatId, UserState.AWAITING_SERVICE_SELECTION);
                log.info("State changed to AWAITING_SERVICE_SELECTION");

                bot.editMessageText("💎 *Выберите процедуру:*", chatId, callback.getMessage().getMessageId(), "Markdown");
                bot.editMessageReplyMarkup(
                        InlineKeyboardFactory.getServiceSelectionKeyboard(),
                        chatId,
                        callback.getMessage().getMessageId()
                );
                bot.answerCallback(callback.getId());
            }

            // ============ НАЗАД К КАЛЕНДАРЮ ============
            else if (Constants.BACK_TO_CALENDAR.equals(action)) {
                BotStateManager.setState(chatId, UserState.AWAITING_DATE);
                log.info("State changed back to AWAITING_DATE");

                bot.editMessageText("📅 *Выберите дату:*", chatId, callback.getMessage().getMessageId(), "Markdown");
                bot.editMessageReplyMarkup(
                        InlineKeyboardFactory.getCalendar(YearMonth.now(), null),
                        chatId,
                        callback.getMessage().getMessageId()
                );
                bot.answerCallback(callback.getId());
            }

            else {
                log.warn("Unknown action: {}", action);
                bot.answerCallback(callback.getId(), "Неизвестное действие", true);
            }

        } catch (Exception e) {
            log.error("Failed to parse callback data", e);
            bot.answerCallback(callback.getId(), "❌ Произошла ошибка. Попробуйте снова /start", true);
        }
    }

    // ============ ПОДТВЕРЖДЕНИЕ ЗАПИСИ ============

    public void handleConfirm(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();

        log.info("=== ПОДТВЕРЖДЕНИЕ ЗАПИСИ ===");
        log.info("User: {}", userId);

        UserState state = BotStateManager.getState(chatId);
        if (state != UserState.AWAITING_CONFIRMATION) {
            log.warn("Wrong state for confirmation: {}", state);
            bot.answerCallback(callback.getId(), "Пожалуйста, начните запись заново", true);
            return;
        }

        LocalDateTime appointmentTime = BotStateManager.getAppointmentTime(chatId);
        String name = BotStateManager.getUserName(chatId);
        String phone = BotStateManager.getPhone(chatId);

        if (appointmentTime == null || name == null || phone == null) {
            log.error("Missing data for confirmation");
            bot.editMessageText("❌ Ошибка: данные потеряны. Пожалуйста, начните запись заново.",
                    chatId, callback.getMessage().getMessageId());
            BotStateManager.clear(chatId);
            return;
        }

        List<String> availableSlots = DatabaseService.getAvailableSlots(appointmentTime.toLocalDate());
        String timeStr = appointmentTime.toLocalTime().toString().substring(0, 5);

        if (!availableSlots.contains(timeStr)) {
            log.info("Slot {} is no longer available", timeStr);
            bot.editMessageText("❌ К сожалению, это время только что заняли. Пожалуйста, выберите другое время.",
                    chatId, callback.getMessage().getMessageId());
            BotStateManager.setState(chatId, UserState.AWAITING_DATE);
            bot.sendMessage(chatId, "📅 *Выберите другую дату:*", "Markdown",
                    InlineKeyboardFactory.getCalendar(YearMonth.now(), null));
            return;
        }

        Appointment appt = new Appointment(userId, name, phone, appointmentTime);
        Long apptId = DatabaseService.saveAppointment(appt);
        appt.setId(apptId);

        log.info("Appointment saved with ID: {}", apptId);

        // Планируем напоминание за 24 часа
        try {
            String jobKey = ReminderScheduler.scheduleReminder(appt);
            if (jobKey != null) {
                DatabaseService.updateReminderJobKey(apptId, jobKey);
                log.info("24h reminder scheduled: {}", jobKey);
            }
        } catch (SchedulerException e) {
            log.error("Failed to schedule 24h reminder", e);
        }

        // Планируем напоминание за 3 часа
        try {
            String jobKey3h = ReminderScheduler.schedule3hReminder(appt);
            if (jobKey3h != null) {
                DatabaseService.updateReminder3hJobKey(apptId, jobKey3h);
                log.info("3h reminder scheduled: {}", jobKey3h);
            }
        } catch (SchedulerException e) {
            log.error("Failed to schedule 3h reminder", e);
        }

        // Планируем запрос отзыва после процедуры
        try {
            ReminderScheduler.scheduleReviewRequest(appt);
            log.info("Review request scheduled");
        } catch (SchedulerException e) {
            log.error("Failed to schedule review request", e);
        }

            // Планируем автоудаление записи через 2 часа после начала
            try {
                ReminderScheduler.scheduleAppointmentCleanup(appt);
                log.info("Appointment cleanup scheduled");
            } catch (SchedulerException e) {
                log.error("Failed to schedule appointment cleanup", e);
            }

            // Генерируем уникальный код записи
            String bookingCode = DatabaseService.generateUniqueBookingCode();
            DatabaseService.updateAppointmentBookingCode(apptId, bookingCode);
            DatabaseService.saveBookingCode(userId, bookingCode, apptId);
            log.info("Booking code generated: {}", bookingCode);

            // Обновляем профиль пользователя
            Optional<UserProfile> profileOpt = DatabaseService.getUserProfile(userId);
            UserProfile profile;
            if (profileOpt.isPresent()) {
                profile = profileOpt.get();
                profile.setName(name);
                profile.setPhone(phone);
                profile.addBookingCode(bookingCode);
            } else {
                profile = new UserProfile(userId, name, phone);
                profile.addBookingCode(bookingCode);
            }
            DatabaseService.saveOrUpdateProfile(profile);

            BotStateManager.clear(chatId);
            log.info("State cleared");

            String successText = ResponseFormatter.formatBookingSuccess(
                    appointmentTime.format(DATE_FORMATTER),
                    appointmentTime.format(TIME_FORMATTER),
                    name, phone
            ) + "\n\n🎫 *Ваш код записи:* `" + bookingCode + "`";

            bot.editMessageText(successText, chatId, callback.getMessage().getMessageId(), "Markdown");

        // Получаем @username клиента (может быть null)
        String telegramUsername = null;
        if (callback.getFrom().getUserName() != null) {
            telegramUsername = callback.getFrom().getUserName();
        }

        String adminText = ResponseFormatter.formatAdminNotification(
                name, phone,
                appointmentTime.format(DATE_FORMATTER),
                appointmentTime.format(TIME_FORMATTER),
                userId, telegramUsername
        );
        bot.sendMessage(BotConfig.getAdminId(), adminText, "Markdown");

        if (BotConfig.getChannelId() != null) {
            String channelText = String.format("📋 *Новая запись*\n\n📅 %s в %s\n👤 %s",
                    appointmentTime.format(DATE_FORMATTER),
                    appointmentTime.format(TIME_FORMATTER),
                    name);

            SendMessage channelMsg = new SendMessage();
            channelMsg.setChatId(BotConfig.getChannelId().toString());
            channelMsg.setText(channelText);
            channelMsg.setParseMode("Markdown");

            try {
                bot.execute(channelMsg);
                log.info("Notification sent to channel");
            } catch (Exception e) {
                log.error("Failed to send to channel", e);
            }
        }

        bot.answerCallback(callback.getId(), "✅ Запись подтверждена!", false);
    }

    // ============ ВЫБОР ПРОЦЕДУРЫ ============

    public void handleServiceSelection(CallbackQuery callback, LashesBot bot, String data) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();
        
        log.info("=== ВЫБОР ПРОЦЕДУРЫ ===");
        log.info("Service data: {}", data);

        UserState state = BotStateManager.getState(chatId);
        if (state != UserState.AWAITING_SERVICE_SELECTION) {
            log.warn("Wrong state for service selection: {}", state);
            bot.answerCallback(callback.getId(), "Пожалуйста, начните запись заново", true);
            return;
        }

        // Извлекаем ID услуги из callback data: "service_1", "service_2" и т.д.
        String serviceIdStr = data.substring("service_".length());
        Long serviceId;
        try {
            serviceId = Long.parseLong(serviceIdStr);
        } catch (NumberFormatException e) {
            log.error("Invalid service ID: {}", serviceIdStr);
            bot.answerCallback(callback.getId(), "Ошибка: неверный ID услуги", true);
            return;
        }

        // Получаем услугу из БД
        Optional<lashes.bot.models.Service> serviceOpt = lashes.bot.database.DatabaseService.getServiceById(serviceId);
        if (serviceOpt.isEmpty()) {
            log.error("Service not found: {}", serviceId);
            bot.answerCallback(callback.getId(), "Ошибка: услуга не найдена", true);
            return;
        }

        lashes.bot.models.Service service = serviceOpt.get();
        String serviceName = service.getName();
        double price = service.getPrice();

        BotStateManager.setServiceName(chatId, serviceName);
        BotStateManager.setServicePrice(chatId, price);
        
        log.info("Service selected: {} - {}₽", serviceName, price);

        // Проверяем профиль пользователя
        Optional<UserProfile> profileOpt = DatabaseService.getUserProfile(userId);
        
        if (profileOpt.isEmpty() || profileOpt.get().getName() == null || profileOpt.get().getPhone() == null) {
            // Профиля нет - запрашиваем данные для создания
            log.info("Profile not found, requesting user data");
            BotStateManager.setState(chatId, UserState.AWAITING_NAME);
            bot.editMessageText(
                "📋 *Создание профиля*\n\n" +
                "Для первой записи нам нужна информация о вас.\n\n" +
                "👤 Введите ваше имя:",
                chatId, 
                callback.getMessage().getMessageId(),
                "Markdown");
            bot.answerCallback(callback.getId());
            return;
        }

        UserProfile profile = profileOpt.get();
        String name = profile.getName();
        String phone = profile.getPhone();

        BotStateManager.setUserName(chatId, name);
        BotStateManager.setPhone(chatId, phone);

        LocalDateTime appointmentTime = BotStateManager.getAppointmentTime(chatId);
        if (appointmentTime == null) {
            log.error("Missing appointment time");
            bot.editMessageText("❌ Ошибка: данные потеряны. Пожалуйста, начните запись заново.", 
                chatId, callback.getMessage().getMessageId());
            BotStateManager.clear(chatId);
            bot.answerCallback(callback.getId());
            return;
        }

        double prepaymentAmount = price * 0.5; // 50% предоплата

        // Получаем реквизиты для оплаты из настроек или используем дефолтные
        String paymentDetails = DatabaseService.getSetting("payment_details").orElse(
            "💳 *Реквизиты для предоплаты:*\n\n" +
            "🏦 Банк: Альфа-банк\n" +
            "📱 Номер телефона: +7 (XXX) XXX-XX-XX\n" +
            "💳 Номер карты: XXXX XXXX XXXX XXXX"
        );

        String prepaymentText = String.format(
            "💰 *Предоплата*\n\n" +
            "Для подтверждения записи необходима предоплата 50%%\n\n" +
            "💎 Процедура: %s\n" +
            "💵 Стоимость: %.0f₽\n" +
            "💳 Предоплата: %.0f₽\n\n" +
            "%s\n\n" +
            "📸 После оплаты отправьте скриншот в этот чат.\n" +
            "✅ Администратор подтвердит предоплату, и ваша запись будет зафиксирована.",
            serviceName, price, prepaymentAmount, paymentDetails
        );

        BotStateManager.setState(chatId, UserState.AWAITING_PREPAYMENT_SCREENSHOT);
        log.info("State changed to AWAITING_PREPAYMENT_SCREENSHOT");

        bot.editMessageText(prepaymentText, chatId, callback.getMessage().getMessageId(), "Markdown");
        bot.answerCallback(callback.getId());
    }

    // ============ ОБРАБОТКА ПРЕДОПЛАТЫ ============

    public void handlePrepaymentScreenshot(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        log.info("=== ПОЛУЧЕН СКРИНШОТ ПРЕДОПЛАТЫ ===");

        UserState state = BotStateManager.getState(chatId);
        if (state != UserState.AWAITING_PREPAYMENT_SCREENSHOT) {
            bot.sendMessage(chatId, "Пожалуйста, начните запись заново с кнопки 📝 Записаться");
            return;
        }

        if (!msg.hasPhoto()) {
            bot.sendMessage(chatId, "❌ Пожалуйста, отправьте скриншот предоплаты (фото)");
            return;
        }

        String photoId = msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();
        
        // Получаем данные из состояния
        LocalDateTime appointmentTime = BotStateManager.getAppointmentTime(chatId);
        String name = BotStateManager.getUserName(chatId);
        String phone = BotStateManager.getPhone(chatId);
        String serviceName = BotStateManager.getServiceName(chatId);
        Double price = BotStateManager.getServicePrice(chatId);

        if (appointmentTime == null || name == null || phone == null || serviceName == null || price == null) {
            bot.sendMessage(chatId, "❌ Ошибка: данные записи потеряны. Начните заново.");
            BotStateManager.clear(chatId);
            return;
        }

        // Вычисляем предоплату (используем настраиваемый процент)
        int prepaymentPercent = DatabaseService.getSetting("prepayment_percent")
            .map(Integer::parseInt).orElse(50);
        double prepaymentAmount = price * prepaymentPercent / 100.0;

        // Создаём запись в БД (пока не подтверждена)
        Appointment appt = new Appointment(userId, name, phone, appointmentTime);
        appt.setServiceName(serviceName);
        appt.setPrice(price);
        appt.setPrepaymentAmount(prepaymentAmount);
        appt.setPrepaymentScreenshotId(photoId);
        appt.setPrepaymentConfirmed(false);
        
        Long apptId = DatabaseService.saveAppointment(appt);
        appt.setId(apptId);

        log.info("Appointment created with ID: {} (pending prepayment confirmation)", apptId);

        // Обновляем информацию о процедуре и предоплате
        DatabaseService.updateAppointmentService(apptId, serviceName, price, prepaymentAmount);
        DatabaseService.updatePrepaymentScreenshot(apptId, photoId);

        bot.sendMessage(chatId, "✅ Скриншот получен!\n\nОжидайте подтверждения от администратора. Это может занять несколько минут.");

        // Отправляем скриншот админам для подтверждения
        String adminMessage = String.format(
            "💰 *НОВАЯ ПРЕДОПЛАТА*\n\n" +
            "👤 Клиент: %s\n" +
            "📱 Телефон: %s\n" +
            "💎 Процедура: %s\n" +
            "💵 Стоимость: %.0f₽\n" +
            "💳 Предоплата: %.0f₽\n" +
            "📅 Дата: %s\n" +
            "⏰ Время: %s\n\n" +
            "Подтвердите получение предоплаты:",
            name,
            phone,
            serviceName,
            price,
            prepaymentAmount,
            appointmentTime.format(DATE_FORMATTER),
            appointmentTime.format(TIME_FORMATTER)
        );

        for (Long adminId : BotConfig.getAdminIds()) {
            bot.sendPhoto(adminId, photoId, adminMessage);
            bot.sendMessage(adminId, "Подтвердите предоплату:", null,
                InlineKeyboardFactory.getPrepaymentConfirmationKeyboard(apptId));
        }

        BotStateManager.clear(chatId);
    }

    public void handlePrepaymentConfirmation(CallbackQuery callback, LashesBot bot, String data) {
        Long chatId = callback.getMessage().getChatId();
        
        log.info("=== ПОДТВЕРЖДЕНИЕ ПРЕДОПЛАТЫ АДМИНОМ ===");

        boolean isConfirm = data.startsWith(Constants.CONFIRM_PREPAYMENT_PREFIX);
        String idStr = data.substring(isConfirm ? Constants.CONFIRM_PREPAYMENT_PREFIX.length() : Constants.REJECT_PREPAYMENT_PREFIX.length());
        Long appointmentId = Long.parseLong(idStr);

        Optional<Appointment> apptOpt = DatabaseService.getAppointmentById(appointmentId);
        if (apptOpt.isEmpty()) {
            bot.answerCallback(callback.getId(), "Запись не найдена", true);
            return;
        }

        Appointment appt = apptOpt.get();

        if (isConfirm) {
            // Подтверждаем предоплату
            DatabaseService.confirmPrepayment(appointmentId);
            
            // Планируем напоминания за 24 часа (только если ещё не запланировано)
            if (appt.getReminderJobKey() == null || appt.getReminderJobKey().isEmpty()) {
                try {
                    String jobKey = ReminderScheduler.scheduleReminder(appt);
                    if (jobKey != null) {
                        DatabaseService.updateReminderJobKey(appointmentId, jobKey);
                        log.info("24h reminder scheduled: {}", jobKey);
                    }
                } catch (Exception e) {
                    log.error("Failed to schedule 24h reminder", e);
                }
            } else {
                log.info("24h reminder already scheduled, skipping");
            }

            // Планируем напоминание за 3 часа (только если ещё не запланировано)
            if (appt.getReminder3hJobKey() == null || appt.getReminder3hJobKey().isEmpty()) {
                try {
                    String jobKey3h = ReminderScheduler.schedule3hReminder(appt);
                    if (jobKey3h != null) {
                        DatabaseService.updateReminder3hJobKey(appointmentId, jobKey3h);
                        log.info("3h reminder scheduled: {}", jobKey3h);
                    }
                } catch (Exception e) {
                    log.error("Failed to schedule 3h reminder", e);
                }
            } else {
                log.info("3h reminder already scheduled, skipping");
            }

            // Планируем напоминание админу за 2 часа
            try {
                ReminderScheduler.scheduleAdminReminder(appt);
                log.info("Admin 2h reminder scheduled");
            } catch (Exception e) {
                log.error("Failed to schedule admin reminder", e);
            }

            // Планируем запрос отзыва после процедуры
            try {
                ReminderScheduler.scheduleReviewRequest(appt);
                log.info("Review request scheduled");
            } catch (Exception e) {
                log.error("Failed to schedule review request", e);
            }

            // Планируем автоудаление записи через 2 часа после начала
            try {
                ReminderScheduler.scheduleAppointmentCleanup(appt);
                log.info("Appointment cleanup scheduled");
            } catch (SchedulerException e) {
                log.error("Failed to schedule appointment cleanup", e);
            }
            
            // Генерируем уникальный код записи
            String bookingCode = DatabaseService.generateUniqueBookingCode();
            DatabaseService.updateAppointmentBookingCode(appointmentId, bookingCode);
            DatabaseService.saveBookingCode(appt.getUserId(), bookingCode, appointmentId);
            log.info("Booking code generated: {}", bookingCode);

            // Обновляем профиль пользователя
            Optional<UserProfile> profileOpt = DatabaseService.getUserProfile(appt.getUserId());
            UserProfile profile;
            if (profileOpt.isPresent()) {
                profile = profileOpt.get();
                profile.setName(appt.getUserName());
                profile.setPhone(appt.getPhone());
                profile.addBookingCode(bookingCode);
            } else {
                profile = new UserProfile(appt.getUserId(), appt.getUserName(), appt.getPhone());
                profile.addBookingCode(bookingCode);
            }
            DatabaseService.saveOrUpdateProfile(profile);
            
            bot.editMessageText(
                "✅ *ПРЕДОПЛАТА ПОДТВЕРЖДЕНА*\n\n" +
                "Запись зафиксирована в системе.",
                chatId,
                callback.getMessage().getMessageId(),
                "Markdown"
            );

            // Уведомляем клиента
            String clientMessage = String.format(
                "✅ *Предоплата подтверждена!*\n\n" +
                "Ваша запись подтверждена:\n" +
                "💎 %s\n" +
                "📅 %s в %s\n\n" +
                "🎫 *Ваш код записи:* `%s`\n\n" +
                "Ждём вас! 🌸",
                appt.getServiceName(),
                appt.getAppointmentTime().format(DATE_FORMATTER),
                appt.getAppointmentTime().format(TIME_FORMATTER),
                bookingCode
            );
            bot.sendMessage(appt.getUserId(), clientMessage, "Markdown");

            // Обновляем главное меню с кнопкой отмены
            boolean isAdmin = BotConfig.isAdmin(appt.getUserId());
            bot.sendMessage(appt.getUserId(), "Выберите действие:", null,
                    lashes.bot.keyboards.ReplyKeyboardFactory.getMainMenu(isAdmin, appt.getUserId()));

            // Уведомление в канал (если настроен)
            if (BotConfig.getChannelId() != null) {
                String channelText = String.format("📋 *Новая запись*\n\n📅 %s в %s\n👤 %s\n💎 %s",
                        appt.getAppointmentTime().format(DATE_FORMATTER),
                        appt.getAppointmentTime().format(TIME_FORMATTER),
                        appt.getUserName(),
                        appt.getServiceName());

                try {
                    bot.sendMessage(BotConfig.getChannelId(), channelText, "Markdown");
                    log.info("Notification sent to channel");
                } catch (Exception e) {
                    log.warn("Failed to send notification to channel (not critical, booking saved): {}", e.getMessage());
                }
            }

            bot.answerCallback(callback.getId(), "✅ Предоплата подтверждена", false);
        } else {
            // Отклоняем предоплату и удаляем запись
            DatabaseService.cancelAppointment(appointmentId);
            
            bot.editMessageText(
                "❌ *ПРЕДОПЛАТА ОТКЛОНЕНА*\n\n" +
                "Запись удалена из системы.",
                chatId,
                callback.getMessage().getMessageId(),
                "Markdown"
            );

            // Уведомляем клиента
            String clientMessage = "❌ К сожалению, предоплата не подтверждена.\n\n" +
                "Пожалуйста, свяжитесь с администратором или попробуйте записаться снова.";
            bot.sendMessage(appt.getUserId(), clientMessage);

            bot.answerCallback(callback.getId(), "❌ Предоплата отклонена", false);
        }
    }

    // ============ ВВОД ДАННЫХ ДЛЯ СОЗДАНИЯ ПРОФИЛЯ ПРИ ПЕРВОЙ ЗАПИСИ ============

    public void handleName(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String name = msg.getText().trim();

        log.info("=== ВВОД ИМЕНИ ДЛЯ ПРОФИЛЯ ===");
        log.info("Name: {}", name);

        if (name.length() < 2) {
            bot.sendMessage(chatId, "❌ Имя должно содержать минимум 2 символа. Попробуйте ещё раз:");
            return;
        }

        BotStateManager.setUserName(chatId, name);
        BotStateManager.setState(chatId, UserState.AWAITING_PHONE);
        
        bot.sendMessage(chatId, 
            "📱 *Введите номер телефона:*\n\n" +
            "В формате: +7XXXXXXXXXX или 8XXXXXXXXXX",
            "Markdown");
    }

    public void handlePhone(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String phone = msg.getText().trim();

        log.info("=== ВВОД ТЕЛЕФОНА ДЛЯ ПРОФИЛЯ ===");
        log.info("Phone: {}", phone);

        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.length() < 10 || cleaned.length() > 12) {
            bot.sendMessage(chatId, 
                "❌ Некорректный номер. Попробуйте ещё раз:\n" +
                "Формат: +7XXXXXXXXXX или 8XXXXXXXXXX");
            return;
        }

        BotStateManager.setPhone(chatId, phone);
        BotStateManager.setState(chatId, UserState.PROFILE_EDIT_ADDITIONAL);
        
        bot.sendMessage(chatId, 
            "📝 *Комментарий (необязательно):*\n\n" +
            "Например: аллергии, предпочтения и т.д.\n\n" +
            "Или отправьте \"-\" чтобы пропустить.",
            "Markdown");
    }

    public void handleAdditionalInfo(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String additionalInfo = msg.getText().trim();

        log.info("=== ВВОД КОММЕНТАРИЯ ДЛЯ ПРОФИЛЯ ===");

        if (additionalInfo.equals("-")) {
            additionalInfo = null;
        }

        String name = BotStateManager.getUserName(chatId);
        String phone = BotStateManager.getPhone(chatId);
        String serviceName = BotStateManager.getServiceName(chatId);
        Double servicePrice = BotStateManager.getServicePrice(chatId);
        LocalDateTime appointmentTime = BotStateManager.getAppointmentTime(chatId);

        if (name == null || phone == null || appointmentTime == null) {
            bot.sendMessage(chatId, "❌ Ошибка: данные потеряны. Начните заново с /start");
            BotStateManager.clear(chatId);
            return;
        }

        // Создаём профиль
        UserProfile profile = new UserProfile(userId, name, phone);
        profile.setAdditionalInfo(additionalInfo);
        DatabaseService.saveOrUpdateProfile(profile);
        
        log.info("Profile created for user {}", userId);

        // Продолжаем процесс записи с предоплатой
        double prepaymentAmount = servicePrice * 0.5;

        String paymentDetails = DatabaseService.getSetting("payment_details").orElse(
            "💳 *Реквизиты для предоплаты:*\n\n" +
            "🏦 Банк: Альфа-банк\n" +
            "📱 Номер телефона: +7 (XXX) XXX-XX-XX\n" +
            "💳 Номер карты: XXXX XXXX XXXX XXXX"
        );

        String confirmText = String.format(
            "✅ *Профиль создан!*\n\n" +
            "📋 *Подтверждение записи:*\n\n" +
            "👤 Имя: %s\n" +
            "📱 Телефон: %s\n" +
            "📅 Дата: %s\n" +
            "⏰ Время: %s\n" +
            "💎 Процедура: %s\n" +
            "💰 Стоимость: %.0f₽\n\n" +
            "💳 *Предоплата: %.0f₽ (50%%)*\n\n" +
            "%s\n\n" +
            "📸 Отправьте скриншот оплаты для подтверждения записи.",
            name, phone,
            appointmentTime.format(DATE_FORMATTER),
            appointmentTime.format(TIME_FORMATTER),
            serviceName,
            servicePrice,
            prepaymentAmount,
            paymentDetails
        );

        BotStateManager.setState(chatId, UserState.AWAITING_PREPAYMENT_SCREENSHOT);
        bot.sendMessage(chatId, confirmText, "Markdown");
        
        log.info("Waiting for prepayment screenshot");
    }
}