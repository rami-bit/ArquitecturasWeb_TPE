package service;

import entity.Inscripcion;
import repository.RepositoryInscripcion;

import java.util.List;

public class ServiceInscripcion implements Service<Inscripcion> {
    private RepositoryInscripcion repository;

    public ServiceInscripcion(RepositoryInscripcion repository) {
        this.repository = repository;
    }

    @Override
    public void save(Inscripcion inscripcion) {
        repository.save(inscripcion);
    }

    @Override
    public Inscripcion update(Inscripcion inscripcion) {
        return repository.update(inscripcion);
    }

    @Override
    public void delete(Inscripcion inscripcion) {
        repository.delete(inscripcion);
    }

    @Override
    public Inscripcion findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Inscripcion> findAll() {
        return repository.findAll();
    }
}
