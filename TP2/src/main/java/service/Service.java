package service;

import java.util.List;

public interface Service<T> {
    void save(T t);
    T update(T t);
    void delete(T t);
    T findById(Long id);
    List<T> findAll();
}
