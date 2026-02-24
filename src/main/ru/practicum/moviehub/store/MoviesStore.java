package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MoviesStore {
    private final ConcurrentMap<Long, Movie> movies = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public Optional<Movie> getMovieById(Long id) {
        return Optional.ofNullable(movies.get(id));
    }

    public Movie addMovie(Movie movie) {
        Long id = idGenerator.getAndIncrement();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public boolean deleteMovie(Long id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
    }

    public void clear() {
        movies.clear();
        idGenerator.set(1);
    }

    public int size() {
        return movies.size();
    }
}