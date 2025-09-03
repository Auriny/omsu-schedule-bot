package ru.auriny.omsuschedulebot.service;

import com.google.gson.Gson;
import org.springframework.stereotype.Service;
import ru.auriny.omsuschedulebot.model.api.Group;
import ru.auriny.omsuschedulebot.model.api.GroupsResponse;
import ru.auriny.omsuschedulebot.model.api.ScheduleResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class OmsuApiService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private List<Group> groupsCache = null;

    public Optional<ScheduleResponse> fetchScheduleForGroup(int groupId) { //слыш новенький скинь fetch
        String url = "https://eservice.omsu.ru/schedule/backend/schedule/group/" + groupId;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.ofNullable(gson.fromJson(response.body(), ScheduleResponse.class));
            }
        } catch (Exception e) { // да я знаю что мог сделать вызов логгера но мне лень
            System.err.println("[Schedule] Ошибка при запросе к есервакам ОмГУ: " + e.getMessage());
        }

        return Optional.empty();
    }

    public List<Group> fetchAllGroups() {
        if (groupsCache != null) return groupsCache;

        String url = "https://eservice.omsu.ru/schedule/backend/dict/groups";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                GroupsResponse groupsResponse = gson.fromJson(response.body(), GroupsResponse.class);
                if (groupsResponse != null && groupsResponse.isSuccess()) {
                    this.groupsCache = groupsResponse.getData();
                    return groupsResponse.getData();
                }
            }
        } catch (Exception e) {
            System.err.println("[Groups] Ошибка при запросе к есервакам ОмГУ): " + e.getMessage());
        }

        return Collections.emptyList();
    }
}