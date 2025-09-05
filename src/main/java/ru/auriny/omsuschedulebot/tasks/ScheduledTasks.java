package ru.auriny.omsuschedulebot.tasks;

import com.google.gson.Gson;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.auriny.omsuschedulebot.model.TelegramUser;
import ru.auriny.omsuschedulebot.repository.UserRepository;
import ru.auriny.omsuschedulebot.service.NotificationService;
import ru.auriny.omsuschedulebot.service.OmsuApiService;
import ru.auriny.omsuschedulebot.service.ScheduleService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
public class ScheduledTasks {
    private final OmsuApiService omsuApiService;
    private final ScheduleService scheduleService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ScheduledTasks(OmsuApiService omsuApiService,
                          ScheduleService scheduleService,
                          UserRepository userRepository,
                          NotificationService notificationService) {
        this.omsuApiService = omsuApiService;
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void processMinuteJobs() {
        LocalTime now = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        String currentTime = now.format(formatter);

        userRepository.findAllByDailyNotificationEnabledIsTrue().stream()
                .filter(user -> user.getGroupId() != null && currentTime.equals(user.getNotificationTime()))
                .forEach(this::sendDailyScheduleForUser);
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void processHourlyJobs() {
        System.out.println("Проверяю обновления расписания...");
        userRepository.findAll().stream()
                .filter(user -> user.getGroupId() != null && user.isUpdateNotificationEnabled())
                .forEach(this::checkForUpdatesForUser);
    }

    private void checkForUpdatesForUser(TelegramUser user) {
        omsuApiService.fetchScheduleForGroup(user.getGroupId()).ifPresent(response -> {
            if (response.isSuccess()) {
                String newJson = new Gson().toJson(response.getData());
                String oldJson = user.getScheduleJson();
                String changes = scheduleService.findScheduleDifferences(oldJson, newJson);
                if (!changes.isEmpty()) {
                    notificationService.notifyUser(user.getChatId(), changes);
                }
                user.setScheduleJson(newJson);
                userRepository.save(user);
            }
        });
    }

    private void sendDailyScheduleForUser(TelegramUser user) {
        System.out.println("Проверяю ежедневное расписание для пользователя: " + user.getChatId());

        omsuApiService.fetchScheduleForGroup(user.getGroupId()).ifPresent(response -> {
            if (response.isSuccess()) {
                LocalTime now = LocalTime.now();
                LocalDate dateToSend;

                if (now.isAfter(LocalTime.of(17, 0)) && now.isBefore(LocalTime.of(23, 59, 59))) {
                    dateToSend = LocalDate.now().plusDays(1);
                } else dateToSend = LocalDate.now();

                String scheduleMessage = scheduleService.getFormattedScheduleForDate(response.getData(), dateToSend);

                if (!scheduleMessage.contains("пар нет")) {
                    notificationService.notifyUser(user.getChatId(), scheduleMessage);
                    System.out.println("Отправлено расписание пользователю: " + user.getChatId());
                } else System.out.println("У пользователя " + user.getChatId() + " на выбранный день пар нет, уведомление не отправлено.");
            }
        });
    }

}