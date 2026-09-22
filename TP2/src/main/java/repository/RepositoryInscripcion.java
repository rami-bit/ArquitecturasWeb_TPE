package repository;

import entity.Inscripcion;

import javax.persistence.EntityManager;
import java.util.List;

public class RepositoryInscripcion implements Repository<Inscripcion> {
    private EntityManager em;

    public RepositoryInscripcion(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(Inscripcion inscripcion) {
        if (inscripcion.getId() == null) {
            em.persist(inscripcion);
        } else {
            em.merge(inscripcion);
        }
    }

    @Override
    public Inscripcion findById(Long id) {
        return em.find(Inscripcion.class, id);
    }

    @Override
    public List<Inscripcion> findAll() {
        return em.createQuery("SELECT i FROM Inscripcion i", Inscripcion.class).getResultList();
    }

    @Override
    public void delete(Inscripcion inscripcion) {
        if (!em.contains(inscripcion)) {
            inscripcion = em.merge(inscripcion);
        }
        em.remove(inscripcion);
    }

    @Override
    public Inscripcion update(Inscripcion inscripcion) {
        return em.merge(inscripcion);
    }
}
