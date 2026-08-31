package dao;

import entity.Cliente;

import java.util.List;

public interface ClienteDao {
    void create(Cliente c);

    List<Cliente> findAll();
}
