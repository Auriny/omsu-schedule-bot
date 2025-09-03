package ru.auriny.omsuschedulebot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import ru.auriny.omsuschedulebot.model.TelegramUser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class KeyboardService {
    public ReplyKeyboardMarkup createMainMenuKeyboard() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setSelective(true);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = List.of(
                new KeyboardRow(List.of(new KeyboardButton("⏰ Сегодня"), new KeyboardButton("📅 Завтра"))),
                new KeyboardRow(List.of(new KeyboardButton("️🗓️ Неделя"), new KeyboardButton("⚙️ Настройки")))
        );

        markup.setKeyboard(keyboard);

        return markup;
    }

    public InlineKeyboardMarkup createWeekNavigationKeyboard(LocalDate weekStartDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton prev = new InlineKeyboardButton("⬅️ Пред. неделя");
        prev.setCallbackData("week_nav:" + weekStartDate.minusWeeks(1).format(formatter));

        InlineKeyboardButton refresh = new InlineKeyboardButton("🔄 Обновить");
        refresh.setCallbackData("week_nav:" + weekStartDate.format(formatter));

        InlineKeyboardButton next = new InlineKeyboardButton("След. неделя ➡️");
        next.setCallbackData("week_nav:" + weekStartDate.plusWeeks(1).format(formatter));

        markup.setKeyboard(List.of(List.of(prev, refresh, next)));
        return markup;
    }

    public InlineKeyboardMarkup createDayNavigationKeyboard(LocalDate currentDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton prev = new InlineKeyboardButton("⬅️ Пред. день");
        prev.setCallbackData("day_nav:" + currentDate.minusDays(1).format(formatter));

        InlineKeyboardButton refresh = new InlineKeyboardButton("🔄 Обновить");
        refresh.setCallbackData("day_nav:" + currentDate.format(formatter));

        InlineKeyboardButton next = new InlineKeyboardButton("Завтра ➡️");
        next.setCallbackData("day_nav:" + currentDate.plusDays(1).format(formatter));

        markup.setKeyboard(List.of(List.of(prev, refresh, next)));
        return markup;
    }

    public InlineKeyboardMarkup createSettingsMenu(TelegramUser user) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        String dailyText = (user.isDailyNotificationEnabled() ? "✅ Ежедневные (в " + user.getNotificationTime() + ")" : "❌ Ежедневные");
        InlineKeyboardButton dailyButton = new InlineKeyboardButton(dailyText);
        dailyButton.setCallbackData("toggle_daily");

        String updateText = (user.isUpdateNotificationEnabled() ? "✅ Об изменениях" : "❌ Об изменениях");
        InlineKeyboardButton updateButton = new InlineKeyboardButton(updateText);
        updateButton.setCallbackData("toggle_updates");

        InlineKeyboardButton timeButton = new InlineKeyboardButton("⏰ Изменить время");
        timeButton.setCallbackData("change_time");

        markup.setKeyboard(List.of(
                List.of(dailyButton),
                List.of(updateButton),
                List.of(timeButton)
        ));

        return markup;
    }
}