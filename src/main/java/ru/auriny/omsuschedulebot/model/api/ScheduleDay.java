package ru.auriny.omsuschedulebot.model.api;

import lombok.Data;

import java.util.List;

@Data
public class ScheduleDay {
    private String day;
    private List<Lesson> lessons;
}