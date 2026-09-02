package dao;

import entity.Producto;
import java.util.List;

public interface ProductoDao {
    void create(Producto p);
    Producto findById(Long id);
    List<Producto> findAll();
    void update(Producto p);
    void delete(Long id);
}