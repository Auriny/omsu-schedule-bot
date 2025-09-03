package ru.auriny.omsuschedulebot.model.api;

import lombok.Data;
import java.util.List;

@Data
public class GroupsResponse {
    private boolean success;
    private List<Group> data;
}