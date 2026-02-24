package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

    // Строгий Gson, который требует кавычки у ключей
    protected static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    protected void sendJson(HttpExchange ex, int status, Object object) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        String json = gson.toJson(object);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, responseBytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
    }

    protected void sendError(HttpExchange ex, int status, String error, List<String> details) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(error, details);
        sendJson(ex, status, errorResponse);
    }

    protected void sendError(HttpExchange ex, int status, String error) throws IOException {
        sendError(ex, status, error, List.of());
    }

    // Метод для проверки валидности JSON
    protected boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonParseException e) {
            return false;
        }
    }
}