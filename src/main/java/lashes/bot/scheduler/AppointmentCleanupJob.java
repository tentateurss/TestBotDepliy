package lashes.bot.scheduler;

import lashes.bot.database.DatabaseService;
import lashes.bot.utils.ReminderScheduler;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppointmentCleanupJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(AppointmentCleanupJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long appointmentId = context.getJobDetail().getJobDataMap().getLong("appointmentId");
        
        try {
            // Помечаем запись как завершённую (не отменяем, а завершаем)
            DatabaseService.completeAppointment(appointmentId);
            log.info("Auto-completed appointment {} (2 hours after start)", appointmentId);
        } catch (Exception e) {
            log.error("Failed to auto-complete appointment {}", appointmentId, e);
        }
    }
}
