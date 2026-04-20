package lashes.bot.states;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BotStateManager {
    private static final Map<Long, UserState> userStates = new ConcurrentHashMap<>();
    private static final Map<Long, Map<String, Object>> userData = new ConcurrentHashMap<>();

    public static final String KEY_SELECTED_DATE = "selectedDate";
    public static final String KEY_APPOINTMENT_TIME = "appointmentTime";
    public static final String KEY_USER_NAME = "userName";
    public static final String KEY_PHONE = "phone";
    public static final String KEY_CANCEL_APPOINTMENT_ID = "cancelAppointmentId";
    public static final String KEY_ADMIN_DATE = "admin_date";
    public static final String KEY_ADMIN_SLOTS = "admin_slots";
    public static final String KEY_ADMIN_APPOINTMENTS = "admin_appointments";
    public static final String KEY_SERVICE_NAME = "serviceName";
    public static final String KEY_SERVICE_PRICE = "servicePrice";
    public static final String KEY_PENDING_APPOINTMENT_ID = "pendingAppointmentId";

    public static void setState(Long chatId, UserState state) {
        userStates.put(chatId, state);
    }

    public static UserState getState(Long chatId) {
        return userStates.getOrDefault(chatId, UserState.IDLE);
    }

    public static void setData(Long chatId, String key, Object value) {
        userData.computeIfAbsent(chatId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getData(Long chatId, String key, Class<T> type) {
        Map<String, Object> data = userData.get(chatId);
        if (data == null) return null;
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // Типизированные методы
    public static LocalDate getSelectedDate(Long chatId) {
        return getData(chatId, KEY_SELECTED_DATE, LocalDate.class);
    }

    public static void setSelectedDate(Long chatId, LocalDate date) {
        setData(chatId, KEY_SELECTED_DATE, date);
    }

    public static LocalDateTime getAppointmentTime(Long chatId) {
        return getData(chatId, KEY_APPOINTMENT_TIME, LocalDateTime.class);
    }

    public static void setAppointmentTime(Long chatId, LocalDateTime time) {
        setData(chatId, KEY_APPOINTMENT_TIME, time);
    }

    public static String getUserName(Long chatId) {
        return getData(chatId, KEY_USER_NAME, String.class);
    }

    public static void setUserName(Long chatId, String name) {
        setData(chatId, KEY_USER_NAME, name);
    }

    public static String getPhone(Long chatId) {
        return getData(chatId, KEY_PHONE, String.class);
    }

    public static void setPhone(Long chatId, String phone) {
        setData(chatId, KEY_PHONE, phone);
    }

    public static Long getCancelAppointmentId(Long chatId) {
        return getData(chatId, KEY_CANCEL_APPOINTMENT_ID, Long.class);
    }

    public static void setCancelAppointmentId(Long chatId, Long id) {
        setData(chatId, KEY_CANCEL_APPOINTMENT_ID, id);
    }

    public static String getServiceName(Long chatId) {
        return getData(chatId, KEY_SERVICE_NAME, String.class);
    }

    public static void setServiceName(Long chatId, String serviceName) {
        setData(chatId, KEY_SERVICE_NAME, serviceName);
    }

    public static Double getServicePrice(Long chatId) {
        return getData(chatId, KEY_SERVICE_PRICE, Double.class);
    }

    public static void setServicePrice(Long chatId, Double price) {
        setData(chatId, KEY_SERVICE_PRICE, price);
    }

    public static Long getPendingAppointmentId(Long chatId) {
        return getData(chatId, KEY_PENDING_APPOINTMENT_ID, Long.class);
    }

    public static void setPendingAppointmentId(Long chatId, Long id) {
        setData(chatId, KEY_PENDING_APPOINTMENT_ID, id);
    }

    public static void clearData(Long chatId) {
        Map<String, Object> data = userData.get(chatId);
        if (data != null) {
            data.clear();
        }
    }

    public static void clear(Long chatId) {
        userStates.remove(chatId);
        userData.remove(chatId);
    }
}