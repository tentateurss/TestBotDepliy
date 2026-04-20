package lashes.bot.handlers;

import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.keyboards.ReplyKeyboardFactory;
import lashes.bot.models.Appointment;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class MessageRouter {
    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    private final CommonHandler commonHandler;
    private final BookingHandler bookingHandler;
    private final AdminHandler adminHandler;
    private final InfoHandler infoHandler;
    private final StatisticsHandler statisticsHandler;
    private final ReviewsHandler reviewsHandler;
    private final ProfileHandler profileHandler;
    private final CancelHandler cancelHandler;

    private final Map<UserState, BiConsumer<Message, LashesBot>> stateHandlers;

    public MessageRouter() {
        this.commonHandler = new CommonHandler();
        this.bookingHandler = new BookingHandler();
        this.adminHandler = new AdminHandler();
        this.infoHandler = new InfoHandler();
        this.statisticsHandler = new StatisticsHandler();
        this.reviewsHandler = new ReviewsHandler();
        this.profileHandler = new ProfileHandler();
        this.cancelHandler = new CancelHandler();

        this.stateHandlers = new HashMap<>();
        initStateHandlers();
    }

    private void initStateHandlers() {
        stateHandlers.put(UserState.AWAITING_PREPAYMENT_SCREENSHOT, (msg, bot) -> bookingHandler.handlePrepaymentScreenshot(msg, bot));
        
        // Состояния создания профиля при первой записи
        stateHandlers.put(UserState.AWAITING_NAME, (msg, bot) -> bookingHandler.handleName(msg, bot));
        stateHandlers.put(UserState.AWAITING_PHONE, (msg, bot) -> bookingHandler.handlePhone(msg, bot));
        stateHandlers.put(UserState.PROFILE_EDIT_ADDITIONAL, (msg, bot) -> {
            UserState prevState = BotStateManager.getState(msg.getChatId());
            // Если пришли из процесса записи (AWAITING_PHONE), используем BookingHandler
            if (BotStateManager.getAppointmentTime(msg.getChatId()) != null) {
                bookingHandler.handleAdditionalInfo(msg, bot);
            } else {
                // Иначе это редактирование профиля через ProfileHandler
                profileHandler.handleProfileAdditional(msg, bot);
            }
        });

        // Состояния где AdminHandler ждёт ввода текста
        stateHandlers.put(UserState.ADMIN_ADD_WORK_DAY_SLOTS,
                (msg, bot) -> adminHandler.handleAdminState(msg, bot, UserState.ADMIN_ADD_WORK_DAY_SLOTS));
        stateHandlers.put(UserState.ADMIN_ADD_SLOTS_TIME,
                (msg, bot) -> adminHandler.handleAdminState(msg, bot, UserState.ADMIN_ADD_SLOTS_TIME));
        stateHandlers.put(UserState.ADMIN_REMOVE_SLOT_SELECT_TIME,
                (msg, bot) -> adminHandler.handleAdminState(msg, bot, UserState.ADMIN_REMOVE_SLOT_SELECT_TIME));
        stateHandlers.put(UserState.ADMIN_EDIT_PRICE,
                (msg, bot) -> adminHandler.handleAdminState(msg, bot, UserState.ADMIN_EDIT_PRICE));
        stateHandlers.put(UserState.ADMIN_BROADCAST,
                (msg, bot) -> statisticsHandler.handleBroadcastMessage(msg, bot));
        stateHandlers.put(UserState.AWAITING_REVIEW_COMMENT,
                (msg, bot) -> reviewsHandler.handleReviewComment(msg, bot));
        
        // Настройки бота
        stateHandlers.put(UserState.ADMIN_EDIT_WELCOME_MESSAGE,
                (msg, bot) -> adminHandler.handleAdminState(msg, bot, UserState.ADMIN_EDIT_WELCOME_MESSAGE));
        
        // Состояния профиля
        stateHandlers.put(UserState.PROFILE_EDIT_NAME,
                (msg, bot) -> profileHandler.handleProfileName(msg, bot));
        stateHandlers.put(UserState.PROFILE_EDIT_PHONE,
                (msg, bot) -> profileHandler.handleProfilePhone(msg, bot));
        stateHandlers.put(UserState.PROFILE_EDIT_ADDITIONAL,
                (msg, bot) -> profileHandler.handleProfileAdditional(msg, bot));
    }

    public void route(Update update, LashesBot bot) {
        if (update.hasCallbackQuery()) {
            routeCallback(update, bot);
            return;
        }

        if (update.hasMessage()) {
            Message msg = update.getMessage();

            // Обработка фото (например, для прайса)
            if (msg.hasPhoto()) {
                routePhoto(msg, bot);
                return;
            }

            if (msg.hasText()) {
                routeMessage(msg, bot);
            }
        }
    }

    private void routePhoto(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        UserState state = BotStateManager.getState(chatId);

        if (state == UserState.ADMIN_EDIT_PRICE) {
            adminHandler.handleAdminPhoto(msg, bot);
        } else if (state == UserState.AWAITING_PREPAYMENT_SCREENSHOT) {
            bookingHandler.handlePrepaymentScreenshot(msg, bot);
        } else {
            bot.sendMessage(chatId, "Используйте кнопки меню.");
        }
    }

    private void routeMessage(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        String text = msg.getText();
        Long userId = msg.getFrom().getId();

        log.info("=== ROUTE MESSAGE ===");
        log.info("Text: {}", text);
        log.info("User state: {}", BotStateManager.getState(chatId));

        if (text.equals("/start")) {
            commonHandler.handleStart(msg, bot);
            return;
        }

        if (text.equals("/profile")) {
            profileHandler.handleViewProfile(msg, bot);
            return;
        }

        if (text.equals("/edit_profile")) {
            profileHandler.handleEditProfile(msg, bot);
            return;
        }

        if (text.equals("/admin") || text.equals(Constants.BTN_ADMIN)) {
            if (BotConfig.isAdmin(userId)) {
                adminHandler.handleAdminMenu(msg, bot);
            } else {
                bot.sendMessage(chatId, Constants.MSG_ACCESS_DENIED);
            }
            return;
        }

        UserState state = BotStateManager.getState(chatId);

        if (state == UserState.AWAITING_SUBSCRIPTION) {
            commonHandler.sendSubscriptionCheck(chatId, bot);
            return;
        }

        BiConsumer<Message, LashesBot> handler = stateHandlers.get(state);
        if (handler != null) {
            handler.accept(msg, bot);
        } else {
            routeByMenuText(msg, bot);
        }
    }

    private void routeByMenuText(Message msg, LashesBot bot) {
        String text = msg.getText();

        switch (text) {
            case Constants.BTN_BOOK:
                bookingHandler.handleBook(msg, bot);
                break;
            case Constants.BTN_CANCEL_BOOKING:
                cancelHandler.handleCancel(msg, bot);
                break;
            case Constants.BTN_PRICE:
                infoHandler.handlePrice(msg, bot);
                break;
            case Constants.BTN_PORTFOLIO:
                infoHandler.handlePortfolio(msg, bot);
                break;
            case Constants.BTN_REVIEWS:
                reviewsHandler.handleViewReviews(msg, bot);
                break;
            case Constants.BTN_MY_PROFILE:
                profileHandler.handleViewProfile(msg, bot);
                break;
            default:
                bot.sendMessage(msg.getChatId(), "Используйте кнопки меню или команду /start");
        }
    }

    private void routeCallback(Update update, LashesBot bot) {
        CallbackQuery callback = update.getCallbackQuery();
        String data = callback.getData();
        Long userId = callback.getFrom().getId();
        Long chatId = callback.getMessage().getChatId();

        log.info("=== ROUTE CALLBACK ===");
        log.info("Callback data: {}", data);

        bot.answerCallback(callback.getId());

        // Игнорируем нажатия на служебные кнопки
        if (data.equals("ignore")) {
            return;
        }

        // Пользовательская навигация по календарю
        if (data.startsWith("calendar_prev_") || data.startsWith("calendar_next_")) {
            log.info("Processing user calendar navigation");
            bookingHandler.handleCalendarCallback(callback, bot);
            return;
        }

        // Админская навигация по календарю
        if (data.startsWith(Constants.ADMIN_CALENDAR_PREV) || data.startsWith(Constants.ADMIN_CALENDAR_NEXT)) {
            if (BotConfig.isAdmin(userId)) {
                adminHandler.handleAdminCallback(callback, bot, data);
            }
            return;
        }

        // Подтверждение/отклонение отмены записи админом
        if (data.startsWith(Constants.ADMIN_CONFIRM_CANCEL_APPT_PREFIX)) {
            if (BotConfig.isAdmin(userId)) {
                String idStr = data.substring(Constants.ADMIN_CONFIRM_CANCEL_APPT_PREFIX.length());
                Long appointmentId = Long.parseLong(idStr);
                cancelHandler.handleAdminConfirmCancel(callback, bot, appointmentId);
            }
            return;
        }

        if (data.startsWith("admin_reject_cancel_")) {
            if (BotConfig.isAdmin(userId)) {
                String idStr = data.substring("admin_reject_cancel_".length());
                Long appointmentId = Long.parseLong(idStr);
                cancelHandler.handleAdminRejectCancel(callback, bot, appointmentId);
            }
            return;
        }

        // Шаблон расписания (пошаговый выбор)
        if (data.startsWith(Constants.TEMPLATE_WEEKDAY_PREFIX) || 
            data.startsWith(Constants.TEMPLATE_TIME_PREFIX) || 
            data.startsWith(Constants.TEMPLATE_PERIOD_PREFIX) || 
            data.equals(Constants.TEMPLATE_CONTINUE)) {
            if (BotConfig.isAdmin(userId)) {
                adminHandler.handleAdminCallback(callback, bot, data);
            }
            return;
        }
        
        // Просмотр расписания списком
        if (data.equals(Constants.SCHEDULE_VIEW_NEXT_DAY) || 
            data.equals(Constants.SCHEDULE_VIEW_CALENDAR)) {
            if (BotConfig.isAdmin(userId)) {
                adminHandler.handleAdminCallback(callback, bot, data);
            }
            return;
        }
        
        // Все остальные admin_ callbacks
        if (data.startsWith("admin_")) {
            if (BotConfig.isAdmin(userId)) {
                adminHandler.handleAdminCallback(callback, bot, data);
            } else {
                bot.editMessageText(Constants.MSG_ACCESS_DENIED,
                        chatId, callback.getMessage().getMessageId());
            }
            return;
        }

        // Выбор процедуры
        if (data.startsWith("service_")) {
            bookingHandler.handleServiceSelection(callback, bot, data);
            return;
        }

        // Подтверждение/отклонение предоплаты админом
        if (data.startsWith(Constants.CONFIRM_PREPAYMENT_PREFIX) || data.startsWith(Constants.REJECT_PREPAYMENT_PREFIX)) {
            if (BotConfig.isAdmin(userId)) {
                bookingHandler.handlePrepaymentConfirmation(callback, bot, data);
            }
            return;
        }

        // Статистика
        if (data.startsWith(Constants.STATS_PREV_MONTH) || data.startsWith(Constants.STATS_NEXT_MONTH)) {
            if (BotConfig.isAdmin(userId)) {
                statisticsHandler.handleStatisticsNav(callback, bot, data);
            }
            return;
        }

        // Отзывы
        if (data.startsWith(Constants.REVIEW_RATING_PREFIX)) {
            reviewsHandler.handleReviewRequest(callback, bot, data);
            return;
        }

        if (data.equals(Constants.REVIEW_SKIP)) {
            reviewsHandler.handleReviewSkip(callback, bot);
            return;
        }

        // Профиль
        if (data.equals("edit_profile")) {
            profileHandler.handleEditProfileCallback(callback, bot);
            return;
        }

        if (data.equals("delete_profile")) {
            profileHandler.handleDeleteProfileRequest(callback, bot);
            return;
        }

        if (data.equals("confirm_delete_profile")) {
            profileHandler.handleConfirmDeleteProfile(callback, bot);
            return;
        }

        if (data.equals("cancel_delete_profile")) {
            profileHandler.handleCancelDeleteProfile(callback, bot);
            return;
        }

        // Удаление истории записей
        if (data.equals("delete_appointment_history")) {
            profileHandler.handleDeleteAppointmentHistoryRequest(callback, bot);
            return;
        }

        if (data.equals("confirm_delete_history")) {
            profileHandler.handleConfirmDeleteAppointmentHistory(callback, bot);
            return;
        }

        if (data.equals("cancel_delete_history")) {
            profileHandler.handleCancelDeleteAppointmentHistory(callback, bot);
            return;
        }

        // Список ожидания
        if (data.startsWith(Constants.WAITING_LIST_ADD)) {
            handleWaitingListAdd(callback, bot, data);
            return;
        }

        // JSON data (select_date, select_time, back_to_calendar)
        if (data.startsWith("{")) {
            log.info("Processing JSON callback");
            bookingHandler.handleJsonCallback(callback, bot, data);
            return;
        }

        // Проверка подписки
        if (data.equals(Constants.CHECK_SUBSCRIPTION)) {
            commonHandler.handleSubscriptionCheck(callback, bot);
            return;
        }

        // Подтверждение записи
        if (data.equals(Constants.CONFIRM_BOOKING)) {
            bookingHandler.handleConfirm(callback, bot);
            return;
        }

        // Отмена записи клиентом (только подтверждение — само действие через CONFIRM_CANCEL)
        if (data.equals(Constants.CONFIRM_CANCEL)) {
            // Для пользователей кнопки отмены нет в меню, но если уже открыт диалог — обрабатываем
            new CancelHandler().handleConfirmCancel(callback, bot);
            return;
        }

        // Отмена действия (например, отмена подтверждения записи)
        if (data.equals(Constants.CANCEL_BOOKING) || data.equals("cancel_action")) {
            BotStateManager.clear(chatId);
            bot.editMessageText(Constants.MSG_BOOKING_CANCELLED,
                    chatId, callback.getMessage().getMessageId());
            return;
        }

        // Возврат в главное меню
        if (data.equals(Constants.BACK_TO_MENU)) {
            boolean isAdmin = BotConfig.isAdmin(userId);
            BotStateManager.clear(chatId);
            bot.editMessageText("🔙 Возврат в главное меню", chatId, callback.getMessage().getMessageId());
            bot.sendMessage(chatId, "Выберите действие:", null, ReplyKeyboardFactory.getMainMenu(isAdmin, userId));
            return;
        }

        log.warn("Unhandled callback: {}", data);
    }

    private void handleWaitingListAdd(CallbackQuery callback, LashesBot bot, String data) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();
        String userName = callback.getFrom().getFirstName();

        String[] parts = data.substring(Constants.WAITING_LIST_ADD.length()).split(":");
        if (parts.length != 2) return;

        LocalDate date = LocalDate.parse(parts[0]);
        String timeSlot = parts[1];

        // Получаем телефон из последней записи пользователя
        Optional<Appointment> lastAppt = DatabaseService.getActiveAppointmentByUser(userId);
        String phone = "не указан";
        if (lastAppt.isPresent()) {
            phone = lastAppt.get().getPhone();
        }

        DatabaseService.addToWaitingList(userId, userName, phone, date, timeSlot);

        bot.editMessageText(
            "✅ Вы добавлены в список ожидания!\n\n" +
            "Мы уведомим вас, если это время освободится.",
            chatId, callback.getMessage().getMessageId());
    }
}

