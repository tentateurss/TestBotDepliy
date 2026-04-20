package lashes.bot.models;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class Review {
    private Long id;
    private Long appointmentId;
    private Long userId;
    private String userName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;

    public Review() {}

    public Review(Long appointmentId, Long userId, String userName, Integer rating, String comment) {
        this.appointmentId = appointmentId;
        this.userId = userId;
        this.userName = userName;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = LocalDateTime.now();
    }
}
