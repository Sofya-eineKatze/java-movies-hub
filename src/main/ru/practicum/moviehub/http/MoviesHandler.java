package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.model.Movie;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private static final Logger LOGGER = Logger.getLogger(MoviesHandler.class.getName());

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        try {
            if (method.equalsIgnoreCase("GET") && path.equals("/movies") && query == null) {
                handleGetAll(ex);
            } else if (method.equalsIgnoreCase("GET") && path.equals("/movies") && query != null) {
                handleGetByYear(ex, query);
            } else if (method.equalsIgnoreCase("GET") && path.startsWith("/movies/")) {
                handleGetById(ex, path);
            } else if (method.equalsIgnoreCase("POST") && path.equals("/movies")) {
                handlePost(ex);
            } else if (method.equalsIgnoreCase("DELETE") && path.startsWith("/movies/")) {
                handleDelete(ex, path);
            } else {
                sendError(ex, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Необработанная ошибка", e);
            sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        List<Movie> movies = store.getAllMovies();
        sendJson(ex, 200, movies);
    }

    private void handleGetByYear(HttpExchange ex, String query) throws IOException {
        try {
            if (!query.startsWith("year=")) {
                sendError(ex, 400, "Некорректный параметр запроса");
                return;
            }

            String yearStr = query.substring(5);
            if (yearStr.isEmpty()) {
                sendError(ex, 400, "Некорректный параметр запроса - 'year'");
                return;
            }

            try {
                int year = Integer.parseInt(yearStr);
                List<Movie> movies = store.getMoviesByYear(year);
                sendJson(ex, 200, movies);
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Некорректный параметр запроса - 'year'");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Ошибка в фильтре по году", e);
            sendError(ex, 400, "Некорректный параметр запроса");
        }
    }

    private void handleGetById(HttpExchange ex, String path) throws IOException {
        String idStr = path.substring("/movies/".length());

        if (!idStr.matches("\\d+")) {
            sendError(ex, 400, "Некорректный ID");
            return;
        }

        try {
            Long id = Long.parseLong(idStr);
            store.getMovieById(id).ifPresentOrElse(
                    movie -> {
                        try {
                            sendJson(ex, 200, movie);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Ошибка отправки фильма", e);
                            try {
                                sendError(ex, 500, "Внутренняя ошибка сервера");
                            } catch (IOException ex2) {
                                LOGGER.log(Level.SEVERE, "Ошибка отправки 500", ex2);
                            }
                        }
                    },
                    () -> {
                        try {
                            sendError(ex, 404, "Фильм не найден");
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Ошибка отправки 404", e);
                        }
                    }
            );
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendError(ex, 415, "Unsupported Media Type");
            return;
        }

        try (InputStream is = ex.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Временное решение: Gson пропускает JSON без кавычек
            // Проверяем наличие кавычек у ключей
            if (!hasQuotedKeys(body)) {
                sendError(ex, 400, "Некорректный JSON: ключи должны быть в двойных кавычках (ограничение Gson)");
                return;
            }

            // Парсим в объект Movie
            Movie newMovie;
            try {
                newMovie = gson.fromJson(body, Movie.class);
            } catch (Exception e) {
                sendError(ex, 400, "Некорректный JSON");
                return;
            }

            // Валидация полей
            List<String> errors = validateMovie(newMovie);
            if (!errors.isEmpty()) {
                sendError(ex, 422, "Ошибка валидации", errors);
                return;
            }

            // Сохранение
            Movie createdMovie = store.addMovie(newMovie);
            sendJson(ex, 201, createdMovie);

        } catch (IOException e) {
            sendError(ex, 500, "Ошибка чтения запроса");
        }
    }

    // Временный метод для проверки кавычек из-за особенностей Gson
    private boolean hasQuotedKeys(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        String trimmed = json.trim();
        return trimmed.contains("\"title\"") && trimmed.contains("\"year\"");
    }

    private void handleDelete(HttpExchange ex, String path) throws IOException {
        String idStr = path.substring("/movies/".length());

        if (!idStr.matches("\\d+")) {
            sendError(ex, 400, "Некорректный ID");
            return;
        }

        try {
            Long id = Long.parseLong(idStr);
            boolean deleted = store.deleteMovie(id);

            if (deleted) {
                sendNoContent(ex);
            } else {
                sendError(ex, 404, "Фильм не найден");
            }
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie == null) {
            errors.add("Тело запроса отсутствует");
            return errors;
        }

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("Поле title обязательно и не может быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("Поле title не может быть длиннее 100 символов");
        }

        int currentYear = Year.now().getValue();
        if (movie.getYear() == null) {
            errors.add("Поле year обязательно");
        } else if (movie.getYear() < 1888) {
            errors.add("Год выпуска не может быть меньше 1888");
        } else if (movie.getYear() > currentYear + 1) {
            errors.add(String.format("Год выпуска не может быть больше %d", currentYear + 1));
        }

        return errors;
    }
}