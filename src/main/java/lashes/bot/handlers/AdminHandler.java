package lashes.bot.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.constants.Constants;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.models.Appointment;
import lashes.bot.models.WorkDay;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import lashes.bot.utils.ReminderScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdminHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(Constants.DATE_PATTERN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(Constants.TIME_PATTERN);

    // Default price text (used when nothing is saved in database)
    private static final String DEFAULT_PRICE_TEXT =
            "\uD83D\uDCB0 *\u041f\u0420\u0410\u0419\u0421-\u041b\u0418\u0421\u0422*\n\n" +
            "\uD83D\uDC41 *\u041d\u0430\u0440\u0430\u0449\u0438\u0432\u0430\u043d\u0438\u0435 \u0440\u0435\u0441\u043d\u0438\u0446:*\n" +
            "\u2022 \u041a\u043b\u0430\u0441\u0441\u0438\u043a\u0430 \u2014 1500\u20bd\n" +
            "\u2022 2D \u043e\u0431\u044a\u0451\u043c \u2014 1800\u20bd\n" +
            "\u2022 3D \u043e\u0431\u044a\u0451\u043c \u2014 2100\u20bd\n" +
            "\u2022 \u0413\u043e\u043b\u043b\u0438\u0432\u0443\u0434 \u2014 2500\u20bd\n\n" +
            "\uD83D\uDD04 *\u041a\u043e\u0440\u0440\u0435\u043a\u0446\u0438\u044f:*\n" +
            "\u2022 \u041a\u043b\u0430\u0441\u0441\u0438\u043a\u0430 \u2014 1000\u20bd\n" +
            "\u2022 \u041e\u0431\u044a\u0451\u043c \u2014 1300\u20bd\n\n" +
            "\uD83E\uDDFC *\u0421\u043d\u044f\u0442\u0438\u0435 \u0440\u0435\u0441\u043d\u0438\u0446:*\n" +
            "\u2022 \u0421\u043d\u044f\u0442\u0438\u0435 (\u0441\u0432\u043e\u0438) \u2014 400\u20bd\n" +
            "\u2022 \u0421\u043d\u044f\u0442\u0438\u0435 + \u043d\u043e\u0432\u043e\u0435 \u043d\u0430\u0440\u0430\u0449\u0438\u0432\u0430\u043d\u0438\u0435 \u2014 \u0441\u043a\u0438\u0434\u043a\u0430 200\u20bd\n\n" +
            "\u2728 *\u0414\u043e\u043f\u043e\u043b\u043d\u0438\u0442\u0435\u043b\u044c\u043d\u043e:*\n" +
            "\u2022 \u041e\u043a\u0440\u0430\u0448\u0438\u0432\u0430\u043d\u0438\u0435 \u0440\u0435\u0441\u043d\u0438\u0446 \u2014 300\u20bd\n" +
            "\u2022 \u041b\u0430\u043c\u0438\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435 \u0440\u0435\u0441\u043d\u0438\u0446 \u2014 1800\u20bd\n\n" +
            "\uD83D\uDCCD *\u0410\u0434\u0440\u0435\u0441:* \u0443\u043b. \u041f\u0440\u0438\u043c\u0435\u0440\u043d\u0430\u044f, \u0434. 123";

    // ============ Admin menu entry ============

    public void handleAdminMenu(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        if (!BotConfig.isAdmin(userId)) {
            bot.sendMessage(chatId, Constants.MSG_ACCESS_DENIED);
            return;
        }

        BotStateManager.setState(chatId, UserState.ADMIN_MENU);
        bot.sendMessage(chatId, "\u2699\uFE0F *\u0410\u0414\u041c\u0418\u041d-\u041f\u0410\u041d\u0415\u041b\u042c*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435:", "Markdown",
                InlineKeyboardFactory.getAdminMenu());
    }

    // ============ Admin callback handler ============

    public void handleAdminCallback(CallbackQuery callback, LashesBot bot, String data) {
        Long chatId = callback.getMessage().getChatId();
        int msgId = callback.getMessage().getMessageId();
        String callbackId = callback.getId();

        // Calendar month navigation
        if (data.startsWith(Constants.ADMIN_CALENDAR_PREV) || data.startsWith(Constants.ADMIN_CALENDAR_NEXT)) {
            handleAdminCalendarNav(callback, bot, data);
            return;
        }

        // Date selected: Add work day
        if (data.startsWith(Constants.ADMIN_DATE_SELECT)) {
            String dateStr = data.substring(Constants.ADMIN_DATE_SELECT.length());
            LocalDate date = LocalDate.parse(dateStr);
            BotStateManager.setData(chatId, BotStateManager.KEY_ADMIN_DATE, date);
            BotStateManager.setState(chatId, UserState.ADMIN_ADD_WORK_DAY_SLOTS);
            bot.editMessageText(
                    "\uD83D\uDCC5 \u0412\u044b\u0431\u0440\u0430\u043d\u0430 \u0434\u0430\u0442\u0430: *" + date.format(DATE_FORMATTER) + "*\n\n" +
                    "\u23F0 \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u044b\u0435 \u0441\u043b\u043e\u0442\u044b \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043f\u044f\u0442\u0443\u044e (10:00,11:00,12:00)\n" +
                    "\u0418\u043b\u0438 \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 *default* \u0434\u043b\u044f \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u0445 (10:00-18:00)",
                    chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(null, chatId, msgId);
            return;
        }

        // Date selected: Close day
        if (data.startsWith(Constants.ADMIN_CLOSE_DATE_SELECT)) {
            String dateStr = data.substring(Constants.ADMIN_CLOSE_DATE_SELECT.length());
            LocalDate date = LocalDate.parse(dateStr);
            DatabaseService.closeDay(date);
            BotStateManager.clear(chatId);
            bot.editMessageText("\uD83D\uDEAB \u0414\u0435\u043d\u044c *" + date.format(DATE_FORMATTER) + "* \u0437\u0430\u043a\u0440\u044b\u0442 \u0434\u043b\u044f \u0437\u0430\u043f\u0438\u0441\u0438.",
                    chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(null, chatId, msgId);
            showAdminMenuNewMessage(chatId, bot);
            return;
        }

        // Date selected: Slot management
        if (data.startsWith(Constants.ADMIN_SLOT_DATE_SELECT)) {
            String rest = data.substring(Constants.ADMIN_SLOT_DATE_SELECT.length());
            int underscoreIdx = rest.indexOf('_');
            String action = rest.substring(0, underscoreIdx);
            String dateStr = rest.substring(underscoreIdx + 1);
            LocalDate date = LocalDate.parse(dateStr);
            BotStateManager.setData(chatId, BotStateManager.KEY_ADMIN_DATE, date);

            if ("add".equals(action)) {
                BotStateManager.setState(chatId, UserState.ADMIN_ADD_SLOTS_TIME);
                bot.editMessageText(
                        "\uD83D\uDCC5 \u0412\u044b\u0431\u0440\u0430\u043d\u0430 \u0434\u0430\u0442\u0430: *" + date.format(DATE_FORMATTER) + "*\n\n" +
                        "\u23F0 \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0432\u0440\u0435\u043c\u044f \u043d\u043e\u0432\u043e\u0433\u043e \u0441\u043b\u043e\u0442\u0430 \u0432 \u0444\u043e\u0440\u043c\u0430\u0442\u0435 \u0427\u0427:\u041c\u041c (\u043d\u0430\u043f\u0440\u0438\u043c\u0435\u0440: *14:30*)",
                        chatId, msgId, "Markdown");
            } else {
                handleShowSlotsForRemoval(chatId, date, msgId, bot);
            }
            return;
        }

        // Date selected: Schedule view
        if (data.startsWith(Constants.ADMIN_SCHEDULE_DATE_SELECT)) {
            String dateStr = data.substring(Constants.ADMIN_SCHEDULE_DATE_SELECT.length());
            LocalDate date = LocalDate.parse(dateStr);
            handleViewScheduleList(chatId, date, msgId, bot);
            return;
        }

        // Appointment selected for cancel
        if (data.startsWith(Constants.ADMIN_CANCEL_APPT_PREFIX)) {
            Long apptId = Long.parseLong(data.substring(Constants.ADMIN_CANCEL_APPT_PREFIX.length()));
            handleAdminCancelAskConfirm(chatId, apptId, msgId, bot);
            return;
        }

        // Confirm cancel appointment
        if (data.startsWith(Constants.ADMIN_CONFIRM_CANCEL_APPT_PREFIX)) {
            Long apptId = Long.parseLong(data.substring(Constants.ADMIN_CONFIRM_CANCEL_APPT_PREFIX.length()));
            handleAdminConfirmCancel(chatId, apptId, msgId, bot);
            return;
        }
        
        // Обработка пошагового выбора шаблона расписания
        if (data.startsWith(Constants.TEMPLATE_WEEKDAY_PREFIX)) {
            handleTemplateWeekdayToggle(chatId, msgId, data, bot, callbackId);
            return;
        }
        
        if (data.startsWith(Constants.TEMPLATE_TIME_PREFIX)) {
            handleTemplateTimeToggle(chatId, msgId, data, bot, callbackId);
            return;
        }
        
        if (data.startsWith(Constants.TEMPLATE_PERIOD_PREFIX)) {
            handleTemplatePeriodSelect(chatId, msgId, data, bot, callbackId);
            return;
        }
        
        if (Constants.TEMPLATE_CONTINUE.equals(data)) {
            handleTemplateContinue(chatId, msgId, bot, callbackId);
            return;
        }
        
        // Обработка просмотра расписания списком
        if (Constants.SCHEDULE_VIEW_NEXT_DAY.equals(data)) {
            LocalDate currentDate = BotStateManager.getData(chatId, "schedule_view_date", LocalDate.class);
            if (currentDate == null) currentDate = LocalDate.now();
            handleViewScheduleList(chatId, currentDate.plusDays(1), msgId, bot);
            return;
        }
        
        if (Constants.SCHEDULE_VIEW_CALENDAR.equals(data)) {
            BotStateManager.setState(chatId, UserState.ADMIN_VIEW_SCHEDULE_SELECT_DATE);
            bot.editMessageText(
                    "\uD83D\uDCCB *\u041f\u0440\u043e\u0441\u043c\u043e\u0442\u0440 \u0440\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u044f*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u0430\u0431\u043e\u0447\u0438\u0439 \u0434\u0435\u043d\u044c:\n\uD83D\uDCC5 \u2014 \u0440\u0430\u0431\u043e\u0447\u0438\u0435 \u0434\u043d\u0438",
                    chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminScheduleCalendar(YearMonth.now()), chatId, msgId);
            return;
        }

        // Date selected: Add work day
        if (data.startsWith(Constants.ADMIN_DATE_SELECT)) {
            String dateStr = data.substring(Constants.ADMIN_DATE_SELECT.length());
            LocalDate date = LocalDate.parse(dateStr);
            BotStateManager.setData(chatId, BotStateManager.KEY_ADMIN_DATE, date);
            BotStateManager.setState(chatId, UserState.ADMIN_ADD_WORK_DAY_SLOTS);
            bot.editMessageText(
                    "\uD83D\uDCC5 \u0412\u044b\u0431\u0440\u0430\u043d\u0430 \u0434\u0430\u0442\u0430: *" + date.format(DATE_FORMATTER) + "*\n\n" +
                    "\u23F0 \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u044b\u0435 \u0441\u043b\u043e\u0442\u044b \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043f\u044f\u0442\u0443\u044e (10:00,11:00,12:00)\n" +
                    "\u0418\u043b\u0438 \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 *default* \u0434\u043b\u044f \u0441\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u044b\u0445 (10:00-18:00)",
                    chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(null, chatId, msgId);
            return;
        }

        // Date selected: Close day
        if (data.startsWith(Constants.ADMIN_CLOSE_DATE_SELECT)) {
            String dateStr = data.substring(Constants.ADMIN_CLOSE_DATE_SELECT.length());
            LocalDate date = LocalDate.parse(dateStr);
            DatabaseService.closeDay(date);
            BotStateManager.clear(chatId);
            bot.editMessageText("\uD83D\uDEAB \u0414\u0435\u043d\u044c *" + date.format(DATE_FORMATTER) + "* \u0437\u0430\u043a\u0440\u044b\u0442 \u0434\u043b\u044f \u0437\u0430\u043f\u0438\u0441\u0438.",
                    chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(null, chatId, msgId);
            showAdminMenuNewMessage(chatId, bot);
            return;
        }

        // Date selected: Slot management
        if (data.startsWith(Constants.ADMIN_SLOT_DATE_SELECT)) {
            String rest = data.substring(Constants.ADMIN_SLOT_DATE_SELECT.length());
            int underscoreIdx = rest.indexOf('_');
            String action = rest.substring(0, underscoreIdx);
            String dateStr = rest.substring(underscoreIdx + 1);
            LocalDate date = LocalDate.parse(dateStr);
            BotStateManager.setData(chatId, BotStateManager.KEY_ADMIN_DATE, date);

            if ("add".equals(action)) {
                BotStateManager.setState(chatId, UserState.ADMIN_ADD_SLOTS_TIME);
                bot.editMessageText(
                        "\uD83D\uDCC5 \u0412\u044b\u0431\u0440\u0430\u043d\u0430 \u0434\u0430\u0442\u0430: *" + date.format(DATE_FORMATTER) + "*\n\n" +
                        "\u23F0 \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0432\u0440\u0435\u043c\u044f \u043d\u043e\u0432\u043e\u0433\u043e \u0441\u043b\u043e\u0442\u0430 \u0432 \u0444\u043e\u0440\u043c\u0430\u0442\u0435 \u0427\u0427:\u041c\u041c (\u043d\u0430\u043f\u0440\u0438\u043c\u0435\u0440: *14:30*)",
                        chatId, msgId, "Markdown");
            } else {
                handleShowSlotsForRemoval(chatId, date, msgId, bot);
            }
            return;
        }

        // Date selected: Schedule view
        if (data.startsWith(Constants.ADMIN_SCHEDULE_DATE_SELECT)) {
            String dateStr = data.substring(Constants.ADMIN_SCHEDULE_DATE_SELECT.length());
            LocalDate date = LocalDate.parse(dateStr);
            handleViewScheduleList(chatId, date, msgId, bot);
            return;
        }

        // Appointment selected for cancel
        if (data.startsWith(Constants.ADMIN_CANCEL_APPT_PREFIX)) {
            Long apptId = Long.parseLong(data.substring(Constants.ADMIN_CANCEL_APPT_PREFIX.length()));
            handleAdminCancelAskConfirm(chatId, apptId, msgId, bot);
            return;
        }

        // Confirm cancel appointment
        if (data.startsWith(Constants.ADMIN_CONFIRM_CANCEL_APPT_PREFIX)) {
            Long apptId = Long.parseLong(data.substring(Constants.ADMIN_CONFIRM_CANCEL_APPT_PREFIX.length()));
            handleAdminConfirmCancel(chatId, apptId, msgId, bot);
            return;
        }
        
        // Обработка выбора процента предоплаты
        if (data.startsWith(Constants.ADMIN_SET_PREPAYMENT_PREFIX)) {
            handleSetPrepayment(chatId, msgId, data, bot);
            return;
        }

        // Main menu items
        switch (data) {
            case Constants.ADMIN_ADD_DAY:
                BotStateManager.setState(chatId, UserState.ADMIN_ADD_WORK_DAY_DATE);
                bot.editMessageText(
                        "\uD83D\uDCC5 *\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0440\u0430\u0431\u043e\u0447\u0438\u0439 \u0434\u0435\u043d\u044c*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u0430\u0442\u0443:\n\u2705 \u2014 \u0443\u0436\u0435 \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d\u043d\u044b\u0435 \u0434\u043d\u0438",
                        chatId, msgId, "Markdown");
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminAddDayCalendar(YearMonth.now()), chatId, msgId);
                break;

            case Constants.ADMIN_MANAGE_SLOTS:
                BotStateManager.setState(chatId, UserState.ADMIN_MENU);
                bot.editMessageText("\u23F0 *\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u0441\u043b\u043e\u0442\u0430\u043c\u0438*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435:",
                        chatId, msgId, "Markdown");
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getSlotManagementMenu(), chatId, msgId);
                break;

            case Constants.ADMIN_ADD_SLOTS:
                BotStateManager.setState(chatId, UserState.ADMIN_ADD_SLOTS_DATE);
                bot.editMessageText(
                        "\uD83D\uDCC5 *\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0441\u043b\u043e\u0442*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u0430\u0431\u043e\u0447\u0438\u0439 \u0434\u0435\u043d\u044c:\n\u2705 \u2014 \u0440\u0430\u0431\u043e\u0447\u0438\u0435 \u0434\u043d\u0438",
                        chatId, msgId, "Markdown");
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminSlotCalendar(YearMonth.now(), "add"), chatId, msgId);
                break;

            case Constants.ADMIN_REMOVE_SLOTS:
                BotStateManager.setState(chatId, UserState.ADMIN_REMOVE_SLOT_SELECT_DAY);
                bot.editMessageText(
                        "\uD83D\uDCC5 *\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0441\u043b\u043e\u0442*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u0430\u0431\u043e\u0447\u0438\u0439 \u0434\u0435\u043d\u044c:\n\u2705 \u2014 \u0440\u0430\u0431\u043e\u0447\u0438\u0435 \u0434\u043d\u0438",
                        chatId, msgId, "Markdown");
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminSlotCalendar(YearMonth.now(), "remove"), chatId, msgId);
                break;

            case Constants.ADMIN_CLOSE_DAY:
                BotStateManager.setState(chatId, UserState.ADMIN_CLOSE_DAY_SELECT);
                bot.editMessageText(
                        "\uD83D\uDEAB *\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u0434\u0435\u043d\u044c*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0440\u0430\u0431\u043e\u0447\u0438\u0439 \u0434\u0435\u043d\u044c \u0434\u043b\u044f \u0437\u0430\u043a\u0440\u044b\u0442\u0438\u044f:\n\u2705 \u2014 \u0440\u0430\u0431\u043e\u0447\u0438\u0435 \u0434\u043d\u0438",
                        chatId, msgId, "Markdown");
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminCloseDayCalendar(YearMonth.now()), chatId, msgId);
                break;

            case Constants.ADMIN_CANCEL_APPOINTMENT:
                handleShowAllAppointments(chatId, msgId, bot);
                break;

            case Constants.ADMIN_VIEW_SCHEDULE:
                handleViewScheduleList(chatId, LocalDate.now(), msgId, bot);
                break;

            case Constants.ADMIN_PRICE:
                handleShowPrice(chatId, msgId, bot);
                break;

            case Constants.ADMIN_EDIT_PRICE:
                BotStateManager.setState(chatId, UserState.ADMIN_EDIT_PRICE);
                bot.editMessageText(
                        "✏️ *Редактирование прайса*\n\n" +
                        "Введите новый текст прайса.\n" +
                        "Можно использовать Markdown: *жирный*\n\n" +
                        "Или отправьте фото прайса — бот сохранит его как изображение.",
                        chatId, msgId, "Markdown");
                bot.editMessageReplyMarkup(null, chatId, msgId);
                break;

            case Constants.ADMIN_STATISTICS:
                new StatisticsHandler().handleStatisticsMenu(callback, bot);
                break;

            case Constants.ADMIN_REVIEWS:
                new StatisticsHandler().handleReviewsMenu(callback, bot);
                break;

            case Constants.ADMIN_BROADCAST:
                new StatisticsHandler().handleBroadcastMenu(callback, bot);
                break;

            case Constants.ADMIN_SCHEDULE_TEMPLATE:
                handleScheduleTemplate(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_SETTINGS:
                handleSettingsMenu(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_WELCOME:
                handleEditWelcome(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_LINKS:
                handleEditLinks(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_TOGGLE_SUBSCRIPTION:
                handleToggleSubscription(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_SUBSCRIPTION_CHANNEL:
                handleEditSubscriptionChannel(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_TOGGLE_REVIEWS:
                handleToggleReviews(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_REVIEWS_CHANNEL:
                handleEditReviewsChannel(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_PREPAYMENT:
                handleEditPrepayment(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_NOTIFICATIONS:
                handleEditNotifications(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_REMINDER_24H:
                handleEditReminder24h(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_REMINDER_3H:
                handleEditReminder3h(chatId, msgId, bot);
                break;
                
            case Constants.ADMIN_EDIT_ADMIN_REMINDER:
                handleEditAdminReminder(chatId, msgId, bot);
                break;

            case Constants.ADMIN_EXIT:
                BotStateManager.clear(chatId);
                bot.editMessageText("✅ Выход из админ-панели", chatId, msgId);
                break;

            case Constants.ADMIN_BACK_TO_MENU:
                BotStateManager.setState(chatId, UserState.ADMIN_MENU);
                bot.editMessageText("⚙️ *АДМИН-ПАНЕЛЬ*\n\nВыберите действие:",
                        chatId, msgId, "Markdown");
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminMenu(), chatId, msgId);
                break;

            default:
                log.warn("Unknown admin callback: {}", data);
                break;
        }
    }

    private void handleScheduleTemplate(Long chatId, int msgId, LashesBot bot) {
        BotStateManager.setState(chatId, UserState.ADMIN_SCHEDULE_TEMPLATE_DAYS);
        BotStateManager.clearData(chatId);
        bot.editMessageText(
            "🗓 *Настройка расписания шаблоном*\n\n" +
            "Шаг 1/3: Выберите дни недели, в которые вы работаете.\n" +
            "Нажмите на нужные дни, они отметятся галочкой ✅",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getTemplateWeekdaysKeyboard(new ArrayList<>()), chatId, msgId);
    }

    // ============ Calendar navigation ============

    private void handleAdminCalendarNav(CallbackQuery callback, LashesBot bot, String data) {
        Long chatId = callback.getMessage().getChatId();
        int msgId = callback.getMessage().getMessageId();

        boolean isPrev = data.startsWith(Constants.ADMIN_CALENDAR_PREV);
        String rest = isPrev
                ? data.substring(Constants.ADMIN_CALENDAR_PREV.length())
                : data.substring(Constants.ADMIN_CALENDAR_NEXT.length());

        // rest: "admin_add_2025-04" etc.
        int lastUnderscore = rest.lastIndexOf('_');
        String mode = rest.substring(0, lastUnderscore);
        String ymStr = rest.substring(lastUnderscore + 1);
        YearMonth ym = YearMonth.parse(ymStr);

        switch (mode) {
            case "admin_add":
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminAddDayCalendar(ym), chatId, msgId);
                break;
            case "admin_close":
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminCloseDayCalendar(ym), chatId, msgId);
                break;
            case "admin_schedule":
                bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminScheduleCalendar(ym), chatId, msgId);
                break;
            default:
                if (mode.startsWith("admin_slot_")) {
                    String action = mode.substring("admin_slot_".length());
                    bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminSlotCalendar(ym, action), chatId, msgId);
                }
                break;
        }
        bot.answerCallback(callback.getId());
    }

    // ============ Text state handler ============

    public void handleAdminState(Message msg, LashesBot bot, UserState state) {
        Long chatId = msg.getChatId();
        String text = msg.getText() != null ? msg.getText().trim() : "";

        switch (state) {
            case ADMIN_ADD_WORK_DAY_SLOTS:
                handleAddWorkDaySlots(chatId, text, bot);
                break;
            case ADMIN_ADD_SLOTS_TIME:
                handleAddSlotTime(chatId, text, bot);
                break;
            case ADMIN_REMOVE_SLOT_SELECT_TIME:
                handleRemoveSlotSelectTime(chatId, text, bot);
                break;
            case ADMIN_EDIT_PRICE:
                handleSavePrice(chatId, msg, bot);
                break;
            case ADMIN_EDIT_WELCOME_MESSAGE:
                handleSaveWelcomeMessage(chatId, text, bot);
                break;
            case ADMIN_EDIT_SUBSCRIPTION_LINK:
                handleSaveSubscriptionLink(chatId, text, bot);
                break;
            case ADMIN_EDIT_REVIEWS_LINK:
                handleSaveReviewsLink(chatId, text, bot);
                break;
            case ADMIN_EDIT_REMINDER_24H_TEXT:
                handleSaveReminder24hText(chatId, text, bot);
                break;
            case ADMIN_EDIT_REMINDER_3H_TEXT:
                handleSaveReminder3hText(chatId, text, bot);
                break;
            case ADMIN_EDIT_ADMIN_REMINDER_TEXT:
                handleSaveAdminReminderText(chatId, text, bot);
                break;
            default:
                log.warn("Unhandled admin state: {}", state);
                break;
        }
    }

    // ============ 1. Add work day ============

    private void handleAddWorkDaySlots(Long chatId, String text, LashesBot bot) {
        LocalDate date = BotStateManager.getData(chatId, BotStateManager.KEY_ADMIN_DATE, LocalDate.class);

        if (date == null) {
            bot.sendMessage(chatId, "\u274C \u041e\u0448\u0438\u0431\u043a\u0430: \u0434\u0430\u0442\u0430 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430. \u041d\u0430\u0436\u043c\u0438\u0442\u0435 '\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0440\u0430\u0431\u043e\u0447\u0438\u0439 \u0434\u0435\u043d\u044c' \u0441\u043d\u043e\u0432\u0430.");
            BotStateManager.clear(chatId);
            return;
        }

        List<String> slots;
        if (text.equalsIgnoreCase("default")) {
            slots = Constants.DEFAULT_SLOTS;
        } else {
            slots = new ArrayList<>();
            for (String s : text.split("\\s*,\\s*")) {
                if (!s.matches("\\d{2}:\\d{2}")) {
                    bot.sendMessage(chatId, "\u274C \u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 \u0444\u043e\u0440\u043c\u0430\u0442 \u0432\u0440\u0435\u043c\u0435\u043d\u0438: *" + s + "*\n\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0441\u043b\u043e\u0442\u044b \u0432 \u0444\u043e\u0440\u043c\u0430\u0442\u0435 *\u0427\u0427:\u041c\u041c* \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043f\u044f\u0442\u0443\u044e:", "Markdown");
                    return;
                }
                slots.add(s);
            }
        }

        try {
            WorkDay workDay = new WorkDay();
            workDay.setDate(date);
            workDay.setWorking(true);
            workDay.setSlots(mapper.writeValueAsString(slots));

            DatabaseService.saveWorkDay(workDay);
            BotStateManager.clear(chatId);

            bot.sendMessage(chatId,
                    "\u2705 \u0420\u0430\u0431\u043e\u0447\u0438\u0439 \u0434\u0435\u043d\u044c *" + date.format(DATE_FORMATTER) + "* \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d \u0441\u043e \u0441\u043b\u043e\u0442\u0430\u043c\u0438: " + String.join(", ", slots),
                    "Markdown");
            showAdminMenuNewMessage(chatId, bot);
        } catch (Exception e) {
            log.error("Failed to save work day", e);
            bot.sendMessage(chatId, "\u274C \u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0438. \u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437:");
        }
    }

    // ============ 2. Add slot ============

    private void handleAddSlotTime(Long chatId, String text, LashesBot bot) {
        LocalDate date = BotStateManager.getData(chatId, BotStateManager.KEY_ADMIN_DATE, LocalDate.class);

        if (date == null) {
            bot.sendMessage(chatId, "\u274C \u041e\u0448\u0438\u0431\u043a\u0430: \u0434\u0430\u0442\u0430 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430. \u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0441\u043d\u043e\u0432\u0430.");
            BotStateManager.clear(chatId);
            return;
        }

        if (!text.matches("\\d{2}:\\d{2}")) {
            bot.sendMessage(chatId, "\u274C \u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 \u0444\u043e\u0440\u043c\u0430\u0442. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0432\u0440\u0435\u043c\u044f \u0432 \u0444\u043e\u0440\u043c\u0430\u0442\u0435 *\u0427\u0427:\u041c\u041c* (\u043d\u0430\u043f\u0440\u0438\u043c\u0435\u0440: *14:30*):", "Markdown");
            return;
        }

        Optional<WorkDay> wd = DatabaseService.getWorkDay(date);
        if (wd.isEmpty() || !wd.get().isWorking()) {
            bot.sendMessage(chatId, "\u274C \u0414\u0435\u043d\u044c " + date.format(DATE_FORMATTER) + " \u043d\u0435 \u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u0440\u0430\u0431\u043e\u0447\u0438\u043c.");
            BotStateManager.clear(chatId);
            return;
        }

        DatabaseService.addSlotToDay(date, text);
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "\u2705 \u0421\u043b\u043e\u0442 *" + text + "* \u0434\u043e\u0431\u0430\u0432\u043b\u0435\u043d \u0432 \u0434\u0435\u043d\u044c *" + date.format(DATE_FORMATTER) + "*", "Markdown");
        showAdminMenuNewMessage(chatId, bot);
    }

    // ============ 3. Remove slot ============

    private void handleShowSlotsForRemoval(Long chatId, LocalDate date, int msgId, LashesBot bot) {
        Optional<WorkDay> workDayOpt = DatabaseService.getWorkDay(date);

        if (workDayOpt.isEmpty() || !workDayOpt.get().isWorking()) {
            bot.editMessageText("\u274C \u0414\u0435\u043d\u044c *" + date.format(DATE_FORMATTER) + "* \u043d\u0435 \u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u0440\u0430\u0431\u043e\u0447\u0438\u043c.",
                    chatId, msgId, "Markdown");
            BotStateManager.clear(chatId);
            return;
        }

        try {
            List<String> slots = mapper.readValue(workDayOpt.get().getSlots(), new TypeReference<List<String>>() {});
            BotStateManager.setData(chatId, BotStateManager.KEY_ADMIN_DATE, date);
            BotStateManager.setData(chatId, BotStateManager.KEY_ADMIN_SLOTS, slots);
            BotStateManager.setState(chatId, UserState.ADMIN_REMOVE_SLOT_SELECT_TIME);

            bot.editMessageText(
                    "\uD83D\uDCC5 \u0414\u0430\u0442\u0430: *" + date.format(DATE_FORMATTER) + "*\n\n" +
                    "\u23F0 *\u0422\u0435\u043a\u0443\u0449\u0438\u0435 \u0441\u043b\u043e\u0442\u044b:* " + String.join(", ", slots) + "\n\n" +
                    "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0441\u043b\u043e\u0442, \u043a\u043e\u0442\u043e\u0440\u044b\u0439 \u043d\u0443\u0436\u043d\u043e \u0443\u0434\u0430\u043b\u0438\u0442\u044c (\u043d\u0430\u043f\u0440\u0438\u043c\u0435\u0440, *12:00*):",
                    chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(null, chatId, msgId);
        } catch (Exception e) {
            bot.editMessageText("\u274C \u041e\u0448\u0438\u0431\u043a\u0430 \u043f\u0440\u0438 \u0447\u0442\u0435\u043d\u0438\u0438 \u0441\u043b\u043e\u0442\u043e\u0432.", chatId, msgId);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRemoveSlotSelectTime(Long chatId, String text, LashesBot bot) {
        LocalDate date = BotStateManager.getData(chatId, BotStateManager.KEY_ADMIN_DATE, LocalDate.class);
        List<String> slots = BotStateManager.getData(chatId, BotStateManager.KEY_ADMIN_SLOTS, List.class);

        if (date == null || slots == null) {
            bot.sendMessage(chatId, "\u274C \u041e\u0448\u0438\u0431\u043a\u0430: \u0434\u0430\u043d\u043d\u044b\u0435 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u044b. \u041f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435 \u0437\u0430\u043d\u043e\u0432\u043e.");
            BotStateManager.clear(chatId);
            return;
        }

        List<String> mutableSlots = new ArrayList<>(slots);
        if (mutableSlots.remove(text)) {
            DatabaseService.updateSlots(date, mutableSlots);
            BotStateManager.clear(chatId);
            bot.sendMessage(chatId,
                    "\u2705 \u0421\u043b\u043e\u0442 *" + text + "* \u0443\u0434\u0430\u043b\u0451\u043d \u0438\u0437 *" + date.format(DATE_FORMATTER) + "*", "Markdown");
            showAdminMenuNewMessage(chatId, bot);
        } else {
            bot.sendMessage(chatId, "\u274C \u0421\u043b\u043e\u0442 \"" + text + "\" \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d.\n\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u044b\u0435 \u0441\u043b\u043e\u0442\u044b: " + String.join(", ", mutableSlots));
        }
    }

    // ============ 4. Cancel appointment ============

    private void handleShowAllAppointments(Long chatId, int msgId, LashesBot bot) {
        List<Appointment> appointments = DatabaseService.getAllActiveAppointments();

        if (appointments.isEmpty()) {
            bot.editMessageText("\uD83D\uDCED \u041d\u0435\u0442 \u0430\u043a\u0442\u0438\u0432\u043d\u044b\u0445 \u0437\u0430\u043f\u0438\u0441\u0435\u0439 \u043a\u043b\u0438\u0435\u043d\u0442\u043e\u0432.", chatId, msgId);
            bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminMenu(), chatId, msgId);
            return;
        }

        BotStateManager.setState(chatId, UserState.ADMIN_CANCEL_APPOINTMENT_LIST);
        bot.editMessageText(
                "\u274C *\u041e\u0442\u043c\u0435\u043d\u0430 \u0437\u0430\u043f\u0438\u0441\u0438 \u043a\u043b\u0438\u0435\u043d\u0442\u0430*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0437\u0430\u043f\u0438\u0441\u044c \u0434\u043b\u044f \u043e\u0442\u043c\u0435\u043d\u044b:",
                chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getAppointmentListKeyboard(appointments), chatId, msgId);
    }

    private void handleAdminCancelAskConfirm(Long chatId, Long apptId, int msgId, LashesBot bot) {
        Optional<Appointment> apptOpt = DatabaseService.getAppointment(apptId);
        if (apptOpt.isEmpty()) {
            bot.editMessageText("\u274C \u0417\u0430\u043f\u0438\u0441\u044c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430.", chatId, msgId);
            return;
        }

        Appointment appt = apptOpt.get();
        String dateStr = appt.getAppointmentTime().format(DATE_FORMATTER);
        String timeStr = appt.getAppointmentTime().format(TIME_FORMATTER);

        bot.editMessageText(
                "\u26A0\uFE0F *\u0412\u044b \u0443\u0432\u0435\u0440\u0435\u043d\u044b, \u0447\u0442\u043e \u0445\u043e\u0442\u0438\u0442\u0435 \u043e\u0442\u043c\u0435\u043d\u0438\u0442\u044c \u0437\u0430\u043f\u0438\u0441\u044c?*\n\n" +
                "\uD83D\uDC64 \u041a\u043b\u0438\u0435\u043d\u0442: *" + appt.getUserName() + "*\n" +
                "\uD83D\uDCC5 \u0414\u0430\u0442\u0430: *" + dateStr + "* \u0432 *" + timeStr + "*\n" +
                "\uD83D\uDCF1 \u0422\u0435\u043b\u0435\u0444\u043e\u043d: " + appt.getPhone(),
                chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getConfirmCancelAppointmentKeyboard(apptId), chatId, msgId);
    }

    private void handleAdminConfirmCancel(Long chatId, Long apptId, int msgId, LashesBot bot) {
        Optional<Appointment> apptOpt = DatabaseService.getAppointment(apptId);
        if (apptOpt.isEmpty()) {
            bot.editMessageText("\u274C \u0417\u0430\u043f\u0438\u0441\u044c \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430.", chatId, msgId);
            return;
        }

        Appointment appt = apptOpt.get();

        if (appt.getReminderJobKey() != null && !appt.getReminderJobKey().isEmpty()) {
            ReminderScheduler.cancelReminder(appt.getReminderJobKey());
        }

        DatabaseService.cancelAppointment(apptId);
        BotStateManager.clear(chatId);

        String dateStr = appt.getAppointmentTime().format(DATE_FORMATTER);
        String timeStr = appt.getAppointmentTime().format(TIME_FORMATTER);

        bot.editMessageText(
                "\u2705 \u0417\u0430\u043f\u0438\u0441\u044c \u043a\u043b\u0438\u0435\u043d\u0442\u0430 *" + appt.getUserName() + "* \u043d\u0430 *" + dateStr + "* \u0432 *" + timeStr + "* \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430.",
                chatId, msgId, "Markdown");

        // Notify client
        String clientText = String.format(
                "\u26A0\uFE0F \u0412\u0430\u0448\u0430 \u0437\u0430\u043f\u0438\u0441\u044c \u043d\u0430 %s \u0432 %s \u0431\u044b\u043b\u0430 \u043e\u0442\u043c\u0435\u043d\u0435\u043d\u0430 \u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u043e\u043c.\n\n" +
                "\u0414\u043b\u044f \u0443\u0442\u043e\u0447\u043d\u0435\u043d\u0438\u044f \u0434\u0435\u0442\u0430\u043b\u0435\u0439 \u0441\u0432\u044f\u0436\u0438\u0442\u0435\u0441\u044c \u0441 \u043d\u0430\u043c\u0438: @%s",
                dateStr, timeStr,
                BotConfig.getBotUsername().replace("@", ""));
        bot.sendMessage(appt.getUserId(), clientText);

        showAdminMenuNewMessage(chatId, bot);
    }

    // ============ 5. Schedule view ============

    private void handleViewScheduleForDate(Long chatId, LocalDate date, int msgId, LashesBot bot) {
        Optional<WorkDay> workDayOpt = DatabaseService.getWorkDay(date);
        List<Appointment> appointments = DatabaseService.getAppointmentsByDate(date);

        StringBuilder schedule = new StringBuilder();
        schedule.append("\uD83D\uDCC5 *\u0420\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u0435 \u043d\u0430 ").append(date.format(DATE_FORMATTER)).append("*\n\n");

        if (workDayOpt.isEmpty() || !workDayOpt.get().isWorking()) {
            schedule.append("\uD83D\uDEAB *\u0412\u044b\u0445\u043e\u0434\u043d\u043e\u0439 \u0434\u0435\u043d\u044c*");
        } else {
            schedule.append("*\u0417\u0430\u043f\u0438\u0441\u0430\u043d\u043d\u044b\u0435 \u043a\u043b\u0438\u0435\u043d\u0442\u044b:*\n");
            if (appointments.isEmpty()) {
                schedule.append("\u2014 \u041d\u0435\u0442 \u0437\u0430\u043f\u0438\u0441\u0435\u0439\n");
            } else {
                for (Appointment a : appointments) {
                    schedule.append("\u2022 ").append(a.getAppointmentTime().format(TIME_FORMATTER))
                            .append(" \u2014 ").append(a.getUserName())
                            .append(" (").append(a.getPhone()).append(")\n");
                }
            }

            List<String> freeSlots = DatabaseService.getAvailableSlots(date);
            schedule.append("\n*\u0421\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u0435 \u0441\u043b\u043e\u0442\u044b:*\n");
            if (freeSlots.isEmpty()) {
                schedule.append("\u2014 \u0412\u0441\u0435 \u0441\u043b\u043e\u0442\u044b \u0437\u0430\u043d\u044f\u0442\u044b");
            } else {
                schedule.append(String.join(", ", freeSlots));
            }
        }

        BotStateManager.clear(chatId);
        bot.editMessageText(schedule.toString(), chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getAdminMenu(), chatId, msgId);
    }

    // ============ 6. Price ============

    private void handleShowPrice(Long chatId, int msgId, LashesBot bot) {
        String priceText = DatabaseService.getSetting("price_text").orElse(DEFAULT_PRICE_TEXT);

        bot.editMessageText(
                "\uD83D\uDCB0 *\u0422\u0415\u041A\u0423\u0429\u0418\u0419 \u041F\u0420\u0410\u0419\u0421:*\n\n" + priceText,
                chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getPriceAdminKeyboard(), chatId, msgId);
    }

    private void handleSavePrice(Long chatId, Message msg, LashesBot bot) {
        // Photo support
        if (msg.hasPhoto()) {
            var photos = msg.getPhoto();
            String fileId = photos.get(photos.size() - 1).getFileId();
            DatabaseService.saveSetting("price_photo_id", fileId);
            DatabaseService.saveSetting("price_text", "");
            BotStateManager.clear(chatId);
            bot.sendMessage(chatId, "\u2705 \u0424\u043e\u0442\u043e \u043f\u0440\u0430\u0439\u0441\u0430 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u043e! \u0422\u0435\u043f\u0435\u0440\u044c \u043e\u043d\u043e \u0431\u0443\u0434\u0435\u0442 \u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0442\u044c\u0441\u044f \u043a\u043b\u0438\u0435\u043d\u0442\u0430\u043c.");
            showAdminMenuNewMessage(chatId, bot);
            return;
        }

        String text = msg.getText() != null ? msg.getText().trim() : "";
        if (text.isEmpty()) {
            bot.sendMessage(chatId, "\u274C \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0442\u0435\u043a\u0441\u0442 \u043f\u0440\u0430\u0439\u0441\u0430 \u0438\u043b\u0438 \u043e\u0442\u043f\u0440\u0430\u0432\u044c\u0442\u0435 \u0444\u043e\u0442\u043e:");
            return;
        }

        DatabaseService.saveSetting("price_text", text);
        DatabaseService.saveSetting("price_photo_id", "");
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "\u2705 \u041f\u0440\u0430\u0439\u0441 \u0443\u0441\u043f\u0435\u0448\u043d\u043e \u043e\u0431\u043d\u043e\u0432\u043b\u0451\u043d!");
        showAdminMenuNewMessage(chatId, bot);
    }

    // ============ Photo handler ============

    public void handleAdminPhoto(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        UserState state = BotStateManager.getState(chatId);
        if (state == UserState.ADMIN_EDIT_PRICE) {
            handleSavePrice(chatId, msg, bot);
        }
    }

    // ============ Helpers ============

    private void showAdminMenuNewMessage(Long chatId, LashesBot bot) {
        BotStateManager.setState(chatId, UserState.ADMIN_MENU);
        bot.sendMessage(chatId, "\u2699\uFE0F *\u0410\u0414\u041c\u0418\u041d-\u041f\u0410\u041d\u0415\u041b\u042c*\n\n\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435:", "Markdown",
                InlineKeyboardFactory.getAdminMenu());
    }

    // ============ Пошаговый выбор шаблона расписания ============

    @SuppressWarnings("unchecked")
    private void handleTemplateWeekdayToggle(Long chatId, int msgId, String data, LashesBot bot, String callbackId) {
        int dayValue = Integer.parseInt(data.substring(Constants.TEMPLATE_WEEKDAY_PREFIX.length()));
        
        List<Integer> selectedDays = BotStateManager.getData(chatId, "template_days", List.class);
        if (selectedDays == null) {
            selectedDays = new ArrayList<>();
        } else {
            selectedDays = new ArrayList<>(selectedDays);
        }
        
        if (selectedDays.contains(dayValue)) {
            selectedDays.remove(Integer.valueOf(dayValue));
        } else {
            selectedDays.add(dayValue);
        }
        
        BotStateManager.setData(chatId, "template_days", selectedDays);
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getTemplateWeekdaysKeyboard(selectedDays), chatId, msgId);
        bot.answerCallback(callbackId);
    }

    @SuppressWarnings("unchecked")
    private void handleTemplateTimeToggle(Long chatId, int msgId, String data, LashesBot bot, String callbackId) {
        int hour = Integer.parseInt(data.substring(Constants.TEMPLATE_TIME_PREFIX.length()));
        
        List<Integer> selectedHours = BotStateManager.getData(chatId, "template_hours", List.class);
        if (selectedHours == null) {
            selectedHours = new ArrayList<>();
        } else {
            selectedHours = new ArrayList<>(selectedHours);
        }
        
        if (selectedHours.contains(hour)) {
            selectedHours.remove(Integer.valueOf(hour));
        } else {
            selectedHours.add(hour);
        }
        
        BotStateManager.setData(chatId, "template_hours", selectedHours);
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getTemplateTimeKeyboard(selectedHours), chatId, msgId);
        bot.answerCallback(callbackId);
    }

    @SuppressWarnings("unchecked")
    private void handleTemplateContinue(Long chatId, int msgId, LashesBot bot, String callbackId) {
        UserState state = BotStateManager.getState(chatId);
        
        if (state == UserState.ADMIN_SCHEDULE_TEMPLATE_DAYS) {
            List<Integer> selectedDays = BotStateManager.getData(chatId, "template_days", List.class);
            if (selectedDays == null || selectedDays.isEmpty()) {
                bot.answerCallback(callbackId, "Выберите хотя бы один день недели", true);
                return;
            }
            
            BotStateManager.setState(chatId, UserState.ADMIN_SCHEDULE_TEMPLATE_TIME);
            bot.editMessageText(
                "🗓 *Настройка расписания шаблоном*\n\n" +
                "Шаг 2/3: Выберите рабочие часы.\n" +
                "Нажмите на нужные часы, они отметятся галочкой ✅",
                chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(InlineKeyboardFactory.getTemplateTimeKeyboard(new ArrayList<>()), chatId, msgId);
            
        } else if (state == UserState.ADMIN_SCHEDULE_TEMPLATE_TIME) {
            List<Integer> selectedHours = BotStateManager.getData(chatId, "template_hours", List.class);
            if (selectedHours == null || selectedHours.isEmpty()) {
                bot.answerCallback(callbackId, "Выберите хотя бы один час", true);
                return;
            }
            
            BotStateManager.setState(chatId, UserState.ADMIN_SCHEDULE_TEMPLATE_PERIOD);
            bot.editMessageText(
                "🗓 *Настройка расписания шаблоном*\n\n" +
                "Шаг 3/3: Выберите период, на который заполнить расписание.",
                chatId, msgId, "Markdown");
            bot.editMessageReplyMarkup(InlineKeyboardFactory.getTemplatePeriodKeyboard(), chatId, msgId);
        }
        
        bot.answerCallback(callbackId);
    }

    @SuppressWarnings("unchecked")
    private void handleTemplatePeriodSelect(Long chatId, int msgId, String data, LashesBot bot, String callbackId) {
        String period = data.substring(Constants.TEMPLATE_PERIOD_PREFIX.length());
        
        List<Integer> selectedDays = BotStateManager.getData(chatId, "template_days", List.class);
        List<Integer> selectedHours = BotStateManager.getData(chatId, "template_hours", List.class);
        
        if (selectedDays == null || selectedHours == null) {
            bot.editMessageText("❌ Ошибка: данные потеряны. Начните заново.", chatId, msgId);
            BotStateManager.clear(chatId);
            return;
        }
        
        // Определяем количество дней для заполнения
        int daysToFill;
        switch (period) {
            case "week":
                daysToFill = 7;
                break;
            case "2weeks":
                daysToFill = 14;
                break;
            case "month":
                daysToFill = 30;
                break;
            case "2months":
                daysToFill = 60;
                break;
            default:
                daysToFill = 7;
        }
        
        // Создаём слоты из выбранных часов
        List<String> slots = new ArrayList<>();
        for (int hour : selectedHours) {
            slots.add(String.format("%02d:00", hour));
        }
        
        // Заполняем расписание
        int addedDays = 0;
        LocalDate currentDate = LocalDate.now();
        
        try {
            for (int i = 0; i < daysToFill * 2; i++) { // *2 чтобы точно покрыть нужное количество рабочих дней
                LocalDate date = currentDate.plusDays(i);
                int dayOfWeek = date.getDayOfWeek().getValue() % 7; // 0 = воскресенье, 1-6 = пн-сб
                
                if (selectedDays.contains(dayOfWeek)) {
                    WorkDay workDay = new WorkDay();
                    workDay.setDate(date);
                    workDay.setWorking(true);
                    workDay.setSlots(mapper.writeValueAsString(slots));
                    
                    DatabaseService.saveWorkDay(workDay);
                    addedDays++;
                    
                    if (addedDays >= daysToFill) {
                        break;
                    }
                }
            }
            
            BotStateManager.clear(chatId);
            bot.editMessageText(
                "✅ *Расписание успешно заполнено!*\n\n" +
                "Добавлено рабочих дней: " + addedDays + "\n" +
                "Слоты: " + String.join(", ", slots),
                chatId, msgId, "Markdown");
            
            showAdminMenuNewMessage(chatId, bot);
            
        } catch (Exception e) {
            log.error("Failed to create template schedule", e);
            bot.editMessageText("❌ Ошибка при создании расписания. Попробуйте ещё раз.", chatId, msgId);
        }
        
        bot.answerCallback(callbackId);
    }

    // ============ Просмотр расписания списком ============

    private void handleViewScheduleList(Long chatId, LocalDate date, int msgId, LashesBot bot) {
        BotStateManager.setState(chatId, UserState.ADMIN_VIEW_SCHEDULE_LIST);
        BotStateManager.setData(chatId, "schedule_view_date", date);
        
        Optional<WorkDay> workDayOpt = DatabaseService.getWorkDay(date);
        List<Appointment> appointments = DatabaseService.getAppointmentsByDate(date);
        
        StringBuilder schedule = new StringBuilder();
        schedule.append("\uD83D\uDCC5 *Расписание на ").append(date.format(DATE_FORMATTER)).append("*\n\n");
        
        if (workDayOpt.isEmpty() || !workDayOpt.get().isWorking()) {
            schedule.append("\uD83D\uDEAB *Выходной день*");
        } else {
            schedule.append("*Записанные клиенты:*\n");
            if (appointments.isEmpty()) {
                schedule.append("— Нет записей\n");
            } else {
                for (Appointment a : appointments) {
                    schedule.append("\u2022 ").append(a.getAppointmentTime().format(TIME_FORMATTER))
                            .append(" — ").append(a.getUserName())
                            .append(" (").append(a.getPhone()).append(")\n");
                }
            }
            
            List<String> freeSlots = DatabaseService.getAvailableSlots(date);
            schedule.append("\n*Свободные слоты:*\n");
            if (freeSlots.isEmpty()) {
                schedule.append("— Все слоты заняты");
            } else {
                schedule.append(String.join(", ", freeSlots));
            }
        }
        
        bot.editMessageText(schedule.toString(), chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getScheduleViewKeyboard(), chatId, msgId);
    }
    
    // ============ Настройки бота ============
    
    private void handleSettingsMenu(Long chatId, int msgId, LashesBot bot) {
        BotStateManager.setState(chatId, UserState.ADMIN_MENU);
        bot.editMessageText(
            "⚙️ *Настройки бота*\n\n" +
            "Здесь вы можете настроить приветственное сообщение и управлять ссылками.",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getSettingsMenu(), chatId, msgId);
    }
    
    private void handleEditWelcome(Long chatId, int msgId, LashesBot bot) {
        String currentWelcome = DatabaseService.getSetting("welcome_message").orElse(
            "👋 Добро пожаловать!\n\n" +
            "Я помогу вам записаться на процедуру наращивания ресниц.\n\n" +
            "📍 Адрес: укажите ваш адрес\n" +
            "🕐 Время работы: укажите время работы"
        );
        
        BotStateManager.setState(chatId, UserState.ADMIN_EDIT_WELCOME_MESSAGE);
        bot.editMessageText(
            "✏️ *Редактирование приветственного сообщения*\n\n" +
            "*Текущее сообщение:*\n" + currentWelcome + "\n\n" +
            "Отправьте новое приветственное сообщение.\n" +
            "Можно использовать Markdown для форматирования.",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(null, chatId, msgId);
    }
    
    private void handleSaveWelcomeMessage(Long chatId, String text, LashesBot bot) {
        if (text.isEmpty()) {
            bot.sendMessage(chatId, "❌ Сообщение не может быть пустым. Попробуйте ещё раз:");
            return;
        }
        
        DatabaseService.saveSetting("welcome_message", text);
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "✅ Приветственное сообщение успешно обновлено!");
        showAdminMenuNewMessage(chatId, bot);
    }
    
    private void handleEditLinks(Long chatId, int msgId, LashesBot bot) {
        boolean subscriptionEnabled = DatabaseService.getSetting("subscription_required")
            .map(Boolean::parseBoolean).orElse(false);
        boolean reviewsEnabled = DatabaseService.getSetting("reviews_enabled")
            .map(Boolean::parseBoolean).orElse(true);
        
        BotStateManager.setState(chatId, UserState.ADMIN_MENU);
        bot.editMessageText(
            "🔗 *Управление ссылками*\n\n" +
            "Настройте обязательную подписку на канал и канал отзывов.",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getLinksMenu(subscriptionEnabled, reviewsEnabled), chatId, msgId);
    }
    
    private void handleToggleSubscription(Long chatId, int msgId, LashesBot bot) {
        boolean currentState = DatabaseService.getSetting("subscription_required")
            .map(Boolean::parseBoolean).orElse(false);
        boolean newState = !currentState;
        
        DatabaseService.saveSetting("subscription_required", String.valueOf(newState));
        
        String message = newState 
            ? "✅ Обязательная подписка включена" 
            : "❌ Обязательная подписка выключена";
        
        bot.answerCallback(String.valueOf(msgId), message, true);
        handleEditLinks(chatId, msgId, bot);
    }
    
    private void handleEditSubscriptionChannel(Long chatId, int msgId, LashesBot bot) {
        String currentLink = DatabaseService.getSetting("subscription_channel_link")
            .orElse("https://t.me/your_channel");
        
        BotStateManager.setState(chatId, UserState.ADMIN_EDIT_SUBSCRIPTION_LINK);
        bot.editMessageText(
            "📢 *Редактирование канала подписки*\n\n" +
            "*Текущая ссылка:* " + currentLink + "\n\n" +
            "Отправьте новую ссылку на канал (например: https://t.me/your_channel)",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(null, chatId, msgId);
    }
    
    private void handleSaveSubscriptionLink(Long chatId, String text, LashesBot bot) {
        if (!text.startsWith("https://t.me/")) {
            bot.sendMessage(chatId, "❌ Неверный формат ссылки. Ссылка должна начинаться с https://t.me/");
            return;
        }
        
        DatabaseService.saveSetting("subscription_channel_link", text);
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "✅ Ссылка на канал подписки обновлена!");
        showAdminMenuNewMessage(chatId, bot);
    }
    
    private void handleToggleReviews(Long chatId, int msgId, LashesBot bot) {
        boolean currentState = DatabaseService.getSetting("reviews_enabled")
            .map(Boolean::parseBoolean).orElse(true);
        boolean newState = !currentState;
        
        DatabaseService.saveSetting("reviews_enabled", String.valueOf(newState));
        
        String message = newState 
            ? "✅ Отзывы включены" 
            : "❌ Отзывы выключены";
        
        bot.answerCallback(String.valueOf(msgId), message, true);
        handleEditLinks(chatId, msgId, bot);
    }
    
    private void handleEditReviewsChannel(Long chatId, int msgId, LashesBot bot) {
        String currentLink = DatabaseService.getSetting("reviews_channel_link")
            .orElse("https://t.me/your_reviews_channel");
        
        BotStateManager.setState(chatId, UserState.ADMIN_EDIT_REVIEWS_LINK);
        bot.editMessageText(
            "⭐️ *Редактирование канала отзывов*\n\n" +
            "*Текущая ссылка:* " + currentLink + "\n\n" +
            "Отправьте новую ссылку на канал отзывов (например: https://t.me/your_reviews)",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(null, chatId, msgId);
    }
    
    private void handleSaveReviewsLink(Long chatId, String text, LashesBot bot) {
        if (!text.startsWith("https://t.me/")) {
            bot.sendMessage(chatId, "❌ Неверный формат ссылки. Ссылка должна начинаться с https://t.me/");
            return;
        }
        
        DatabaseService.saveSetting("reviews_channel_link", text);
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "✅ Ссылка на канал отзывов обновлена!");
        showAdminMenuNewMessage(chatId, bot);
    }
    
    // ============ Настройка предоплаты ============
    
    private void handleEditPrepayment(Long chatId, int msgId, LashesBot bot) {
        int currentPercent = DatabaseService.getSetting("prepayment_percent")
            .map(Integer::parseInt).orElse(50);
        
        BotStateManager.setState(chatId, UserState.ADMIN_MENU);
        bot.editMessageText(
            "💰 *Настройка предоплаты*\n\n" +
            "Текущий процент: *" + currentPercent + "%*\n\n" +
            "Выберите новый процент предоплаты:",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getPrepaymentMenu(currentPercent), chatId, msgId);
    }
    
    private void handleSetPrepayment(Long chatId, int msgId, String data, LashesBot bot) {
        int percent = Integer.parseInt(data.substring(Constants.ADMIN_SET_PREPAYMENT_PREFIX.length()));
        
        DatabaseService.saveSetting("prepayment_percent", String.valueOf(percent));
        
        bot.answerCallback(String.valueOf(msgId), "✅ Предоплата установлена: " + percent + "%", true);
        bot.editMessageText(
            "✅ *Предоплата успешно обновлена!*\n\n" +
            "Новый процент: *" + percent + "%*",
            chatId, msgId, "Markdown");
        
        showAdminMenuNewMessage(chatId, bot);
    }
    
    // ============ Настройка уведомлений ============
    
    private void handleEditNotifications(Long chatId, int msgId, LashesBot bot) {
        BotStateManager.setState(chatId, UserState.ADMIN_MENU);
        bot.editMessageText(
            "📢 *Настройка текстов уведомлений*\n\n" +
            "Выберите, какое уведомление хотите изменить:",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(InlineKeyboardFactory.getNotificationsMenu(), chatId, msgId);
    }
    
    private void handleEditReminder24h(Long chatId, int msgId, LashesBot bot) {
        String currentText = DatabaseService.getSetting("reminder_24h_text").orElse(
            "⏰ Напоминаем, что завтра у вас запись на наращивание ресниц!"
        );
        
        BotStateManager.setState(chatId, UserState.ADMIN_EDIT_REMINDER_24H_TEXT);
        bot.editMessageText(
            "⏰ *Редактирование уведомления за 24 часа*\n\n" +
            "*Текущий текст:*\n" + currentText + "\n\n" +
            "Отправьте новый текст уведомления:",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(null, chatId, msgId);
    }
    
    private void handleSaveReminder24hText(Long chatId, String text, LashesBot bot) {
        if (text.isEmpty()) {
            bot.sendMessage(chatId, "❌ Текст не может быть пустым. Попробуйте ещё раз:");
            return;
        }
        
        DatabaseService.saveSetting("reminder_24h_text", text);
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "✅ Текст уведомления за 24 часа обновлён!");
        showAdminMenuNewMessage(chatId, bot);
    }
    
    private void handleEditReminder3h(Long chatId, int msgId, LashesBot bot) {
        String currentText = DatabaseService.getSetting("reminder_3h_text").orElse(
            "⏰ *%s*, напоминаем, что через 3 часа у вас запись на наращивание ресниц!"
        );
        
        BotStateManager.setState(chatId, UserState.ADMIN_EDIT_REMINDER_3H_TEXT);
        bot.editMessageText(
            "⏰ *Редактирование уведомления за 3 часа*\n\n" +
            "*Текущий текст:*\n" + currentText + "\n\n" +
            "Отправьте новый текст уведомления.\n" +
            "Используйте %s для подстановки имени клиента.",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(null, chatId, msgId);
    }
    
    private void handleSaveReminder3hText(Long chatId, String text, LashesBot bot) {
        if (text.isEmpty()) {
            bot.sendMessage(chatId, "❌ Текст не может быть пустым. Попробуйте ещё раз:");
            return;
        }
        
        DatabaseService.saveSetting("reminder_3h_text", text);
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "✅ Текст уведомления за 3 часа обновлён!");
        showAdminMenuNewMessage(chatId, bot);
    }
    
    private void handleEditAdminReminder(Long chatId, int msgId, LashesBot bot) {
        String currentText = DatabaseService.getSetting("admin_reminder_text").orElse(
            "⏰ *НАПОМИНАНИЕ*\n\nЧерез 2 часа к вам придёт клиент:\n👤 %s\n📱 %s\n💅 %s\n🕐 %s"
        );
        
        BotStateManager.setState(chatId, UserState.ADMIN_EDIT_ADMIN_REMINDER_TEXT);
        bot.editMessageText(
            "👨‍💼 *Редактирование уведомления админу*\n\n" +
            "*Текущий текст:*\n" + currentText + "\n\n" +
            "Отправьте новый текст уведомления.\n" +
            "Используйте %s для подстановки: имя, телефон, процедура, время.",
            chatId, msgId, "Markdown");
        bot.editMessageReplyMarkup(null, chatId, msgId);
    }
    
    private void handleSaveAdminReminderText(Long chatId, String text, LashesBot bot) {
        if (text.isEmpty()) {
            bot.sendMessage(chatId, "❌ Текст не может быть пустым. Попробуйте ещё раз:");
            return;
        }
        
        DatabaseService.saveSetting("admin_reminder_text", text);
        BotStateManager.clear(chatId);
        bot.sendMessage(chatId, "✅ Текст уведомления админу обновлён!");
        showAdminMenuNewMessage(chatId, bot);
    }
}

