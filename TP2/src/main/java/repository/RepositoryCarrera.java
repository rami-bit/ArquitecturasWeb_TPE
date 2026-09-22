package repository;

import entity.Carrera;

import javax.persistence.EntityManager;
import java.util.List;

public class RepositoryCarrera implements Repository<Carrera> {

    private EntityManager em;

    public RepositoryCarrera(EntityManager em) {
        this.em = em;
    }

    @Override
    public void save(Carrera carrera) {
        if (carrera.getId() == null) {
            em.persist(carrera);
        }else {
            em.merge(carrera);
        }
    }

    @Override
    public Carrera findById(Long id) {
        return em.find(Carrera.class, id);
    }

    @Override
    public List<Carrera> findAll() {
        return em.createQuery("SELECT c FROM Carrera c", Carrera.class).getResultList();
    }

    @Override
    public void delete(Carrera carrera) {
        if (!em.contains(carrera)) {
            carrera = em.merge(carrera);
        }
        em.remove(carrera);
    }

    @Override
    public Carrera update(Carrera carrera) {
        return em.merge(carrera);
    }
}
