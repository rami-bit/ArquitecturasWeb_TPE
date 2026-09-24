package repository;
import factory.JPAutil;
import repository.EstudianteCarreraRepository;

import javax.persistence.EntityManager;
import entity.EstudianteCarrera;

import java.util.List;

public class EstudianteCarreraRepositoryImpl implements EstudianteCarreraRepository {
    // cargar matriculas csv
    @Override
    public void matricularEstudiantes(List<EstudianteCarrera> estudiantesCarrera) {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            em.getTransaction().begin();
            for(EstudianteCarrera estudianteCarrera:estudiantesCarrera){
                em.persist(estudianteCarrera);
            }
            em.getTransaction().commit();
        }catch (Exception e){
            if (em != null && em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("error al matricular los estudiantes",e);
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }
}
