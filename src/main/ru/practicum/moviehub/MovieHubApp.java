package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

public class MovieHubApp {
    public static void main(String[] args) {
        final MoviesStore store = new MoviesStore();
        final MoviesServer server = new MoviesServer(store, 8080);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        System.out.println("MovieHub API запущен. Доступные эндпоинты:");
        System.out.println("  GET /movies");
        System.out.println("  POST /movies");
        System.out.println("  GET /movies/{id}");
        System.out.println("  DELETE /movies/{id}");
        System.out.println("  GET /movies?year=YYYY");
        System.out.println("Для остановки нажмите Ctrl+C");
    }
}