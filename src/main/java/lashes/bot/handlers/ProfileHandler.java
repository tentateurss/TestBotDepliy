package lashes.bot.handlers;

import lashes.LashesBot;
import lashes.bot.database.DatabaseService;
import lashes.bot.keyboards.InlineKeyboardFactory;
import lashes.bot.models.UserProfile;
import lashes.bot.states.BotStateManager;
import lashes.bot.states.UserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Optional;

public class ProfileHandler {
    private static final Logger log = LoggerFactory.getLogger(ProfileHandler.class);

    // ============ ПРОСМОТР ПРОФИЛЯ ============

    public void handleViewProfile(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        log.info("=== ПРОСМОТР ПРОФИЛЯ ===");
        log.info("User ID: {}", userId);

        Optional<UserProfile> profileOpt = DatabaseService.getUserProfile(userId);

        if (profileOpt.isEmpty()) {
            bot.sendMessage(chatId, 
                "📋 Ваш профиль\n\n" +
                "У вас ещё нет профиля.\n\n" +
                "Профиль будет создан автоматически при первой записи.");
            return;
        }

        UserProfile profile = profileOpt.get();
        StringBuilder text = new StringBuilder();
        text.append("📋 *Ваш профиль*\n\n");
        
        String name = profile.getName() != null ? profile.getName() : "не указано";
        String phone = profile.getPhone() != null ? profile.getPhone() : "не указан";
        
        text.append("👤 Имя: ").append(name).append("\n");
        text.append("📱 Телефон: ").append(phone).append("\n");
        
        if (profile.getAdditionalInfo() != null && !profile.getAdditionalInfo().isEmpty()) {
            text.append("📝 Комментарий: ").append(profile.getAdditionalInfo()).append("\n");
        }

        // История записей
        List<lashes.bot.models.Appointment> appointments = DatabaseService.getAllAppointmentsByUser(userId);
        if (!appointments.isEmpty()) {
            text.append("\n📅 *История записей:*\n");
            text.append("Всего записей: ").append(appointments.size()).append("\n\n");
            
            // Показываем последние 5 записей
            int count = Math.min(appointments.size(), 5);
            for (int i = appointments.size() - 1; i >= appointments.size() - count; i--) {
                lashes.bot.models.Appointment appt = appointments.get(i);
                String dateStr = appt.getAppointmentTime().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                String timeStr = appt.getAppointmentTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                String statusEmoji = "active".equals(appt.getStatus()) ? "✅" : 
                                   "cancelled".equals(appt.getStatus()) ? "❌" : "✔️";
                String service = appt.getServiceName() != null ? appt.getServiceName() : "Не указана";
                
                text.append(statusEmoji).append(" ").append(dateStr).append(" в ").append(timeStr)
                    .append("\n   💎 ").append(service).append("\n");
            }
            
            if (appointments.size() > 5) {
                text.append("\n...и ещё ").append(appointments.size() - 5).append(" записей\n");
            }
        } else {
            text.append("\n📅 История записей пуста\n");
        }

        bot.sendMessage(chatId, text.toString(), "Markdown", 
            lashes.bot.keyboards.InlineKeyboardFactory.getProfileKeyboard());
    }
    
    // Метод для экранирования специальных символов Markdown (не используется пока)
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace(">", "\\>")
                   .replace("#", "\\#")
                   .replace("+", "\\+")
                   .replace("-", "\\-")
                   .replace("=", "\\=")
                   .replace("|", "\\|")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace(".", "\\.")
                   .replace("!", "\\!");
    }

    // ============ РЕДАКТИРОВАНИЕ ПРОФИЛЯ ============

    public void handleEditProfile(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();

        log.info("=== РЕДАКТИРОВАНИЕ ПРОФИЛЯ ===");
        log.info("User ID: {}", userId);

        BotStateManager.setState(chatId, UserState.PROFILE_EDIT_NAME);
        bot.sendMessage(chatId, 
            "✏️ *Редактирование профиля*\n\n" +
            "👤 Введите ваше имя:",
            "Markdown");
    }

    public void handleEditProfileCallback(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        
        BotStateManager.setState(chatId, UserState.PROFILE_EDIT_NAME);
        bot.editMessageText(
            "✏️ *Редактирование профиля*\n\n" +
            "👤 Введите ваше имя:",
            chatId, 
            callback.getMessage().getMessageId(),
            "Markdown");
    }

    // ============ ВВОД ИМЕНИ ============

    public void handleProfileName(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String name = msg.getText().trim();

        log.info("=== ВВОД ИМЕНИ ПРОФИЛЯ ===");
        log.info("Name: {}", name);

        if (name.length() < 2) {
            bot.sendMessage(chatId, "❌ Имя должно содержать минимум 2 символа. Попробуйте ещё раз:");
            return;
        }

        BotStateManager.setUserName(chatId, name);
        BotStateManager.setState(chatId, UserState.PROFILE_EDIT_PHONE);
        
        bot.sendMessage(chatId, 
            "📱 *Введите номер телефона:*\n\n" +
            "В формате: +7XXXXXXXXXX или 8XXXXXXXXXX",
            "Markdown");
    }

    // ============ ВВОД ТЕЛЕФОНА ============

    public void handleProfilePhone(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String phone = msg.getText().trim();

        log.info("=== ВВОД ТЕЛЕФОНА ПРОФИЛЯ ===");
        log.info("Phone: {}", phone);

        String cleaned = phone.replaceAll("[^0-9+]", "");
        if (cleaned.length() < 10 || cleaned.length() > 12) {
            bot.sendMessage(chatId, 
                "❌ Некорректный номер. Попробуйте ещё раз:\n" +
                "Формат: +7XXXXXXXXXX или 8XXXXXXXXXX");
            return;
        }

        BotStateManager.setPhone(chatId, phone);
        BotStateManager.setState(chatId, UserState.PROFILE_EDIT_ADDITIONAL);
        
        bot.sendMessage(chatId, 
            "📝 *Дополнительная информация (необязательно):*\n\n" +
            "Например: аллергии, предпочтения и т.д.\n\n" +
            "Или отправьте \"-\" чтобы пропустить.",
            "Markdown");
    }

    // ============ ВВОД ДОПОЛНИТЕЛЬНОЙ ИНФОРМАЦИИ ============

    public void handleProfileAdditional(Message msg, LashesBot bot) {
        Long chatId = msg.getChatId();
        Long userId = msg.getFrom().getId();
        String additionalInfo = msg.getText().trim();

        log.info("=== ВВОД ДОП. ИНФОРМАЦИИ ===");

        if (additionalInfo.equals("-")) {
            additionalInfo = null;
        }

        String name = BotStateManager.getUserName(chatId);
        String phone = BotStateManager.getPhone(chatId);

        if (name == null || phone == null) {
            bot.sendMessage(chatId, "❌ Ошибка: данные потеряны. Начните заново с /edit_profile");
            BotStateManager.clear(chatId);
            return;
        }

        // Сохраняем или обновляем профиль
        UserProfile profile = new UserProfile(userId, name, phone);
        profile.setAdditionalInfo(additionalInfo);
        
        // Если профиль уже существует, сохраняем историю кодов
        Optional<UserProfile> existingProfile = DatabaseService.getUserProfile(userId);
        if (existingProfile.isPresent()) {
            profile.setCreatedAt(existingProfile.get().getCreatedAt());
            profile.setBookingCodes(existingProfile.get().getBookingCodes());
        }

        DatabaseService.saveOrUpdateProfile(profile);

        BotStateManager.clear(chatId);

        bot.sendMessage(chatId, 
            "✅ Профиль сохранён!\n\n" +
            "👤 Имя: " + name + "\n" +
            "📱 Телефон: " + phone + "\n" +
            (additionalInfo != null ? "📝 Доп. информация: " + additionalInfo : ""));

        log.info("Profile saved for user {}", userId);
    }

    // ============ УДАЛЕНИЕ ПРОФИЛЯ ============

    public void handleDeleteProfileRequest(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();

        log.info("=== ЗАПРОС НА УДАЛЕНИЕ ПРОФИЛЯ ===");
        log.info("User ID: {}", userId);

        Optional<UserProfile> profileOpt = DatabaseService.getUserProfile(userId);
        
        if (profileOpt.isEmpty()) {
        bot.editMessageText(
            "❌ Профиль не найден.",
            chatId,
            callback.getMessage().getMessageId());
            bot.answerCallback(callback.getId());
            return;
        }

        bot.editMessageText(
            "⚠️ Удаление профиля\n\n" +
            "Вы уверены, что хотите удалить свой профиль?\n\n" +
            "⚠️ Это действие нельзя отменить!\n" +
            "Будут удалены:\n" +
            "• Ваше имя и телефон\n" +
            "• Дополнительная информация\n" +
            "• История кодов записи\n\n" +
            "Активные записи НЕ будут отменены.",
            chatId,
            callback.getMessage().getMessageId());
        
        bot.editMessageReplyMarkup(
            InlineKeyboardFactory.getDeleteProfileConfirmationKeyboard(),
            chatId,
            callback.getMessage().getMessageId());
    }

    public void handleConfirmDeleteProfile(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();

        log.info("=== ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ ПРОФИЛЯ ===");
        log.info("User ID: {}", userId);

        DatabaseService.deleteUserProfile(userId);

        bot.editMessageText(
            "✅ Профиль удалён\n\n" +
            "Ваш профиль успешно удалён.\n\n" +
            "Вы можете создать новый профиль в любое время через /edit_profile",
            chatId,
            callback.getMessage().getMessageId());
        
        log.info("Profile deleted for user {}", userId);
    }

    public void handleCancelDeleteProfile(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        
        bot.editMessageText(
            "❌ Удаление отменено.\n\n" +
            "Ваш профиль сохранён.",
            chatId,
            callback.getMessage().getMessageId());
    }

    // ============ УДАЛЕНИЕ ИСТОРИИ ЗАПИСЕЙ ============

    public void handleDeleteAppointmentHistoryRequest(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();

        log.info("=== ЗАПРОС НА УДАЛЕНИЕ ИСТОРИИ ЗАПИСЕЙ ===");
        log.info("User ID: {}", userId);

        List<lashes.bot.models.Appointment> appointments = DatabaseService.getAllAppointmentsByUser(userId);
        long historyCount = appointments.stream()
            .filter(a -> !"active".equals(a.getStatus()))
            .count();

        if (historyCount == 0) {
            bot.editMessageText(
                "📋 История записей пуста.\n\n" +
                "Нет записей для удаления.",
                chatId,
                callback.getMessage().getMessageId());
            bot.answerCallback(callback.getId());
            return;
        }

        bot.editMessageText(
            "⚠️ Удаление истории записей\n\n" +
            "Вы уверены, что хотите удалить историю записей?\n\n" +
            "⚠️ Это действие нельзя отменить!\n" +
            "Будет удалено записей: " + historyCount + "\n\n" +
            "Активные записи НЕ будут удалены.",
            chatId,
            callback.getMessage().getMessageId());
        
        bot.editMessageReplyMarkup(
            lashes.bot.keyboards.InlineKeyboardFactory.getDeleteHistoryConfirmationKeyboard(),
            chatId,
            callback.getMessage().getMessageId());
    }

    public void handleConfirmDeleteAppointmentHistory(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        Long userId = callback.getFrom().getId();

        log.info("=== ПОДТВЕРЖДЕНИЕ УДАЛЕНИЯ ИСТОРИИ ЗАПИСЕЙ ===");
        log.info("User ID: {}", userId);

        DatabaseService.deleteAppointmentHistory(userId);

        bot.editMessageText(
            "✅ История записей удалена\n\n" +
            "Все завершённые и отменённые записи удалены.\n" +
            "Активные записи сохранены.",
            chatId,
            callback.getMessage().getMessageId());
        
        log.info("Appointment history deleted for user {}", userId);
    }

    public void handleCancelDeleteAppointmentHistory(CallbackQuery callback, LashesBot bot) {
        Long chatId = callback.getMessage().getChatId();
        
        bot.editMessageText(
            "❌ Удаление отменено.\n\n" +
            "История записей сохранена.",
            chatId,
            callback.getMessage().getMessageId());
    }
}
