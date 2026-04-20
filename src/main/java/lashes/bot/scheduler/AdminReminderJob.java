package lashes.bot.scheduler;

import lashes.bot.config.BotConfig;
import lashes.bot.utils.ReminderScheduler;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AdminReminderJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(AdminReminderJob.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getJobDetail().getJobDataMap();

        Long appointmentId = dataMap.getLong("appointmentId");
        String userName = dataMap.getString("userName");
        String phone = dataMap.getString("phone");
        String serviceName = dataMap.getString("serviceName");
        String appointmentTimeStr = dataMap.getString("appointmentTime");

        LocalDateTime appointmentTime = LocalDateTime.parse(appointmentTimeStr);
        String timeStr = appointmentTime.format(TIME_FORMATTER);
        String dateStr = appointmentTime.format(DATE_FORMATTER);

        String messageText = String.format(
                "⏰ *НАПОМИНАНИЕ*\n\n" +
                "Через 2 часа к вам придёт клиент:\n\n" +
                "👤 Имя: %s\n" +
                "📱 Телефон: %s\n" +
                "💎 Процедура: %s\n" +
                "📅 Дата: %s\n" +
                "⏰ Время: %s",
                userName, phone, serviceName, dateStr, timeStr
        );

        // Отправляем всем администраторам
        for (Long adminId : BotConfig.getAdminIds()) {
            SendMessage message = new SendMessage();
            message.setChatId(adminId.toString());
            message.setText(messageText);
            message.setParseMode("Markdown");

            try {
                ReminderScheduler.getBot().execute(message);
                log.info("Sent admin reminder for appointment {} to admin {}", appointmentId, adminId);
            } catch (Exception e) {
                log.error("Failed to send admin reminder for appointment {} to admin {}", appointmentId, adminId, e);
            }
        }
    }
}
