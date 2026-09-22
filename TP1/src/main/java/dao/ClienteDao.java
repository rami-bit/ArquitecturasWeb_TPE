package dao;

import entity.Cliente;

import java.util.List;

public interface ClienteDao extends Dao<Cliente>{
    List<Cliente> findAll();
    List<Cliente> findAllOrderedByFacturas();

    void create (Cliente c);
}
