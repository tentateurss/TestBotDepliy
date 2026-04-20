package lashes.bot.utils;

import lashes.bot.database.DatabaseService;
import lashes.bot.models.WorkDay;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public class CalendarBuilder {

    private static final Map<Integer, String> MONTH_NAMES = new HashMap<>();
    static {
        MONTH_NAMES.put(1, "Январь");
        MONTH_NAMES.put(2, "Февраль");
        MONTH_NAMES.put(3, "Март");
        MONTH_NAMES.put(4, "Апрель");
        MONTH_NAMES.put(5, "Май");
        MONTH_NAMES.put(6, "Июнь");
        MONTH_NAMES.put(7, "Июль");
        MONTH_NAMES.put(8, "Август");
        MONTH_NAMES.put(9, "Сентябрь");
        MONTH_NAMES.put(10, "Октябрь");
        MONTH_NAMES.put(11, "Ноябрь");
        MONTH_NAMES.put(12, "Декабрь");
    }

    public static String getMonthName(int month) {
        return MONTH_NAMES.getOrDefault(month, "");
    }

    public static List<CalendarDay> generateMonthDays(YearMonth yearMonth, LocalDate selectedDate) {
        List<CalendarDay> days = new ArrayList<>();

        LocalDate firstOfMonth = yearMonth.atDay(1);
        int firstDayOfWeek = firstOfMonth.getDayOfWeek().getValue();
        int daysInMonth = yearMonth.lengthOfMonth();

        for (int i = 1; i < firstDayOfWeek; i++) {
            days.add(CalendarDay.empty());
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = yearMonth.atDay(day);
            Optional<WorkDay> workDayOpt = DatabaseService.getWorkDay(date);
            boolean isWorking = workDayOpt.isPresent() && workDayOpt.get().isWorking();

            boolean hasSlots = false;
            if (isWorking) {
                List<String> slots = DatabaseService.getAvailableSlots(date);
                hasSlots = !slots.isEmpty();
            }

            boolean isSelected = date.equals(selectedDate);
            boolean isPast = date.isBefore(LocalDate.now());

            days.add(new CalendarDay(day, date, isWorking, hasSlots, isSelected, isPast));
        }

        return days;
    }

    public static class CalendarDay {
        private final Integer dayNumber;
        private final LocalDate date;
        private final boolean isEmpty;
        private final boolean isWorking;
        private final boolean hasAvailableSlots;
        private final boolean isSelected;
        private final boolean isPast;

        private CalendarDay() {
            this.dayNumber = null;
            this.date = null;
            this.isEmpty = true;
            this.isWorking = false;
            this.hasAvailableSlots = false;
            this.isSelected = false;
            this.isPast = false;
        }

        public CalendarDay(int dayNumber, LocalDate date, boolean isWorking,
                           boolean hasAvailableSlots, boolean isSelected, boolean isPast) {
            this.dayNumber = dayNumber;
            this.date = date;
            this.isEmpty = false;
            this.isWorking = isWorking;
            this.hasAvailableSlots = hasAvailableSlots;
            this.isSelected = isSelected;
            this.isPast = isPast;
        }

        public static CalendarDay empty() {
            return new CalendarDay();
        }

        public Integer getDayNumber() { return dayNumber; }
        public LocalDate getDate() { return date; }
        public boolean isEmpty() { return isEmpty; }
        public boolean isWorking() { return isWorking; }
        public boolean hasAvailableSlots() { return hasAvailableSlots; }
        public boolean isSelected() { return isSelected; }
        public boolean isPast() { return isPast; }

        public String getDisplayText() {
            if (isEmpty) return " ";
            StringBuilder sb = new StringBuilder();
            if (isSelected) sb.append("✅ ");
            sb.append(dayNumber);
            if (!isPast && isWorking) {
                sb.append(hasAvailableSlots ? " 🟢" : " 🔴");
            } else if (isPast) {
                sb.append(" ⚪");
            } else if (!isWorking) {
                sb.append(" ⚫");
            }
            return sb.toString();
        }

        public boolean isSelectable() {
            return !isEmpty && !isPast && isWorking && hasAvailableSlots;
        }
    }
}