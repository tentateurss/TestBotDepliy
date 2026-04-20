package lashes.bot.models;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalTime;

@Getter
@Setter
public class TimeSlot {
    private LocalTime time;
    private boolean available;

    public TimeSlot() {}

    public TimeSlot(LocalTime time, boolean available) {
        this.time = time;
        this.available = available;
    }

    public TimeSlot(String timeStr, boolean available) {
        this.time = LocalTime.parse(timeStr);
        this.available = available;
    }

    public String getFormattedTime() {
        return time != null ? time.toString() : "";
    }
}