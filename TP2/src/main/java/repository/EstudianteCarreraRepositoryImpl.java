package repository;
import factory.JPAutil;

import javax.persistence.EntityManager;

import entity.Carrera;
import entity.Estudiante;
import entity.EstudianteCarrera;

import java.util.List;


public class EstudianteCarreraRepositoryImpl implements EstudianteCarreraRepository {

    @Override
    public void matricularEstudiante( long dni, int carreraId,int inscripcion, int antiguedad, int graduacion) {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            Estudiante estudiante = em.find(Estudiante.class, dni);
            if (estudiante == null) {
                throw new RuntimeException("no existe un estudiante con numero de dni: " + dni);
            }

            Carrera carrera = em.find(Carrera.class, (long) carreraId);
            if (carrera == null) {
                throw new RuntimeException("No existe la carrera indicada con el id " + carreraId);
            }

            List<EstudianteCarrera> inscripciones = em.createQuery(
                            "SELECT ec FROM EstudianteCarrera ec " +
                                    "WHERE ec.estudiante = :estudiante AND ec.carrera = :carrera",
                            EstudianteCarrera.class)
                    .setParameter("estudiante", estudiante)
                    .setParameter("carrera", carrera)
                    .getResultList();

            if (!inscripciones.isEmpty()) {
                throw new RuntimeException("El estudiante ya está inscripto en dicha carrera");
            }

            Long nuevoId = em.createQuery(
                    "SELECT MAX(ec.id) FROM EstudianteCarrera ec",
                    Long.class
            ).getSingleResult();
            nuevoId = nuevoId == null ? 1L : nuevoId + 1;

            EstudianteCarrera nuevaInscripcion = new EstudianteCarrera(
                    nuevoId, estudiante, carrera, inscripcion, graduacion, antiguedad);

            try {
                em.getTransaction().begin();
                em.persist(nuevaInscripcion);
                em.getTransaction().commit();
            } catch (Exception ex) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw new RuntimeException("No se pudo realizar la matrícula", ex);
            }
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }


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
