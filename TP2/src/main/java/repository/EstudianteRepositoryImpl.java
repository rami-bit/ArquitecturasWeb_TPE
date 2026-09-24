package repository;


import dto.EstudianteDTO;
import repository.EstudianteRepository;

import factory.JPAutil;
import entity.Estudiante;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EstudianteRepositoryImpl implements EstudianteRepository {
    // Lista de campos de ordenamiento por el metodo obtenerEstudiantesOrdenados
    private final ArrayList<String> camposOrdenados = new ArrayList<>(
            Arrays.asList("nombre", "apellido", "dni", "edad", "genero", "ciudad"));

    // punto a)
    @Override
    public void addEstudiante(Estudiante estudiante) {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            em.getTransaction().begin();

            if (!em.contains(estudiante)) {em.persist(estudiante);
            }else {em.merge(estudiante);}

            em.getTransaction().commit();
        } catch (Exception e) {
            if (em != null && em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("error al cargar el estudiante", e);
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    // PUNTO c)
    @Override
    public List<EstudianteDTO> getEstudiantesSorted(String campo) {
        // si el campo no existe en camposOrdenados lanzar excepcion
        if (!camposOrdenados.contains(campo.toLowerCase())) {
            throw new IllegalArgumentException("el campo a ordenar no existe");
        }

        EntityManager em = JPAutil.getEntityManager();
        String orden = campo.equals("ciudad") ? "ciudadResidencia" : campo;
        String jpql = "SELECT new dto.EstudianteDTO(e.dni, e.nombre, e.apellido, e.edad, e.genero, e.ciudadResidencia, e.numeroLibreta) "
                +
                "FROM Estudiante e " +
                "ORDER BY LOWER(e." + orden + ")";

        List<EstudianteDTO> estudiantes = em.createQuery(jpql, EstudianteDTO.class)
                .getResultList();
        em.close();

        return estudiantes;
    }

    // PUNTO d
    @Override
    public EstudianteDTO getEstudianteLU(int nroLibreta) {
        try {
            EntityManager em = JPAutil.getEntityManager();
            String jpql = "SELECT new dto.EstudianteDTO(e.dni,e.nombre,e.apellido,e.edad,e.genero,e.ciudadResidencia,e.numeroLibreta) "
                    +
                    "FROM Estudiante e " +
                    "WHERE e.numeroLibreta=:nroLibreta";
            EstudianteDTO estudiante = em.createQuery(jpql, EstudianteDTO.class).setParameter("nroLibreta", nroLibreta)
                    .getSingleResult();
            em.close();

            return estudiante;
        } catch (Exception e) {
            System.err.println("no se encontro el estudiante con nroLibreta: " + nroLibreta);
            return null;
        }
    }

    // PUNTO e)
    @Override
    public List<EstudianteDTO> getEstudiantesByGenero(String genero) {
        EntityManager em = JPAutil.getEntityManager();
        String jpql = "SELECT new dto.EstudianteDTO(e.dni,e.nombre,e.apellido,e.edad,e.genero,e.ciudadResidencia,e.numeroLibreta) "
                +
                "FROM Estudiante e " +
                "WHERE LOWER(e.genero) = LOWER(:genero)";
        List<EstudianteDTO> estudiantes = em.createQuery(jpql, EstudianteDTO.class).setParameter("genero", genero)
                .getResultList();
        em.close();

        if (estudiantes.isEmpty()) {
            return null;
        }

        return estudiantes;
    }

    // Cargar estudiantes csv
    @Override
    public void addEstudiantes(List<Estudiante> estudiantes) {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            em.getTransaction().begin();
            for (Estudiante estudiante : estudiantes) {
                em.persist(estudiante);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em != null && em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("error al cargar los estudiantes", e);
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    // PUNTO g)
    @Override
    public List<EstudianteDTO> getEstudiantesByCarreraAndCiudad(String carrera, String ciudad) {
        EntityManager em = JPAutil.getEntityManager();
        String jpql = "SELECT new dto.EstudianteDTO(e.dni,e.nombre,e.apellido,e.edad,e.genero,e.ciudadResidencia,e.numeroLibreta) "
                +
                "FROM Estudiante e JOIN e.inscripciones m " +
                "JOIN m.carrera c " +
                "WHERE LOWER(c.carrera) = LOWER(:carrera) AND LOWER(e.ciudadResidencia) = LOWER(:ciudad)";
        List<EstudianteDTO> estudiantes = em.createQuery(jpql, EstudianteDTO.class)
                .setParameter("carrera", carrera)
                .setParameter("ciudad", ciudad)
                .getResultList();
        em.close();
        return estudiantes;
    }

    // Buscar estudiante por id
    @Override
    public Estudiante findById(long id) {
        EntityManager em = JPAutil.getEntityManager();        
        Estudiante estudiante = em.find(Estudiante.class, id);
        em.close();
        return estudiante;
    }

}