package com.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.game.Race.Race;
import com.game.Race.User;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TypingRaceServer {
    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper json = new ObjectMapper();
    private final Race race;

    public TypingRaceServer(String host, int port, Race race) throws IOException {
        this.race = race;
        server = HttpServer.create(new InetSocketAddress(host, port), 32);
        executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        server.setExecutor(executor);
        server.createContext("/api/health", this::health);
        server.createContext("/api/race/distances", this::distances);
        server.createContext("/api/race/participants", this::participants);
        server.createContext("/api/race/start", this::start);
        server.createContext("/api/race/reset", this::reset);
        server.createContext("/api/race", this::raceState);
        server.createContext("/", this::staticFiles);
    }

    public void start() { server.start(); }

    public void stop() {
        server.stop(1);
        executor.shutdownNow();
    }

    public int getPort() { return server.getAddress().getPort(); }

    private void health(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "GET")) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "typing-race");
        sendJson(exchange, 200, body);
    }

    private void raceState(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "GET")) return;
        sendJson(exchange, 200, race.state());
    }

    private void distances(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "GET")) return;
        sendJson(exchange, 200, race.distances());
    }

    private void reset(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "POST")) return;
        sendJson(exchange, 200, race.reset());
    }

    private void start(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "POST")) return;
        try {
            JsonNode request = readJson(exchange);
            if (!request.has("participantId")) {
                throw new IllegalArgumentException("participantId is required");
            }
            sendJson(exchange, 200, race.start(request.path("participantId").asLong()));
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Request body must be valid JSON");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, exception.getMessage());
        }
    }

    private void participants(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        try {
            String path = exchange.getRequestURI().getPath();
            if ("/api/race/participants".equals(path)) {
                if (!requireMethod(exchange, "POST")) return;
                JsonNode request = readJson(exchange);
                int color = parseColor(request.path("color").asText("#22D3EE"));
                User user = race.join(request.path("name").asText(null), color);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("participantId", user.getId());
                response.put("race", race.state());
                sendJson(exchange, 201, response);
                return;
            }

            String prefix = "/api/race/participants/";
            if (path.startsWith(prefix) && path.endsWith("/progress")) {
                if (!requireMethod(exchange, "PUT")) return;
                String idText = path.substring(prefix.length(), path.length() - "/progress".length());
                long id = Long.parseLong(idText);
                JsonNode request = readJson(exchange);
                if (!request.has("typedCharacters")) {
                    throw new IllegalArgumentException("typedCharacters is required");
                }
                race.updateProgress(id, request.path("typedCharacters").asInt(), request.path("errors").asInt(0));
                sendJson(exchange, 200, race.distances());
                return;
            }
            sendError(exchange, 404, "Endpoint not found");
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Request body must be valid JSON");
        } catch (NumberFormatException exception) {
            sendError(exchange, 400, "Invalid participant id");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, exception.getMessage());
        }
    }

    private void staticFiles(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path)) path = "/index.html";
        if (path.contains("..")) {
            sendError(exchange, 400, "Invalid path");
            return;
        }
        String resource = "/web" + path;
        try (InputStream input = TypingRaceServer.class.getResourceAsStream(resource)) {
            if (input == null) {
                sendError(exchange, 404, "Page not found");
                return;
            }
            byte[] bytes = readAll(input, 2_000_000);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(path));
            headers.set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private JsonNode readJson(HttpExchange exchange) throws IOException {
        byte[] bytes = readAll(exchange.getRequestBody(), 32_768);
        if (bytes.length == 0) return json.createObjectNode();
        return json.readTree(bytes);
    }

    private byte[] readAll(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximumBytes) throw new IOException("Request or resource is too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean requireMethod(HttpExchange exchange, String expected) throws IOException {
        if (!expected.equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return false;
        }
        return true;
    }

    private boolean preflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            addCors(exchange.getResponseHeaders());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        addCors(headers);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", message == null ? "Request failed" : message);
        sendJson(exchange, status, error);
    }

    private static void addCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static int parseColor(String value) {
        String cleaned = value == null ? "" : value.trim().replace("#", "");
        if (!cleaned.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color must be a 6-digit hex value");
        return Integer.parseInt(cleaned, 16);
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "text/html; charset=utf-8";
    }
}
