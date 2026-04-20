package lashes.bot.models;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class Service {
    private Long id;
    private String name;
    private Double price;
    private String category; // "наращивание", "коррекция", "снятие", "дополнительно"
    private Integer displayOrder;
    private Boolean active;

    public Service() {
        this.active = true;
    }

    public Service(String name, Double price, String category, Integer displayOrder) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.displayOrder = displayOrder;
        this.active = true;
    }
}
