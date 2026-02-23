package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class MoviesStore {
    private final ConcurrentMap<Long, Movie> movies = new ConcurrentHashMap<>();

    public Collection<Movie> getAllMovies() {
        return movies.values();
    }

    public Optional<Movie> getMovieById(Long id) {
        return Optional.ofNullable(movies.get(id));
    }

    public Movie addMovie(Movie movie) {
        Long id = Movie.generateId();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public boolean deleteMovie(Long id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
    }

    public void clear() {
        movies.clear();
    }

    public int size() {
        return movies.size();
    }
}