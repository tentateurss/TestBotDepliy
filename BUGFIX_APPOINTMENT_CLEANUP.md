# Исправление автоудаления записей

## Проблема

Записи клиентов не удалялись через 2 часа после их окончания.

## Причина

Метод `DatabaseService.cancelAppointment()` только менял статус записи на `'cancelled'`, но не помечал её как завершённую. Это приводило к тому, что записи оставались в статусе `'active'` или `'cancelled'`, но не переходили в статус `'completed'`.

## Решение

### 1. Добавлен новый метод в DatabaseService.java

```java
public static void completeAppointment(Long id) {
    String sql = "UPDATE appointments SET status = 'completed' WHERE id = ?";
    DatabaseUtils.executeUpdate(sql, id);
}
```

Этот метод помечает запись как завершённую (`'completed'`), а не отменённую.

### 2. Обновлён AppointmentCleanupJob.java

**Было:**
```java
DatabaseService.cancelAppointment(appointmentId);
log.info("Auto-deleted appointment {} (2 hours after start)", appointmentId);
```

**Стало:**
```java
DatabaseService.completeAppointment(appointmentId);
log.info("Auto-completed appointment {} (2 hours after start)", appointmentId);
```

## Логика работы

### Статусы записей:

1. **active** - активная запись (клиент записан)
2. **cancelled** - отменённая запись (клиент или админ отменил)
3. **completed** - завершённая запись (процедура прошла)

### Когда меняется статус:

- **active → cancelled**: когда клиент или админ отменяет запись
- **active → completed**: автоматически через 2 часа после времени записи

### Планирование автозавершения:

Задание планируется в двух местах:
1. При создании записи (BookingHandler.java:321)
2. При подтверждении предоплаты (BookingHandler.java:672)

Время выполнения: `appointment_time + 2 часа`

## Проверка работы

### В логах должно появиться:

```
Scheduled cleanup for appointment 123 at 16.04.2026 16:00 (timezone: Europe/Samara)
...
Auto-completed appointment 123 (2 hours after start)
```

### Проверка в БД:

```sql
SELECT id, user_name, appointment_time, status 
FROM appointments 
WHERE status = 'completed';
```

Должны появляться записи со статусом `'completed'` через 2 часа после времени записи.

## Тестирование

1. Создайте тестовую запись на ближайшее время (например, через 10 минут)
2. Подтвердите предоплату
3. Проверьте логи - должно быть: `Scheduled cleanup for appointment X at ...`
4. Подождите 2 часа после времени записи
5. Проверьте логи - должно быть: `Auto-completed appointment X`
6. Проверьте БД - статус должен быть `'completed'`

## Важно

- Записи НЕ удаляются из БД, они только меняют статус на `'completed'`
- Это позволяет сохранить историю всех записей
- Завершённые записи не отображаются в списке активных записей
- Завершённые записи можно посмотреть в статистике

---

**Дата исправления:** 16.04.2026  
**Версия:** 2.2.1
