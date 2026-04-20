package lashes.bot.scheduler;

import lashes.bot.database.DatabaseService;
import lashes.bot.utils.ReminderScheduler;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Reminder3hJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(Reminder3hJob.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getJobDetail().getJobDataMap();

        Long appointmentId = dataMap.getLong("appointmentId");
        Long userId = dataMap.getLong("userId");
        String userName = dataMap.getString("userName");
        String appointmentTimeStr = dataMap.getString("appointmentTime");

        LocalDateTime appointmentTime = LocalDateTime.parse(appointmentTimeStr);
        String timeStr = appointmentTime.format(TIME_FORMATTER);
        String dateStr = appointmentTime.format(DATE_FORMATTER);

        String messageText = String.format(
                "⏰ *%s*, напоминаем, что через 3 часа у вас запись на наращивание ресниц!\n\n" +
                        "📅 Дата: *%s*\n" +
                        "🕐 Время: *%s*\n\n" +
                        "Ждём вас! 💖\n" +
                        "📍 Адрес: ул. Примерная, д. 123",
                userName, dateStr, timeStr
        );

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText(messageText);
        message.setParseMode("Markdown");

        try {
            ReminderScheduler.getBot().execute(message);
            log.info("Sent 3h reminder for appointment {} to user {}", appointmentId, userId);
        } catch (Exception e) {
            log.error("Failed to send 3h reminder for appointment {}", appointmentId, e);
        }
    }
}
