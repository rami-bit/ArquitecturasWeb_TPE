package service;

import entity.Carrera;
import repository.RepositoryCarrera;

import java.util.List;

public class ServiceCarrera implements Service<Carrera> {
    private RepositoryCarrera repository;

    public ServiceCarrera(RepositoryCarrera repository) {
        this.repository = repository;
    }

    @Override
    public void save(Carrera carrera) {
        repository.save(carrera);
    }

    @Override
    public Carrera update(Carrera carrera) {
        return repository.update(carrera);
    }

    @Override
    public void delete(Carrera carrera) {
        repository.delete(carrera);
    }

    @Override
    public Carrera findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Carrera> findAll() {
        return repository.findAll();
    }
}
