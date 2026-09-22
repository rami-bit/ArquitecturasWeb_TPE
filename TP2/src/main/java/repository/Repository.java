package repository;

import java.util.List;

public interface Repository<T>{
    public void save(T t);
    public T update(T t);
    public void delete(T t);
    public T findById(Long id);
    public List<T> findAll();
}
