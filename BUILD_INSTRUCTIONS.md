# Инструкция по сборке проекта

## Что было добавлено

1. **📊 Статистика** - количество записей за месяц, популярные слоты, доход
2. **📲 Напоминание за 3 часа** - дополнительное напоминание перед записью
3. **⭐️ Отзывы** - автоматический запрос отзыва после процедуры
4. **📋 История записей клиента** - клиент может посмотреть свои прошлые визиты
5. **🔁 Быстрая повторная запись** - кнопка "Записаться снова на то же время"
6. **💬 Рассылка** - админ может отправить сообщение всем клиентам
7. **⏳ Список ожидания** - если слот занят, клиент встаёт в очередь
8. **🗓 Настройка расписания шаблоном** - добавить несколько дней сразу
9. **🗑 Автоудаление записи** - запись удаляется через 2 часа после начала
10. **🔐 Безопасность** - токены и настройки в переменных окружения

## Новые таблицы в базе данных

- `reviews` - отзывы клиентов
- `waiting_list` - список ожидания
- `user_profiles` - профили пользователей
- `booking_codes` - история кодов записи
- Новые колонки в `appointments`: `price`, `completed`, `reminder_3h_job_key`, `booking_code`

## Настройка переменных окружения

### Шаг 1: Создайте файл .env

Скопируйте `.env.example` в `.env`:

```bash
cp .env.example .env
```

### Шаг 2: Заполните .env файл

Откройте `.env` и укажите ваши настройки:

```properties
# Telegram Bot Configuration
BOT_TOKEN=your_bot_token_here
BOT_USERNAME=your_bot_username
BOT_ADMIN_IDS=123456789,987654321

# Channel Configuration
BOT_CHANNEL_ID=-1001234567890
BOT_CHANNEL_LINK=https://t.me/your_channel
BOT_CHANNEL_USERNAME=your_channel

# Reviews Channel Configuration
BOT_REVIEWS_CHANNEL_ID=-1001234567890
BOT_REVIEWS_CHANNEL_LINK=https://t.me/your_reviews_channel

# Database Configuration
DATABASE_PATH=data/lashes_bot.db

# Timezone Configuration
BOT_TIMEZONE=Europe/Samara
```

### Шаг 3: Загрузите переменные окружения

**Windows (PowerShell):**
```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
    }
}
```

**Linux/Mac:**
```bash
export $(cat .env | xargs)
```

**Или используйте IDE:**
- IntelliJ IDEA: Run → Edit Configurations → Environment Variables → Load from file (.env)

## Сборка проекта

### Вариант 1: Через IDE (IntelliJ IDEA / Eclipse)

1. Откройте проект в IDE
2. Убедитесь, что включена обработка аннотаций Lombok:
   - IntelliJ IDEA: Settings → Build → Compiler → Annotation Processors → Enable annotation processing
   - Eclipse: Установите Lombok plugin
3. Выполните: Build → Build Project
4. Запустите: Run → Run 'BotApplication'

### Вариант 2: Через Maven (если установлен)

```bash
# Очистка и компиляция
mvn clean compile

# Сборка JAR файла
mvn clean package

# Запуск
java -jar target/BotTG2-1.0.0.jar
```

### Вариант 3: Установка Maven и сборка

1. Скачайте Maven: https://maven.apache.org/download.cgi
2. Распакуйте и добавьте в PATH
3. Выполните команды из Варианта 2

## Важные изменения в коде

### Новые файлы:
- `StatisticsHandler.java` - обработчик статистики, отзывов, рассылки
- `Review.java` - модель отзыва
- `WaitingListEntry.java` - модель записи в списке ожидания
- `ReviewRequestJob.java` - задача для запроса отзыва
- `Reminder3hJob.java` - задача для напоминания за 3 часа
- `AppointmentCleanupJob.java` - задача для автоудаления записи

### Обновлённые файлы:
- `DatabaseManager.java` - добавлены новые таблицы
- `DatabaseService.java` - методы для работы с отзывами, статистикой, списком ожидания
- `Appointment.java` - новые поля (price, completed, reminder3hJobKey)
- `ReminderScheduler.java` - планирование всех типов уведомлений
- `MessageRouter.java` - обработка новых callback'ов
- `AdminHandler.java` - новые пункты меню
- `BookingHandler.java` - планирование всех задач при создании записи
- `InlineKeyboardFactory.java` - новые клавиатуры
- `Constants.java` - новые константы
- `UserState.java` - новые состояния

## Использование новых функций

### Для администратора:
1. Войдите в админ-панель: `/admin`
2. Доступные функции:
   - **📊 Статистика** - просмотр статистики по месяцам
   - **⭐️ Отзывы** - просмотр всех отзывов клиентов
   - **💬 Рассылка** - отправка сообщения всем клиентам с активными записями
   - **🗓 Настройка расписания шаблоном** - быстрое добавление рабочих дней

### Для клиента:
- После записи клиент получит 2 напоминания: за 24 часа и за 3 часа
- После процедуры бот попросит оставить отзыв
- Клиент может посмотреть историю своих записей
- Если слот занят, можно встать в список ожидания
- Запись автоматически удалится через 2 часа после начала

## Устранение ошибок компиляции

Если видите ошибки типа "The method getXXX() is undefined":
1. Это нормально - Lombok ещё не сгенерировал геттеры/сеттеры
2. Выполните полную пересборку проекта
3. В IDE: Build → Rebuild Project
4. Или через Maven: `mvn clean compile`

## Проверка работы

После запуска проверьте:
1. База данных создалась с новыми таблицами
2. Админ-панель показывает новые пункты меню
3. При создании записи планируются все задачи (проверьте логи)

## Логи

Все действия логируются. Проверьте консоль на наличие:
- "Scheduled 24h reminder for appointment..."
- "Scheduled 3h reminder for appointment..."
- "Review request scheduled"
- "Appointment cleanup scheduled"
