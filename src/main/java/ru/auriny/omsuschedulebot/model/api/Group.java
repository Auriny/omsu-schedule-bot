package ru.auriny.omsuschedulebot.model.api;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = "id")
public class Group {
    private int id;
    private String name;
}