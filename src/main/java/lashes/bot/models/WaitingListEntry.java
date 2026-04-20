package lashes.bot.models;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class WaitingListEntry {
    private Long id;
    private Long userId;
    private String userName;
    private String phone;
    private LocalDate date;
    private String timeSlot;
    private LocalDateTime createdAt;

    public WaitingListEntry() {}

    public WaitingListEntry(Long userId, String userName, String phone, LocalDate date, String timeSlot) {
        this.userId = userId;
        this.userName = userName;
        this.phone = phone;
        this.date = date;
        this.timeSlot = timeSlot;
        this.createdAt = LocalDateTime.now();
    }
}
