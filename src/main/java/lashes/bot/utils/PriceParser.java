package lashes.bot.utils;

import lashes.bot.models.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceParser {
    private static final Logger log = LoggerFactory.getLogger(PriceParser.class);
    
    // Паттерн для извлечения услуги и цены: "• Название — 1500₽" или "• Название - 1500₽"
    // Поддерживает: длинное тире (—), короткое тире (–), дефис (-), и минус (−)
    // Ищет последнее число перед ₽ (нежадный квантификатор для случаев типа "скидка 200₽")
    private static final Pattern PRICE_PATTERN = Pattern.compile("•\\s*([^—–\\-−]+?)\\s*[—–\\-−]\\s*.*?(\\d+)\\s*₽");
    
    /**
     * Парсит текст прайса и извлекает услуги с ценами
     */
    public static List<Service> parsePrice(String priceText) {
        List<Service> services = new ArrayList<>();
        
        if (priceText == null || priceText.trim().isEmpty()) {
            log.warn("Empty price text provided");
            return services;
        }
        
        String[] lines = priceText.split("\n");
        String currentCategory = "общее";
        int displayOrder = 1;
        
        for (String line : lines) {
            line = line.trim();
            
            // Пропускаем пустые строки
            if (line.isEmpty()) {
                continue;
            }
            
            // Пропускаем строки без маркера • (это либо заголовки, либо пустые строки)
            if (!line.contains("•")) {
                // Определяем категорию по заголовкам (регистронезависимо)
                String lineLower = line.toLowerCase();
                if (lineLower.contains("наращивание")) {
                    currentCategory = "наращивание";
                } else if (lineLower.contains("коррекция")) {
                    currentCategory = "коррекция";
                } else if (lineLower.contains("снятие")) {
                    currentCategory = "снятие";
                } else if (lineLower.contains("дополнительно")) {
                    currentCategory = "дополнительно";
                }
                continue;
            }
            
            // Ищем услугу и цену
            Matcher matcher = PRICE_PATTERN.matcher(line);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                String priceStr = matcher.group(2).trim();
                
                try {
                    double price = Double.parseDouble(priceStr);
                    Service service = new Service(name, price, currentCategory, displayOrder++);
                    services.add(service);
                    log.info("Parsed service: {} - {}₽ ({})", name, price, currentCategory);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse price for: {}", line);
                }
            }
        }
        
        log.info("Total services parsed: {}", services.size());
        return services;
    }
    
    /**
     * Форматирует список услуг в текст прайса
     */
    public static String formatPrice(List<Service> services) {
        if (services.isEmpty()) {
            return "Прайс-лист пуст";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("💰 ПРАЙС-ЛИСТ\n\n");
        
        String currentCategory = "";
        
        for (Service service : services) {
            // Добавляем заголовок категории
            if (!service.getCategory().equals(currentCategory)) {
                currentCategory = service.getCategory();
                
                if (sb.length() > 20) { // Не первая категория
                    sb.append("\n");
                }
                
                switch (currentCategory) {
                    case "наращивание":
                        sb.append("👁 Наращивание ресниц:\n");
                        break;
                    case "коррекция":
                        sb.append("🔄 Коррекция:\n");
                        break;
                    case "снятие":
                        sb.append("🧼 Снятие ресниц:\n");
                        break;
                    case "дополнительно":
                        sb.append("✨ Дополнительно:\n");
                        break;
                    default:
                        sb.append("📋 Услуги:\n");
                }
            }
            
            // Добавляем услугу
            sb.append(String.format("• %s — %.0f₽\n", service.getName(), service.getPrice()));
        }
        
        return sb.toString();
    }
}
