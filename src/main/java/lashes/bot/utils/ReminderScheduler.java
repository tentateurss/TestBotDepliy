package lashes.bot.utils;

import lashes.LashesBot;
import lashes.bot.config.BotConfig;
import lashes.bot.database.DatabaseService;
import lashes.bot.models.Appointment;
import lashes.bot.scheduler.AdminReminderJob;
import lashes.bot.scheduler.AppointmentCleanupJob;
import lashes.bot.scheduler.Reminder3hJob;
import lashes.bot.scheduler.ReminderJob;
import lashes.bot.scheduler.ReviewRequestJob;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static Scheduler scheduler;
    private static LashesBot botInstance;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static void init(LashesBot bot) throws SchedulerException {
        botInstance = bot;
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
        
        ZoneId timezone = BotConfig.getTimezone();
        log.info("ReminderScheduler initialized with timezone: {}", timezone);
        log.info("Current time in {}: {}", timezone, ZonedDateTime.now(timezone).format(TIME_FORMATTER));
        
        restoreReminders();
    }

    private static void restoreReminders() {
        List<Appointment> appointments = DatabaseService.getAllActiveAppointments();
        ZoneId timezone = BotConfig.getTimezone();
        LocalDateTime now = LocalDateTime.now(timezone);
        
        log.info("Restoring reminders for {} active appointments", appointments.size());

        for (Appointment appt : appointments) {
            // Восстанавливаем напоминание за 24 часа
            LocalDateTime reminderTime24h = appt.getAppointmentTime().minusHours(24);
            if (reminderTime24h.isAfter(now)) {
                if (appt.getReminderJobKey() == null || appt.getReminderJobKey().isEmpty()) {
                    try {
                        String jobKey = scheduleReminder(appt);
                        DatabaseService.updateReminderJobKey(appt.getId(), jobKey);
                    } catch (SchedulerException e) {
                        log.error("Failed to restore 24h reminder for appointment {}", appt.getId(), e);
                    }
                }
            }

            // Восстанавливаем напоминание за 3 часа
            LocalDateTime reminderTime3h = appt.getAppointmentTime().minusHours(3);
            if (reminderTime3h.isAfter(now)) {
                if (appt.getReminder3hJobKey() == null || appt.getReminder3hJobKey().isEmpty()) {
                    try {
                        String jobKey = schedule3hReminder(appt);
                        DatabaseService.updateReminder3hJobKey(appt.getId(), jobKey);
                    } catch (SchedulerException e) {
                        log.error("Failed to restore 3h reminder for appointment {}", appt.getId(), e);
                    }
                }
            }
        }
    }

    public static String scheduleReminder(Appointment appointment) throws SchedulerException {
        ZoneId timezone = BotConfig.getTimezone();
        LocalDateTime reminderTime = appointment.getAppointmentTime().minusHours(24);
        LocalDateTime now = LocalDateTime.now(timezone);

        if (reminderTime.isBefore(now)) {
            log.info("Skipping 24h reminder for appointment {} (less than 24h)", appointment.getId());
            return null;
        }

        String jobId = "reminder_24h_" + appointment.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        JobDetail job = JobBuilder.newJob(ReminderJob.class)
                .withIdentity(jobId, "reminders")
                .usingJobData("appointmentId", appointment.getId())
                .usingJobData("userId", appointment.getUserId())
                .usingJobData("userName", appointment.getUserName())
                .usingJobData("appointmentTime", appointment.getAppointmentTime().toString())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobId + "_trigger", "reminders")
                .startAt(Date.from(reminderTime.atZone(timezone).toInstant()))
                .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled 24h reminder for appointment {} at {} (timezone: {})", 
                appointment.getId(), reminderTime.format(TIME_FORMATTER), timezone);
        return jobId;
    }

    public static String schedule3hReminder(Appointment appointment) throws SchedulerException {
        ZoneId timezone = BotConfig.getTimezone();
        LocalDateTime reminderTime = appointment.getAppointmentTime().minusHours(3);
        LocalDateTime now = LocalDateTime.now(timezone);

        if (reminderTime.isBefore(now)) {
            log.info("Skipping 3h reminder for appointment {} (less than 3h)", appointment.getId());
            return null;
        }

        String jobId = "reminder_3h_" + appointment.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        JobDetail job = JobBuilder.newJob(Reminder3hJob.class)
                .withIdentity(jobId, "reminders")
                .usingJobData("appointmentId", appointment.getId())
                .usingJobData("userId", appointment.getUserId())
                .usingJobData("userName", appointment.getUserName())
                .usingJobData("appointmentTime", appointment.getAppointmentTime().toString())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobId + "_trigger", "reminders")
                .startAt(Date.from(reminderTime.atZone(timezone).toInstant()))
                .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled 3h reminder for appointment {} at {} (timezone: {})", 
                appointment.getId(), reminderTime.format(TIME_FORMATTER), timezone);
        return jobId;
    }

    public static void scheduleReviewRequest(Appointment appointment) throws SchedulerException {
        ZoneId timezone = BotConfig.getTimezone();
        LocalDateTime reviewTime = appointment.getAppointmentTime().plusMinutes(30);
        LocalDateTime now = LocalDateTime.now(timezone);

        if (reviewTime.isBefore(now)) {
            log.info("Skipping review request for appointment {} (time passed)", appointment.getId());
            return;
        }

        String jobId = "review_" + appointment.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        JobDetail job = JobBuilder.newJob(ReviewRequestJob.class)
                .withIdentity(jobId, "reviews")
                .usingJobData("appointmentId", appointment.getId())
                .usingJobData("userId", appointment.getUserId())
                .usingJobData("userName", appointment.getUserName())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobId + "_trigger", "reviews")
                .startAt(Date.from(reviewTime.atZone(timezone).toInstant()))
                .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled review request for appointment {} at {} (timezone: {})", 
                appointment.getId(), reviewTime.format(TIME_FORMATTER), timezone);
    }

    public static void scheduleAppointmentCleanup(Appointment appointment) throws SchedulerException {
        ZoneId timezone = BotConfig.getTimezone();
        LocalDateTime cleanupTime = appointment.getAppointmentTime().plusHours(2);
        LocalDateTime now = LocalDateTime.now(timezone);

        if (cleanupTime.isBefore(now)) {
            log.info("Skipping cleanup for appointment {} (time passed)", appointment.getId());
            return;
        }

        String jobId = "cleanup_" + appointment.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        JobDetail job = JobBuilder.newJob(AppointmentCleanupJob.class)
                .withIdentity(jobId, "cleanup")
                .usingJobData("appointmentId", appointment.getId())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobId + "_trigger", "cleanup")
                .startAt(Date.from(cleanupTime.atZone(timezone).toInstant()))
                .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled cleanup for appointment {} at {} (timezone: {})", 
                appointment.getId(), cleanupTime.format(TIME_FORMATTER), timezone);
    }

    public static void scheduleAdminReminder(Appointment appointment) throws SchedulerException {
        ZoneId timezone = BotConfig.getTimezone();
        LocalDateTime reminderTime = appointment.getAppointmentTime().minusHours(2);
        LocalDateTime now = LocalDateTime.now(timezone);

        if (reminderTime.isBefore(now)) {
            log.info("Skipping admin 2h reminder for appointment {} (less than 2h)", appointment.getId());
            return;
        }

        String jobId = "admin_reminder_2h_" + appointment.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        JobDetail job = JobBuilder.newJob(AdminReminderJob.class)
                .withIdentity(jobId, "admin_reminders")
                .usingJobData("appointmentId", appointment.getId())
                .usingJobData("userName", appointment.getUserName())
                .usingJobData("phone", appointment.getPhone())
                .usingJobData("serviceName", appointment.getServiceName())
                .usingJobData("appointmentTime", appointment.getAppointmentTime().toString())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobId + "_trigger", "admin_reminders")
                .startAt(Date.from(reminderTime.atZone(timezone).toInstant()))
                .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled admin 2h reminder for appointment {} at {} (timezone: {})", 
                appointment.getId(), reminderTime.format(TIME_FORMATTER), timezone);
    }

    public static void cancelReminder(String jobKey) {
        if (jobKey == null) return;
        try {
            scheduler.deleteJob(new JobKey(jobKey, "reminders"));
            log.info("Cancelled reminder: {}", jobKey);
        } catch (SchedulerException e) {
            log.error("Failed to cancel reminder: {}", jobKey, e);
        }
    }

    public static LashesBot getBot() {
        return botInstance;
    }

    public static void shutdown() {
        try {
            if (scheduler != null) {
                scheduler.shutdown();
            }
        } catch (SchedulerException e) {
            log.error("Failed to shutdown scheduler", e);
        }
    }
}
