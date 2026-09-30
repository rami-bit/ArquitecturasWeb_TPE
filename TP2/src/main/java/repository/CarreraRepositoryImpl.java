package repository;
import dto.ReporteCarreraDTO;
import dto.CarreraInscriptos;
import entity.Carrera;
import factory.JPAutil;
import javax.persistence.EntityManager;
import java.util.List;

public class CarreraRepositoryImpl implements CarreraRepository {
    @Override
    public void addCarreras(List<Carrera> carreras) {
        EntityManager em = null;
        try{
            em = JPAutil.getEntityManager();
            em.getTransaction().begin();
            for(Carrera carrera:carreras){
                em.persist(carrera);
            }
            em.getTransaction().commit();
        }catch (Exception e){
            if (em != null && em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("error al cargar las carreras",e);
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }

    @Override
    public Carrera findById(int id) {
        EntityManager em = JPAutil.getEntityManager();
        Carrera carrera = em.find(Carrera.class, (long) id);
        em.close();
        return carrera;
    }

    @Override
    public List<CarreraInscriptos> getCarrerasConInscriptos() {
        EntityManager em = null;
        List<CarreraInscriptos> carrerasConInscriptos = null;
        try{
            em = JPAutil.getEntityManager();
            carrerasConInscriptos = em.createQuery("SELECT new dto.CarreraInscriptos(ec.carrera.carrera, COUNT(*))" +
                    " FROM EstudianteCarrera ec GROUP BY ec.carrera ORDER BY COUNT(*) DESC"
                    , CarreraInscriptos.class).getResultList();
        } finally {
            if (em != null){
                em.close();
            }
        }
        return carrerasConInscriptos;
    }

    @Override
    public void getReporteCarreras() {
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            List<ReporteCarreraDTO> reporte = em.createQuery(
                            "SELECT new dto.ReporteCarreraDTO(" +
                                    "  c.carrera, " +
                                    "  ec.inscripcion, " +
                                    "  COUNT(ec), " +
                                    "  SUM(CASE WHEN ec.graduacion > 0 THEN 1L ELSE 0L END)) " +
                                    "FROM Carrera c LEFT JOIN EstudianteCarrera ec ON ec.carrera = c " +
                                    "GROUP BY c.carrera, ec.inscripcion " +
                                    "ORDER BY c.carrera ASC, ec.inscripcion ASC",
                            ReporteCarreraDTO.class)
                    .getResultList();

            imprimirReporte(reporte);   // se invoca acá, al final de la consulta
        } finally {
            if (em != null) em.close();
        }
    }

    private void imprimirReporte(List<ReporteCarreraDTO> reporte) {
        String carreraActual = "";
        for (ReporteCarreraDTO fila : reporte) {
            if (!fila.getCarrera().equals(carreraActual)) {
                System.out.println("\nCarrera: " + fila.getCarrera());
                carreraActual = fila.getCarrera();
            }
            if (fila.getAnio() == null) {
                System.out.println("  Sin inscriptos");
            } else {
                System.out.println("  Año " + fila.getAnio() +
                        " | Inscriptos: " + fila.getInscriptos() +
                        " | Egresados: " + fila.getEgresados());
            }
        }
    }
}
