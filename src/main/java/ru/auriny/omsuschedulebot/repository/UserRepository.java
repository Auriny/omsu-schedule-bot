package ru.auriny.omsuschedulebot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.auriny.omsuschedulebot.model.TelegramUser;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<TelegramUser, Long> {
    List<TelegramUser> findAllByDailyNotificationEnabledIsTrue();
}