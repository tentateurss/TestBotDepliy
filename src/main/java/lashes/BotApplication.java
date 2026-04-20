package lashes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class BotApplication {
    private static final Logger log = LoggerFactory.getLogger(BotApplication.class);


    public static void main(String[] args) {
        SpringApplication.run(BotApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            LashesBot bot = new LashesBot();
            botsApi.registerBot(bot);
            bot.checkBotAdminStatus();
            log.info("✅ Telegram бот запущен успешно!");
            log.info("✅ REST API доступен на http://localhost:8080");
        } catch (Exception e) {
            log.error("❌ Ошибка запуска бота", e);
        }
    }
}
