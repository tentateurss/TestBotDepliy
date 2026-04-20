package lashes.bot.models;

import lombok.Data;
import java.time.LocalDate;

@Data
public class WorkDay {
    private Long id;
    private LocalDate date;
    private boolean working;
    private String slots;
}