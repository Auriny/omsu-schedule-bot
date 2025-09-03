package ru.auriny.omsuschedulebot.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Service;
import ru.auriny.omsuschedulebot.model.api.Lesson;
import ru.auriny.omsuschedulebot.model.api.ScheduleDay;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScheduleService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Map<Integer, String> LESSON_TIMES = Map.of(
            1, "08:45 - 10:20",
            2, "10:30 - 12:05",
            3, "12:45 - 14:20",
            4, "14:30 - 16:05",
            5, "16:15 - 17:50",
            6, "18:00 - 19:35"
    );

    public String getFormattedWeekSchedule(List<ScheduleDay> scheduleDays, LocalDate dateInWeek) {
        LocalDate monday = dateInWeek.with(DayOfWeek.MONDAY);
        StringBuilder sb = new StringBuilder("🗓️ *Расписание на неделю (")
                .append(monday.format(DATE_FORMATTER))
                .append(" - ")
                .append(monday.plusDays(5).format(DATE_FORMATTER))
                .append(")*\n\n");

        boolean hasLessons = false;
        for (int i = 0; i < 6; i++) {
            LocalDate day = monday.plusDays(i);
            String dayName = day.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("ru"));
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);

            String scheduleForDay = getFormattedScheduleForDate(scheduleDays, day);

            if (!scheduleForDay.contains("пар нет")) {
                hasLessons = true;
                sb.append("*").append(dayName).append("*, ").append(day.format(DATE_FORMATTER)).append("\n");
                sb.append(scheduleForDay.split("\n\n", 2)[1]);
                sb.append("\n");
            }
        }

        return hasLessons ? sb.toString() : "🎉 На этой неделе пар нет!";
    }

    public String findScheduleDifferences(String oldJson, String newJson) {
        if (oldJson == null || oldJson.isEmpty()) return "";

        Gson gson = new Gson();
        Type type = new TypeToken<List<ScheduleDay>>(){}.getType();

        List<ScheduleDay> oldDays = gson.fromJson(oldJson, type);
        if (oldDays == null) oldDays = Collections.emptyList();

        List<ScheduleDay> newDays = gson.fromJson(newJson, type);
        if (newDays == null) newDays = Collections.emptyList();

        Map<Long, Lesson> oldLessons = oldDays.stream()
                .flatMap(day -> day.getLessons().stream())
                .collect(Collectors.toMap(Lesson::getId, Function.identity(), (a, b) -> a));

        Map<Long, Lesson> newLessons = newDays.stream()
                .flatMap(day -> day.getLessons().stream())
                .collect(Collectors.toMap(Lesson::getId, Function.identity(), (a, b) -> a));

        StringBuilder changes = new StringBuilder("⚠️ *Расписание было изменено:*\n\n");
        boolean hasChanges = false;

        for (Lesson oldLesson : oldLessons.values()) {
            Lesson newLesson = newLessons.get(oldLesson.getId());
            if (newLesson == null) {
                changes.append("❌ *Удалена пара:*\n")
                        .append(formatLessonForDiff(oldLesson)).append("\n");
                hasChanges = true;
            } else if (!areLessonsEqual(oldLesson, newLesson)) {
                changes.append("🔄 *Изменена пара (").append(oldLesson.getDay()).append("):\n")
                        .append("└ _Было:_ ").append(formatLessonForDiff(oldLesson)).append("\n")
                        .append("└ _Стало:_ ").append(formatLessonForDiff(newLesson)).append("\n");
                hasChanges = true;
            }
        }

        for (Lesson newLesson : newLessons.values()) {
            if (!oldLessons.containsKey(newLesson.getId())) {
                changes.append("✅ *Добавлена пара:*\n")
                        .append(formatLessonForDiff(newLesson)).append("\n");
                hasChanges = true;
            }
        }

        return hasChanges ? changes.toString() : "";
    }

    private String formatLessonForDiff(Lesson l) {
        return String.format("%s (%s) у '%s' в ауд. %s",
                l.getLessonName(), l.getDay(), l.getTeacherName(), l.getAuditory());
    }

    private boolean areLessonsEqual(Lesson l1, Lesson l2) {
        return Objects.equals(l1.getDay(), l2.getDay()) &&
                l1.getTime() == l2.getTime() &&
                Objects.equals(l1.getTeacherName(), l2.getTeacherName()) &&
                Objects.equals(l1.getAuditory(), l2.getAuditory());
    }

    public String getFormattedScheduleForDate(List<ScheduleDay> scheduleDays, LocalDate date) {
        String dateString = date.format(DATE_FORMATTER);

        Optional<ScheduleDay> dayOptional = scheduleDays.stream()
                .filter(d -> d.getDay().equals(dateString))
                .findFirst();

        if (dayOptional.isEmpty() || dayOptional.get().getLessons().isEmpty()) {
            return "🎉 На *" + dateString + "* пар нет!";
        }

        StringBuilder sb = new StringBuilder("🗓️ *Расписание на " + dateString + "*\n\n");

        dayOptional.get().getLessons().stream()
                .collect(Collectors.groupingBy(Lesson::getTime))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int time = entry.getKey();
                    List<Lesson> lessonsAtTime = entry.getValue();

                    sb.append("*").append(time).append(" пара (").append(LESSON_TIMES.getOrDefault(time, "??:??")).append(")*\n");

                    var lessonsBySubject = lessonsAtTime.stream()
                            .collect(Collectors.groupingBy(Lesson::getLessonName));

                    var iterator = lessonsBySubject.entrySet().iterator();
                    while (iterator.hasNext()) {
                        var subjectEntry = iterator.next();
                        List<Lesson> lessons = subjectEntry.getValue();
                        Lesson firstLesson = lessons.get(0);

                        sb.append("📚 ").append(formatLessonName(firstLesson.getLessonName())).append("\n");

                        if (lessons.size() > 1) {
                            sb.append("👥 Преподаватели / 📍 Аудитории:\n");
                            lessons.forEach(l -> {
                                String groupNameToDisplay = l.getSubgroupName() != null ? l.getSubgroupName() : l.getGroup();
                                sb.append(" • ").append(l.getTeacherName()).append(" (").append(l.getAuditory()).append(") `").append(groupNameToDisplay).append("`\n");
                            });
                        } else {
                            sb.append("👥 ").append(firstLesson.getTeacherName()).append("\n");
                            String groupNameToDisplay = firstLesson.getSubgroupName() != null ? firstLesson.getSubgroupName() : firstLesson.getGroup();
                            sb.append("🏷️ Группа: `").append(groupNameToDisplay).append("`\n");
                            sb.append("📍 Аудитория: ").append(firstLesson.getAuditory()).append("\n");
                        }

                        if (iterator.hasNext()) sb.append("`--------------------`\n");
                    }
                    sb.append("\n");
                });

        return sb.toString();
    }

    private String formatLessonName(String lessonName) {
        return lessonName.replace("Прак", "(Прак)").replace("Лек", "(Лек)").replace("Лаб", "(Лаб)");
    }
}