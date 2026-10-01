package repository;


import dto.EstudianteDTO;
import repository.EstudianteRepository;

import factory.JPAutil;
import entity.Estudiante;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EstudianteRepositoryImpl implements EstudianteRepository {
    // Lista de campos de ordenamiento por el metodo obtenerEstudiantesOrdenados
    private final Set<String> camposOrdenados = Set.of("nombre", "apellido", "genero", "ciudad");

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
        String campoNormalizado = campo == null ? "" : campo.toLowerCase(Locale.ROOT);

        // si el campo no existe en camposOrdenados lanzar excepcion
        if (!camposOrdenados.contains(campoNormalizado)) {
            throw new IllegalArgumentException("el campo a ordenar no existe");
        }

        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            String orden = campoNormalizado.equals("ciudad") ? "ciudadResidencia" : campoNormalizado;
            String jpql = "SELECT new dto.EstudianteDTO(e.dni, e.nombre, e.apellido, e.edad, e.genero, e.ciudadResidencia, e.numeroLibreta) "
                    +
                    "FROM Estudiante e " +
                    "ORDER BY LOWER(e." + orden + ")";

            return em.createQuery(jpql, EstudianteDTO.class).getResultList();
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    // PUNTO d
    @Override
    public EstudianteDTO getEstudianteLU(int nroLibreta) {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            String jpql = "SELECT new dto.EstudianteDTO(e.dni,e.nombre,e.apellido,e.edad,e.genero,e.ciudadResidencia,e.numeroLibreta) "
                    +
                    "FROM Estudiante e " +
                    "WHERE e.numeroLibreta=:nroLibreta";
            return em.createQuery(jpql, EstudianteDTO.class).setParameter("nroLibreta", nroLibreta)
                    .getSingleResult();
        } catch (NoResultException e) {
            System.err.println("no se encontro el estudiante con nroLibreta: " + nroLibreta);
            return null;
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    // PUNTO e)
    @Override
    public List<EstudianteDTO> getEstudiantesByGenero(String genero) {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            String jpql = "SELECT new dto.EstudianteDTO(e.dni,e.nombre,e.apellido,e.edad,e.genero,e.ciudadResidencia,e.numeroLibreta) "
                    +
                    "FROM Estudiante e " +
                    "WHERE LOWER(e.genero) = LOWER(:genero)";
            List<EstudianteDTO> estudiantes = em.createQuery(jpql, EstudianteDTO.class)
                    .setParameter("genero", genero)
                    .getResultList();

            return estudiantes.isEmpty() ? null : estudiantes;
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
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
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            String jpql = "SELECT new dto.EstudianteDTO(e.dni,e.nombre,e.apellido,e.edad,e.genero,e.ciudadResidencia,e.numeroLibreta) "
                    +
                    "FROM Estudiante e JOIN e.inscripciones m " +
                    "JOIN m.carrera c " +
                    "WHERE LOWER(c.carrera) = LOWER(:carrera) AND LOWER(e.ciudadResidencia) = LOWER(:ciudad)";
            return em.createQuery(jpql, EstudianteDTO.class)
                    .setParameter("carrera", carrera)
                    .setParameter("ciudad", ciudad)
                    .getResultList();
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    // Buscar estudiante por id
    @Override
    public Estudiante findById(long id) {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            return em.find(Estudiante.class, id);
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

}
