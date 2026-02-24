package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
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

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

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
                            try {
                                sendError(ex, 500, "Внутренняя ошибка сервера");
                            } catch (IOException ex2) {
                                // ignore
                            }
                        }
                    },
                    () -> {
                        try {
                            sendError(ex, 404, "Фильм не найден");
                        } catch (IOException e) {
                            // ignore
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

            // ПРОВЕРКА 1: Должны быть двойные кавычки у ключей
            if (!body.contains("\"title\"") || !body.contains("\"year\"")) {
                sendError(ex, 400, "Некорректный JSON: ключи должны быть в двойных кавычках");
                return;
            }

            // ПРОВЕРКА 2: Должны быть двоеточия после ключей
            int titlePos = body.indexOf("\"title\"");
            int yearPos = body.indexOf("\"year\"");

            if (titlePos == -1 || yearPos == -1) {
                sendError(ex, 400, "Некорректный JSON");
                return;
            }

            // Проверяем, что после "title" есть двоеточие
            String afterTitle = body.substring(titlePos + 7);
            if (!afterTitle.trim().startsWith(":")) {
                sendError(ex, 400, "Некорректный JSON");
                return;
            }

            // Проверяем, что после "year" есть двоеточие
            String afterYear = body.substring(yearPos + 6);
            if (!afterYear.trim().startsWith(":")) {
                sendError(ex, 400, "Некорректный JSON");
                return;
            }

            // ПРОВЕРКА 3: Пробуем распарсить через JsonParser
            try {
                JsonParser.parseString(body);
            } catch (JsonParseException e) {
                sendError(ex, 400, "Некорректный JSON");
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

            // Проверяем, что объект не null
            if (newMovie == null) {
                sendError(ex, 400, "Некорректный JSON");
                return;
            }

            // Валидация полей
            List<String> errors = validateMovie(newMovie);
            if (!errors.isEmpty()) {
                sendError(ex, 422, "Ошибка валидации", errors);
                return;
            }

            // Сохраняем фильм
            Movie createdMovie = store.addMovie(newMovie);
            sendJson(ex, 201, createdMovie);

        } catch (IOException e) {
            sendError(ex, 500, "Ошибка чтения запроса");
        }
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
            errors.add("Тело запроса не может быть пустым");
            return errors;
        }

        // Проверка title
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название должно быть не длиннее 100 символов");
        }

        // Проверка year
        int currentYear = Year.now().getValue();
        if (movie.getYear() == null) {
            errors.add("год должен быть указан");
        } else if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            errors.add(String.format("год должен быть между 1888 и %d", currentYear + 1));
        }

        return errors;
    }
}