package ru.auriny.omsuschedulebot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "telegram_users")
public class TelegramUser {

    public static final String STATE_AWAITING_GROUP = "AWAITING_GROUP";
    public static final String STATE_REGISTERED = "REGISTERED";

    @Id
    private Long chatId;

    private String firstName;
    private String username;

    private String userState = STATE_AWAITING_GROUP;

    private Integer groupId;
    private String groupName;

    @Column(columnDefinition = "TEXT")
    private String scheduleJson;

    private boolean dailyNotificationEnabled = true;
    private boolean updateNotificationEnabled = true;

    private String notificationTime = "20:00"; //default, will be changed by user
}