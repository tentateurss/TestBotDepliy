package lashes.bot.models;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Data
public class UserProfile {
    private Long userId;
    private String name;
    private String phone;
    private String additionalInfo; // Дополнительная информация (аллергии, предпочтения и т.д.)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> bookingCodes; // История кодов записи

    public UserProfile() {
        this.bookingCodes = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public UserProfile(Long userId, String name, String phone) {
        this();
        this.userId = userId;
        this.name = name;
        this.phone = phone;
    }

    public void addBookingCode(String code) {
        if (this.bookingCodes == null) {
            this.bookingCodes = new ArrayList<>();
        }
        this.bookingCodes.add(code);
        this.updatedAt = LocalDateTime.now();
    }
}
