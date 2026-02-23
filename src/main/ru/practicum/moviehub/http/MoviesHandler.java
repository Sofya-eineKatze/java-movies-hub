package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.model.Movie;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
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
                ex.sendResponseHeaders(405, -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ex.sendResponseHeaders(500, -1);
        }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        Collection<Movie> movies = store.getAllMovies();
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
                            e.printStackTrace();
                        }
                    },
                    () -> {
                        try {
                            sendError(ex, 404, "Фильм не найден");
                        } catch (IOException e) {
                            e.printStackTrace();
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
            ex.sendResponseHeaders(415, -1);
            return;
        }

        InputStream is = ex.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        // Строгая проверка JSON
        if (!isStrictValidJson(body)) {
            sendError(ex, 400, "Некорректный JSON");
            return;
        }

        try {
            Movie newMovie = gson.fromJson(body, Movie.class);

            if (newMovie == null) {
                sendError(ex, 400, "Некорректный JSON");
                return;
            }

            List<String> errors = validateMovie(newMovie);

            if (!errors.isEmpty()) {
                sendError(ex, 422, "Ошибка валидации", errors);
                return;
            }

            Movie createdMovie = store.addMovie(newMovie);
            sendJson(ex, 201, createdMovie);
        } catch (Exception e) {
            sendError(ex, 400, "Некорректный JSON");
        }
    }

    private boolean isStrictValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        String trimmed = json.trim();

        // Проверяем, что это объект в фигурных скобках
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }

        // Проверяем наличие двоеточий (ключ: значение)
        if (!trimmed.contains(":")) {
            return false;
        }

        // Проверяем, что ключи в двойных кавычках
        // Ищем что-то типа "title": или "year":
        if (!trimmed.contains("\"title\"") || !trimmed.contains("\"year\"")) {
            return false;
        }

        // Проверяем формат { "title": "Inception", "year": 2010 }
        // Должны быть кавычки вокруг title и year
        int titlePos = trimmed.indexOf("\"title\"");
        int yearPos = trimmed.indexOf("\"year\"");

        if (titlePos == -1 || yearPos == -1) {
            return false;
        }

        // Проверяем, что после title идет двоеточие
        int colonAfterTitle = trimmed.indexOf(":", titlePos + 7);
        if (colonAfterTitle == -1) {
            return false;
        }

        // Проверяем, что после year идет двоеточие
        int colonAfterYear = trimmed.indexOf(":", yearPos + 6);
        if (colonAfterYear == -1) {
            return false;
        }

        return true;
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
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название должно быть не длиннее 100 символов");
        }
        int currentYear = Year.now().getValue();
        if (movie.getYear() == null) {
            errors.add("год должен быть указан");
        } else if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            errors.add(String.format("год должен быть между 1888 и %d", currentYear + 1));
        }
        return errors;
    }
}