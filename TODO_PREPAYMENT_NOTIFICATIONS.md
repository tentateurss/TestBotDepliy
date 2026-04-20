# Руководство по завершению реализации настроек предоплаты и уведомлений

## ✅ Уже сделано:

1. **Constants.java** - добавлены константы:
   - `ADMIN_EDIT_PREPAYMENT`
   - `ADMIN_SET_PREPAYMENT_PREFIX`
   - `ADMIN_EDIT_NOTIFICATIONS`
   - `ADMIN_EDIT_REMINDER_24H/3H/ADMIN_REMINDER`

2. **UserState.java** - добавлены состояния:
   - `ADMIN_EDIT_REMINDER_24H_TEXT`
   - `ADMIN_EDIT_REMINDER_3H_TEXT`
   - `ADMIN_EDIT_ADMIN_REMINDER_TEXT`

3. **InlineKeyboardFactory.java** - добавлены меню:
   - `getPrepaymentMenu(int currentPercent)` - выбор процента (10-50%)
   - `getNotificationsMenu()` - меню редактирования уведомлений
   - Обновлено `getSettingsMenu()` с новыми кнопками

4. **AdminHandler.java** - добавлены вызовы обработчиков в switch

---

## 📝 Что нужно доделать:

### 1. AdminHandler.java - Добавить методы обработчиков

Добавьте в конец файла перед закрывающей скобкой класса:

```java
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
```

### 2. AdminHandler.java - Добавить обработку callback для процента предоплаты

В методе `handleAdminCallback` после обработки других callback'ов добавьте:

```java
// Обработка выбора процента предоплаты
if (data.startsWith(Constants.ADMIN_SET_PREPAYMENT_PREFIX)) {
    handleSetPrepayment(chatId, msgId, data, bot);
    return;
}
```

### 3. BookingHandler.java - Использовать настраиваемый процент предоплаты

Найдите строку где вычисляется `prepaymentAmount` (примерно строка 556) и замените:

```java
// Было:
double prepaymentAmount = price * 0.5;

// Стало:
int prepaymentPercent = DatabaseService.getSetting("prepayment_percent")
    .map(Integer::parseInt).orElse(50);
double prepaymentAmount = price * prepaymentPercent / 100.0;
```

### 4. Job-классы - Использовать настраиваемые тексты

#### ReminderJob.java (уведомление за 24 часа):
```java
// Найдите строку с текстом уведомления и замените:
String text = DatabaseService.getSetting("reminder_24h_text")
    .orElse("⏰ Напоминаем, что завтра у вас запись на наращивание ресниц!");
```

#### Reminder3hJob.java (уведомление за 3 часа):
```java
// Найдите строку с текстом уведомления и замените:
String template = DatabaseService.getSetting("reminder_3h_text")
    .orElse("⏰ *%s*, напоминаем, что через 3 часа у вас запись на наращивание ресниц!");
String text = String.format(template, userName);
```

#### AdminReminderJob.java (уведомление админу):
```java
// Найдите строку с текстом уведомления и замените:
String template = DatabaseService.getSetting("admin_reminder_text")
    .orElse("⏰ *НАПОМИНАНИЕ*\n\nЧерез 2 часа к вам придёт клиент:\n👤 %s\n📱 %s\n💅 %s\n🕐 %s");
String text = String.format(template, userName, phone, serviceName, timeStr);
```

---

## 🗄️ Настройки в БД

Будут автоматически созданы при первом использовании:
- `prepayment_percent` - процент предоплаты (10-50, по умолчанию 50)
- `reminder_24h_text` - текст уведомления за 24 часа
- `reminder_3h_text` - текст уведомления за 3 часа (с %s для имени)
- `admin_reminder_text` - текст уведомления админу (с %s для данных)

---

## 🧪 Тестирование

1. Откройте админ-панель: `/admin`
2. Нажмите "⚙️ Настройки бота"
3. Проверьте:
   - "💰 Настройка предоплаты" - выбор процента
   - "📢 Тексты уведомлений" - редактирование текстов
4. Создайте тестовую запись и проверьте:
   - Сумма предоплаты соответствует выбранному проценту
   - Уведомления приходят с новыми текстами

---

**Версия:** 2.2.0  
**Дата:** 16 апреля 2026
