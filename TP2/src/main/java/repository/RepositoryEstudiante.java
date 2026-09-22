package repository;

import entity.Estudiante;

import javax.persistence.EntityManager;
import java.util.List;

public class RepositoryEstudiante implements Repository<Estudiante> {
    private EntityManager em;

    public RepositoryEstudiante(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(Estudiante estudiante) {
        if (estudiante.getDni() == null) {
            em.persist(estudiante);
        } else {
            em.merge(estudiante);
        }
    }

    @Override
    public Estudiante findById(Long id) {
        return  em.find(Estudiante.class, id);
    }


    @Override
    public List<Estudiante> findAll() {
        return em.createQuery("SELECT e FROM Estudiante e", Estudiante.class).getResultList();
    }

    @Override
    public void delete(Estudiante estudiante) {
        if (!em.contains(estudiante)) {
            estudiante = em.merge(estudiante);
        }
        em.remove(estudiante);
    }

    @Override
    public Estudiante update(Estudiante estudiante) {
        return em.merge(estudiante);
    }
}
