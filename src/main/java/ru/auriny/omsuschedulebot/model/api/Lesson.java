package ru.auriny.omsuschedulebot.model.api;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Lesson {
    private long id;
    private String day;
    private int time;
    private String group;

    @SerializedName("lesson") private String lessonName;
    @SerializedName("type_work") private String lessonType;
    @SerializedName("teacher") private String teacherName;
    @SerializedName("auditCorps") private String auditory;
    @SerializedName("subgroupName") private String subgroupName;
}