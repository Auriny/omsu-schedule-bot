package ru.auriny.omsuschedulebot.bot;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Component
public class BotInitializer {
    private final Bot scheduleBot;

    public BotInitializer(Bot scheduleBot) {
        this.scheduleBot = scheduleBot;
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(scheduleBot);
        } catch (TelegramApiException e) {
            System.err.println("Ошибка при регистрации бота: " + e.getMessage());
        }
    }
}