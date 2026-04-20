package lashes.bot.models;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Data

public class Appointment {
    private Long id;
    private Long userId;
    private String userName;
    private String phone;
    private LocalDateTime appointmentTime;
    private String status;
    private LocalDateTime createdAt;
    private String reminderJobKey;
    private String reminder3hJobKey;
    private String telegramUsername; // @username из Telegram (может быть null)
    private Double price;
    private Integer completed; // 0 - не завершена, 1 - завершена
    private String serviceName; // Название выбранной процедуры
    private Double prepaymentAmount; // Сумма предоплаты (50% от цены)
    private String prepaymentScreenshotId; // ID фото скриншота предоплаты
    private Boolean prepaymentConfirmed; // Подтверждена ли предоплата админом

    public Appointment() {}

    public Appointment(Long userId, String userName, String phone, LocalDateTime appointmentTime) {
        this.userId = userId;
        this.userName = userName;
        this.phone = phone;
        this.appointmentTime = appointmentTime;
        this.status = "active";
        this.createdAt = LocalDateTime.now();
        this.completed = 0;
        this.prepaymentConfirmed = false;
    }
}
