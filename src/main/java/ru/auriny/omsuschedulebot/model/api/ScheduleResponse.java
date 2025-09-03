package ru.auriny.omsuschedulebot.model.api;

import lombok.Data;
import java.util.List;

@Data
public class ScheduleResponse {
    private boolean success;
    private String message;
    private List<ScheduleDay> data;
}
