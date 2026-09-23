package com.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.Race.Race;
import com.game.hardware.StepperAngleConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypingRaceServerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private TypingRaceServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = new TypingRaceServer("127.0.0.1", 0, new Race(new StepperAngleConverter(0.03)));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void servesGameAndParticipantDistanceWorkflow() throws Exception {
        HttpResponse<String> page = send("GET", "/", null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("TYPE<span>SHIFT"));

        HttpResponse<String> joined = send("POST", "/api/race/participants",
                "{\"name\":\"LAN Driver\",\"color\":\"#22D3EE\"}");
        assertEquals(201, joined.statusCode());
        long id = json.readTree(joined.body()).path("participantId").asLong();

        HttpResponse<String> updated = send("PUT", "/api/race/participants/" + id + "/progress",
                "{\"typedCharacters\":40,\"errors\":1}");
        assertEquals(200, updated.statusCode());

        HttpResponse<String> distance = send("GET", "/api/race/distances", null);
        assertEquals(200, distance.statusCode());
        assertEquals("*", distance.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        String participant = json.readTree(distance.body()).path("participants").findValuesAsText("name").stream()
                .filter("LAN Driver"::equals).findFirst().orElse(null);
        assertTrue(participant != null);
        assertTrue(distance.body().contains("\"distanceMeters\""));
        assertTrue(distance.body().contains("\"steps\""));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.method(method, HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json");
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
