package ru.auriny.omsuschedulebot.service;


import lombok.Setter;
import org.springframework.stereotype.Service;
import ru.auriny.omsuschedulebot.bot.Bot;

@Setter
@Service
public class NotificationService {
    private Bot scheduleBot;

    public void notifyUser(long chatId, String message) {
        if (scheduleBot != null) scheduleBot.sendMessage(chatId, message);
    }
}
