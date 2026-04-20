package lashes.bot.constants;

import java.util.List;

public class Constants {

    // Callback data
    public static final String CHECK_SUBSCRIPTION = "check_subscription";
    public static final String CONFIRM_BOOKING = "confirm_booking";
    public static final String CANCEL_BOOKING = "cancel_booking";
    public static final String CONFIRM_CANCEL = "confirm_cancel";
    public static final String BACK_TO_CALENDAR = "back_to_calendar";
    public static final String BACK_TO_MENU = "back_to_menu";
    public static final String ADMIN_BACK_TO_MENU = "admin_back_to_menu";
    public static final String CALENDAR_IGNORE = "calendar_ignore";

    // Admin callbacks
    public static final String ADMIN_ADD_DAY = "admin_add_day";
    public static final String ADMIN_MANAGE_SLOTS = "admin_manage_slots";
    public static final String ADMIN_ADD_SLOTS = "admin_add_slots";
    public static final String ADMIN_REMOVE_SLOTS = "admin_remove_slots";
    public static final String ADMIN_CLOSE_DAY = "admin_close_day";
    public static final String ADMIN_CANCEL_APPOINTMENT = "admin_cancel_appointment";
    public static final String ADMIN_VIEW_SCHEDULE = "admin_view_schedule";
    public static final String ADMIN_EXIT = "admin_exit";
    public static final String ADMIN_PRICE = "admin_price";
    public static final String ADMIN_EDIT_PRICE = "admin_edit_price";
    public static final String ADMIN_STATISTICS = "admin_statistics";
    public static final String ADMIN_REVIEWS = "admin_reviews";
    public static final String ADMIN_BROADCAST = "admin_broadcast";
    public static final String ADMIN_SCHEDULE_TEMPLATE = "admin_schedule_template";
    
    // Admin settings menu
    public static final String ADMIN_SETTINGS = "admin_settings";
    public static final String ADMIN_EDIT_WELCOME = "admin_edit_welcome";
    public static final String ADMIN_EDIT_LINKS = "admin_edit_links";
    public static final String ADMIN_TOGGLE_SUBSCRIPTION = "admin_toggle_subscription";
    public static final String ADMIN_EDIT_SUBSCRIPTION_CHANNEL = "admin_edit_subscription_channel";
    public static final String ADMIN_EDIT_REVIEWS_CHANNEL = "admin_edit_reviews_channel";
    public static final String ADMIN_TOGGLE_REVIEWS = "admin_toggle_reviews";
    
    // Prepayment settings
    public static final String ADMIN_EDIT_PREPAYMENT = "admin_edit_prepayment";
    public static final String ADMIN_SET_PREPAYMENT_PREFIX = "admin_set_prepay_";
    
    // Notification settings
    public static final String ADMIN_EDIT_NOTIFICATIONS = "admin_edit_notifications";
    public static final String ADMIN_EDIT_REMINDER_24H = "admin_edit_reminder_24h";
    public static final String ADMIN_EDIT_REMINDER_3H = "admin_edit_reminder_3h";
    public static final String ADMIN_EDIT_ADMIN_REMINDER = "admin_edit_admin_reminder";
    
    // Template schedule callbacks (пошаговый выбор)
    public static final String TEMPLATE_WEEKDAY_PREFIX = "template_day_";
    public static final String TEMPLATE_TIME_PREFIX = "template_time_";
    public static final String TEMPLATE_PERIOD_PREFIX = "template_period_";
    public static final String TEMPLATE_CONTINUE = "template_continue";
    
    // Schedule view callbacks
    public static final String SCHEDULE_VIEW_NEXT_DAY = "schedule_next_day";
    public static final String SCHEDULE_VIEW_CALENDAR = "schedule_view_calendar";

    // Statistics navigation
    public static final String STATS_PREV_MONTH = "stats_prev:";
    public static final String STATS_NEXT_MONTH = "stats_next:";

    // Review callbacks
    public static final String REVIEW_PREFIX = "review_";
    public static final String REVIEW_RATING_PREFIX = "review_rating_";
    public static final String REVIEW_SKIP = "review_skip";
    public static final String VIEW_REVIEWS = "view_reviews";

    // Waiting list
    public static final String WAITING_LIST_ADD = "waiting_add_";
    public static final String WAITING_LIST_NOTIFY = "waiting_notify_";

    // Client history
    public static final String CLIENT_HISTORY = "client_history";

    // Admin calendar callback prefixes (для разграничения admin и user календарей)
    public static final String ADMIN_CALENDAR_PREV = "admin_cal_prev_";
    public static final String ADMIN_CALENDAR_NEXT = "admin_cal_next_";
    public static final String ADMIN_DATE_SELECT = "admin_date_";
    public static final String ADMIN_CLOSE_DATE_SELECT = "admin_close_date_";
    public static final String ADMIN_SLOT_DATE_SELECT = "admin_slot_date_";
    public static final String ADMIN_SCHEDULE_DATE_SELECT = "admin_sched_date_";

    // Admin cancel appointment
    public static final String ADMIN_CANCEL_APPT_PREFIX = "admin_cancel_appt_";
    public static final String ADMIN_CONFIRM_CANCEL_APPT_PREFIX = "admin_confirm_cancel_";

    // Button texts
    public static final String BTN_BOOK = "📝 Записаться";
    public static final String BTN_PRICE = "💰 Прайс";
    public static final String BTN_PORTFOLIO = "🖼 Портфолио";
    public static final String BTN_REVIEWS = "⭐️ Отзывы";
    public static final String BTN_MY_HISTORY = "📋 Мои записи";
    public static final String BTN_ADMIN = "⚙️ Админ-панель";
    public static final String BTN_MY_PROFILE = "👤 Мой профиль";
    public static final String BTN_CANCEL_BOOKING = "❌ Отменить запись";

    // Statuses
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CANCELLED = "cancelled";

    // Date formats
    public static final String DATE_PATTERN = "dd.MM.yyyy";
    public static final String TIME_PATTERN = "HH:mm";

    // Default slots
    public static final List<String> DEFAULT_SLOTS = List.of(
            "10:00", "11:00", "12:00", "13:00", "14:00",
            "15:00", "16:00", "17:00", "18:00"
    );

    // Messages
    public static final String MSG_ACCESS_DENIED = "⛔ Доступ запрещён";
    public static final String MSG_NO_APPOINTMENTS = "📭 На эту дату нет записей";
    public static final String MSG_INVALID_DATE = "❌ Неверный формат даты. Попробуйте ещё раз (ДД.ММ.ГГГГ):";
    public static final String MSG_BOOKING_CANCELLED = "❌ Запись отменена";
    
    // Prepayment callbacks
    public static final String CONFIRM_PREPAYMENT_PREFIX = "confirm_prepay_";
    public static final String REJECT_PREPAYMENT_PREFIX = "reject_prepay_";
    
    // Services and prices
    public static final String SERVICE_CLASSIC = "Классика";
    public static final double PRICE_CLASSIC = 1500.0;
    
    public static final String SERVICE_2D = "2D объём";
    public static final double PRICE_2D = 1800.0;
    
    public static final String SERVICE_3D = "3D объём";
    public static final double PRICE_3D = 2100.0;
    
    public static final String SERVICE_HOLLYWOOD = "Голливуд";
    public static final double PRICE_HOLLYWOOD = 2500.0;
    
    public static final String SERVICE_CORRECTION_CLASSIC = "Коррекция Классика";
    public static final double PRICE_CORRECTION_CLASSIC = 1000.0;
    
    public static final String SERVICE_CORRECTION_VOLUME = "Коррекция Объём";
    public static final double PRICE_CORRECTION_VOLUME = 1300.0;
    
    public static final String SERVICE_REMOVAL = "Снятие ресниц";
    public static final double PRICE_REMOVAL = 400.0;
    
    public static final String SERVICE_COLORING = "Окрашивание ресниц";
    public static final double PRICE_COLORING = 300.0;
    
    public static final String SERVICE_LAMINATION = "Ламинирование ресниц";
    public static final double PRICE_LAMINATION = 1800.0;
}
