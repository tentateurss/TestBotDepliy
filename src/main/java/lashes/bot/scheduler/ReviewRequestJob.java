package lashes.bot.scheduler;

import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.utils.ReminderScheduler;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class ReviewRequestJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ReviewRequestJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        var dataMap = context.getJobDetail().getJobDataMap();
        
        Long appointmentId = dataMap.getLong("appointmentId");
        Long userId = dataMap.getLong("userId");
        String userName = dataMap.getString("userName");

        // Проверяем, не оставил ли уже клиент отзыв
        if (DatabaseService.hasReviewForAppointment(appointmentId)) {
            log.info("Review already exists for appointment {}", appointmentId);
            return;
        }

        String messageText = String.format(
                "✨ *%s*, спасибо, что посетили нас!\n\n" +
                        "Будем рады, если вы оставите отзыв о процедуре. " +
                        "Ваше мнение очень важно для нас! 💖",
                userName
        );

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText(messageText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(InlineKeyboardFactory.createReviewKeyboard(appointmentId));

        try {
            ReminderScheduler.getBot().execute(message);
            log.info("Sent review request for appointment {} to user {}", appointmentId, userId);
        } catch (Exception e) {
            log.error("Failed to send review request for appointment {}", appointmentId, e);
        }
    }
}
