package lashes.bot.models;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Модель для черного списка клиентов
 */
@Getter
@Setter
@Data
public class BlacklistEntry {
    private Long id;
    private Long userId;
    private String userName;
    private String phone;
    private String reason;
    private LocalDateTime createdAt;
    private Long createdBy; // ID админа, который добавил в черный список
    
    public BlacklistEntry() {}
    
    public BlacklistEntry(Long userId, String userName, String phone, String reason, Long createdBy) {
        this.userId = userId;
        this.userName = userName;
        this.phone = phone;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }
}
