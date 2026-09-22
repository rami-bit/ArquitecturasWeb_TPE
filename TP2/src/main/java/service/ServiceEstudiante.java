package service;

import entity.Estudiante;
import repository.RepositoryEstudiante;

import java.util.List;

public class ServiceEstudiante implements Service<Estudiante> {
    private RepositoryEstudiante repository;

    public ServiceEstudiante(RepositoryEstudiante repository) {
        this.repository = repository;
    }

    @Override
    public void save(Estudiante estudiante) {
        repository.save(estudiante);
    }

    @Override
    public Estudiante update(Estudiante estudiante) {
        return repository.update(estudiante);
    }

    @Override
    public void delete(Estudiante estudiante) {
        repository.delete(estudiante);
    }

    @Override
    public Estudiante findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Estudiante> findAll() {
        return repository.findAll();
    }
}
