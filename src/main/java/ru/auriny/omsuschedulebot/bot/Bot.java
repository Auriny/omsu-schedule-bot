package ru.auriny.omsuschedulebot.bot;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.auriny.omsuschedulebot.model.TelegramUser;
import ru.auriny.omsuschedulebot.model.api.Group;
import ru.auriny.omsuschedulebot.repository.UserRepository;
import ru.auriny.omsuschedulebot.service.KeyboardService;
import ru.auriny.omsuschedulebot.service.NotificationService;
import ru.auriny.omsuschedulebot.service.OmsuApiService;
import ru.auriny.omsuschedulebot.service.ScheduleService;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Bot extends TelegramLongPollingBot {
    @Value("${telegram.bot.username}") private String botUsername;

    private final Map<Long, Boolean> userAwaitingTime = new ConcurrentHashMap<>();

    private final OmsuApiService omsuApiService;
    private final ScheduleService scheduleService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final KeyboardService keyboardService;

    public Bot(@Value("${telegram.bot.token}") String botToken, OmsuApiService omsuApiService, ScheduleService scheduleService, UserRepository userRepository, NotificationService notificationService, KeyboardService keyboardService) {
        super(botToken);

        this.omsuApiService = omsuApiService;
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.keyboardService = keyboardService;
    }

    @PostConstruct
    public void init() {
        notificationService.setScheduleBot(this);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        }
    }

    private void handleTextMessage(Message message) {
        long chatId = message.getChatId();
        String messageText = message.getText();
        TelegramUser user = userRepository.findById(chatId).orElseGet(() -> registerUser(message));

        if (messageText.startsWith("/")) {
            handleCommand(user, messageText);
            return;
        }

        if (TelegramUser.STATE_AWAITING_GROUP.equals(user.getUserState())) {
            handleGroupInput(user, messageText);
            return;
        }
        if (userAwaitingTime.getOrDefault(chatId, false)) {
            handleTimeInput(user, messageText);
            return;
        }

        String command = switch (messageText) {
            case "⏰ Сегодня" -> "/today";
            case "📅 Завтра" -> "/tomorrow";
            case "️🗓️ Неделя" -> "/week";
            case "⚙️ Настройки" -> "/settings";
            default -> messageText;
        };

        if (command.startsWith("/")) {
            handleCommand(user, command);
        } else sendMessage(chatId, "Неизвестная команда. Воспользуйтесь меню.");
    }

    private void handleGroupInput(TelegramUser user, String groupName) {
        List<Group> allGroups = omsuApiService.fetchAllGroups();
        Optional<Group> foundGroup = allGroups.stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName.trim()))
                .findFirst();

        if (foundGroup.isPresent()) {
            user.setGroupId(foundGroup.get().getId());
            user.setGroupName(foundGroup.get().getName());
            user.setUserState(TelegramUser.STATE_REGISTERED);
            userRepository.save(user);

            SendMessage msg = new SendMessage(String.valueOf(user.getChatId()),
                    "✅ *Отлично*! Твоя группа установлена: `" + user.getGroupName() + "`.\nТеперь можешь пользоваться командами из меню.");
            msg.setReplyMarkup(keyboardService.createMainMenuKeyboard());
            sendMessage(msg);
        } else sendMessage(user.getChatId(), "❌ *Группа не найдена*. Попробуй еще раз, например: `МПБ-501-О-03`");
    }

    private void handleCommand(TelegramUser user, String command) {
        long chatId = user.getChatId();

        if (command.equals("/start")) {
            if (user.getGroupId() == null) {
                sendMessage(chatId, """
                    *Приветствую! 🐾*
                    
                    Я помогаю студентам удобнее ориентироваться в расписании:
                    ・ Показываю расписание прямо из телеги
                    ・ Скидываю расписание на завтра с вечера
                    ・ Уведомляю об изменениях в расписании
                    
                    Чтобы я могла помогать и тебе, мне нужно знать твою группу.
                    *Пожалуйста, отправь мне её номер следующим сообщением.*
                    Например: `МПБ-501-О-03`
                    
                    *Список моих команд (доступны только после указания группы):*
                    ・ /today - расписание на сегодня
                    ・ /tomorrow - расписание на завтра
                    ・ /week - узнать расписание на неделю
                    ・ /setgroup - поменять группу
                    ・ /settings - настроить бота
                    """);
            } else {
                String welcomeBackText = String.format("""
                    *Мяу! С возвращением! 🐾*
                    
                    Я по-прежнему слежу за расписанием твоей группы: `%s`
                    Просто воспользуйся меню или командами ниже для навигации.
                    
                    *Список моих команд:*
                    ・ /today - расписание на сегодня
                    ・ /tomorrow - расписание на завтра
                    ・ /week - узнать расписание на неделю
                    ・ /setgroup - поменять группу
                    ・ /settings - настроить бота
                    """, user.getGroupName());

                SendMessage msg = new SendMessage(String.valueOf(chatId), welcomeBackText);
                msg.setReplyMarkup(keyboardService.createMainMenuKeyboard());
                sendMessage(msg);
            }

            keyboardService.createMainMenuKeyboard();
            return;
        }

        if (command.equals("/setgroup")) {
            user.setUserState(TelegramUser.STATE_AWAITING_GROUP);
            userRepository.save(user);
            sendMessage(chatId, "Хорошо, давай поменяем твою группу! Отправь мне новое название.");
            return;
        }

        if (user.getGroupId() == null) {
            sendMessage(chatId, "Сначала нужно установить твою группу. Отправь мне ее название!");
            return;
        }

        switch (command) {
            case "/settings" -> showSettings(chatId);
            case "/today" -> sendScheduleForDate(chatId, LocalDate.now());
            case "/tomorrow" -> sendScheduleForDate(chatId, LocalDate.now().plusDays(1));
            case "/week" -> showWeekSchedule(chatId, LocalDate.now());
        }
    }

    private void sendScheduleForDate(long chatId, LocalDate date) {
        userRepository.findById(chatId).ifPresent(user ->
                omsuApiService.fetchScheduleForGroup(user.getGroupId()).ifPresentOrElse(response -> {
                    String text = scheduleService.getFormattedScheduleForDate(response.getData(), date);
                    SendMessage message = new SendMessage(String.valueOf(chatId), text);
                    message.setReplyMarkup(keyboardService.createDayNavigationKeyboard(date));
                    sendMessage(message);
                }, () -> sendMessage(chatId, "Не могу получить расписание с eservice. Если проблема не уходит, обратись к разработчику бота - @auriny."))
        );
    }

    private void handleTimeInput(TelegramUser user, String text) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:m");
            LocalTime newTime = LocalTime.parse(text.trim(), formatter);
            String formattedTime = newTime.format(DateTimeFormatter.ofPattern("HH:mm"));

            user.setNotificationTime(formattedTime);
            userRepository.save(user);

            sendMessage(user.getChatId(), "✅ Время ежедневных оповещений изменено на `" + formattedTime + "`");
            userAwaitingTime.remove(user.getChatId());
            showSettings(user.getChatId());
        } catch (DateTimeParseException e) {
            sendMessage(user.getChatId(), "❌ *Я тебя не понимаю!* Введи время в формате `ЧЧ:ММ`, например: `21:30`.");
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        long messageId = callbackQuery.getMessage().getMessageId();
        String callbackData = callbackQuery.getData();

        if (callbackData.startsWith("week_nav:")) {
            LocalDate weekStartDate = LocalDate.parse(callbackData.split(":")[1], DateTimeFormatter.ISO_LOCAL_DATE);
            updateWeekSchedule(chatId, messageId, weekStartDate);
            return;
        }
        if (callbackData.startsWith("day_nav:")) {
            LocalDate date = LocalDate.parse(callbackData.split(":")[1], DateTimeFormatter.ISO_LOCAL_DATE);
            updateDaySchedule(chatId, messageId, date);
            return;
        }

        TelegramUser user = userRepository.findById(chatId).orElse(null);
        if (user == null) return;

        switch (callbackData) {
            case "toggle_daily" -> user.setDailyNotificationEnabled(!user.isDailyNotificationEnabled());
            case "toggle_updates" -> user.setUpdateNotificationEnabled(!user.isUpdateNotificationEnabled());
            case "change_time" -> {
                userAwaitingTime.put(chatId, true);
                sendMessage(chatId, "⏰ Теперь введи новое время для оповещений в формате `ЧЧ:ММ`, например: `21:30`.");
                return;
            }
        }
        userRepository.save(user);
        updateSettingsMessage(chatId, messageId, user);
    }

    private void updateDaySchedule(long chatId, long messageId, LocalDate date) {
        userRepository.findById(chatId).ifPresent(user ->
                omsuApiService.fetchScheduleForGroup(user.getGroupId()).ifPresent(response -> {
                    String text = scheduleService.getFormattedScheduleForDate(response.getData(), date);
                    EditMessageText editedMessage = new EditMessageText();
                    editedMessage.setChatId(chatId);
                    editedMessage.setMessageId((int) messageId);
                    editedMessage.setText(text);
                    editedMessage.setReplyMarkup(keyboardService.createDayNavigationKeyboard(date));
                    tryExecute(editedMessage);
                })
        );
    }

    private void showWeekSchedule(long chatId, LocalDate dateInWeek) {
        LocalDate monday = dateInWeek.with(DayOfWeek.MONDAY);
        userRepository.findById(chatId).ifPresent(user ->
                omsuApiService.fetchScheduleForGroup(user.getGroupId()).ifPresent(response -> {
                    String text = scheduleService.getFormattedWeekSchedule(response.getData(), monday);
                    SendMessage message = new SendMessage(String.valueOf(chatId), text);
                    message.setReplyMarkup(keyboardService.createWeekNavigationKeyboard(monday));
                    sendMessage(message);
                })
        );
    }

    private void updateWeekSchedule(long chatId, long messageId, LocalDate dateInWeek) {
        LocalDate monday = dateInWeek.with(DayOfWeek.MONDAY);
        userRepository.findById(chatId).ifPresent(user ->
                omsuApiService.fetchScheduleForGroup(user.getGroupId()).ifPresent(response -> {
                    String text = scheduleService.getFormattedWeekSchedule(response.getData(), monday);
                    EditMessageText editedMessage = new EditMessageText();
                    editedMessage.setChatId(chatId);
                    editedMessage.setMessageId((int) messageId);
                    editedMessage.setText(text);
                    editedMessage.setParseMode("Markdown");
                    editedMessage.setReplyMarkup(keyboardService.createWeekNavigationKeyboard(monday));
                    tryExecute(editedMessage);
                })
        );
    }

    private void showSettings(long chatId) {
        userRepository.findById(chatId).ifPresent(user -> {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("⚙️ *Настройки оповещений*\nНажми на кнопку, чтобы включить или выключить.");
            message.setParseMode("Markdown");
            message.setReplyMarkup(keyboardService.createSettingsMenu(user));
            sendMessage(message);
        });
    }

    private void updateSettingsMessage(long chatId, long messageId, TelegramUser user) {
        EditMessageText editedMessage = new EditMessageText();
        editedMessage.setChatId(chatId);
        editedMessage.setMessageId((int) messageId);
        editedMessage.setText("⚙️ *Настройки оповещений*\nНажми на кнопку, чтобы включить или выключить.");
        editedMessage.setParseMode("Markdown");
        editedMessage.setReplyMarkup(keyboardService.createSettingsMenu(user));
        tryExecute(editedMessage);
    }

    private TelegramUser registerUser(Message message) {
        return userRepository.findById(message.getChatId()).orElseGet(() -> {
            TelegramUser newUser = new TelegramUser();
            newUser.setChatId(message.getChatId());
            newUser.setFirstName(message.getFrom().getFirstName());
            newUser.setUsername(message.getFrom().getUserName());
            return userRepository.save(newUser);
        });
    }

    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        tryExecute(message);
    }

    public void sendMessage(SendMessage message) {
        message.setParseMode("Markdown");
        tryExecute(message);
    }

    private <T extends Serializable, Method extends BotApiMethod<T>> void tryExecute(Method method) {
        if (method instanceof SendMessage sendMessage) {
            sendMessage.setParseMode("Markdown");
        } else if (method instanceof EditMessageText editMessageText) {
            editMessageText.setParseMode("Markdown");
        }

        try {
            execute(method);
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("message is not modified")) {
                System.out.println("Сообщение не было изменено, пропуск ошибки.");
            } else System.err.println("Не удалось выполнить метод API: " + e.getMessage());
        }
    }
}