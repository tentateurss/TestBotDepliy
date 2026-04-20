package lashes.bot.states;

public enum UserState {
    IDLE,
    AWAITING_SUBSCRIPTION,
    AWAITING_DATE,
    AWAITING_TIME,
    AWAITING_SERVICE_SELECTION,
    AWAITING_NAME,
    AWAITING_PHONE,
    AWAITING_PREPAYMENT_SCREENSHOT,
    AWAITING_CONFIRMATION,
    AWAITING_CANCEL_CONFIRMATION,

    // Админские состояния
    ADMIN_MENU,

    // Добавить рабочий день (через календарь)
    ADMIN_ADD_WORK_DAY_DATE,      // выбор даты через календарь
    ADMIN_ADD_WORK_DAY_SLOTS,     // ввод слотов текстом

    // Управление слотами
    ADMIN_ADD_SLOTS_DATE,         // выбор даты для добавления слотов (через календарь)
    ADMIN_ADD_SLOTS_TIME,         // ввод времени нового слота
    ADMIN_REMOVE_SLOT_SELECT_DAY,   // выбор даты для удаления слотов (через календарь)
    ADMIN_REMOVE_SLOT_SELECT_TIME,  // ввод времени для удаления

    // Закрыть день
    ADMIN_CLOSE_DAY_SELECT,       // выбор дня через календарь

    // Отмена записи клиента
    ADMIN_CANCEL_APPOINTMENT_LIST, // показ списка записей с кнопками
    ADMIN_CANCEL_APPOINTMENT_SELECT_DAY,   // (устаревшее, для совместимости)
    ADMIN_CANCEL_APPOINTMENT_SELECT_CLIENT, // (устаревшее, для совместимости)

    // Просмотр расписания
    ADMIN_VIEW_SCHEDULE_SELECT_DATE,  // выбор даты через календарь

    // Прайс (редактирование)
    ADMIN_EDIT_PRICE,              // ввод нового текста прайса

    // Рассылка
    ADMIN_BROADCAST,               // ввод текста для рассылки

    // Отзывы
    AWAITING_REVIEW_RATING,        // ожидание оценки
    AWAITING_REVIEW_COMMENT,       // ожидание комментария

    // Шаблон расписания (пошаговый выбор)
    ADMIN_SCHEDULE_TEMPLATE_DAYS,      // выбор дней недели
    ADMIN_SCHEDULE_TEMPLATE_TIME,      // выбор времени (часы)
    ADMIN_SCHEDULE_TEMPLATE_PERIOD,    // выбор периода (неделя, месяц и т.д.)

    // Просмотр расписания (список)
    ADMIN_VIEW_SCHEDULE_LIST,          // просмотр списка дней

    // Профиль пользователя
    PROFILE_EDIT_NAME,             // ввод имени
    PROFILE_EDIT_PHONE,            // ввод телефона
    PROFILE_EDIT_ADDITIONAL,       // ввод дополнительной информации
    
    // Настройки бота (админ)
    ADMIN_EDIT_WELCOME_MESSAGE,    // редактирование приветственного сообщения
    ADMIN_EDIT_SUBSCRIPTION_LINK,  // редактирование ссылки на канал подписки
    ADMIN_EDIT_REVIEWS_LINK,       // редактирование ссылки на канал отзывов
    
    // Настройки уведомлений
    ADMIN_EDIT_REMINDER_24H_TEXT,  // редактирование текста уведомления за 24 часа
    ADMIN_EDIT_REMINDER_3H_TEXT,   // редактирование текста уведомления за 3 часа
    ADMIN_EDIT_ADMIN_REMINDER_TEXT, // редактирование текста уведомления админу
}
