package repository;
import dto.ReporteCarreraDTO;
import dto.CarreraInscriptos;
import entity.Carrera;
import factory.JPAutil;
import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        EntityManager em = null;
        try {
            em = JPAutil.getEntityManager();
            return em.find(Carrera.class, (long) id);
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
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
            List<Object[]> carreras = em.createQuery(
                            "SELECT c.id, c.carrera FROM Carrera c ORDER BY c.carrera ASC, c.id ASC",
                            Object[].class)
                    .getResultList();
            List<Object[]> inscriptosPorAnio = em.createQuery(
                            "SELECT ec.carrera.id, ec.inscripcion, COUNT(ec) " +
                                    "FROM EstudianteCarrera ec " +
                                    "GROUP BY ec.carrera.id, ec.inscripcion",
                            Object[].class)
                    .getResultList();
            List<Object[]> egresadosPorAnio = em.createQuery(
                            "SELECT ec.carrera.id, ec.graduacion, COUNT(ec) " +
                                    "FROM EstudianteCarrera ec " +
                                    "WHERE ec.graduacion > 0 " +
                                    "GROUP BY ec.carrera.id, ec.graduacion",
                            Object[].class)
                    .getResultList();

            Map<Long, String> nombresCarreras = new LinkedHashMap<>();
            Map<Long, TreeMap<Integer, long[]>> totalesPorCarreraYAnio = new LinkedHashMap<>();

            for (Object[] carrera : carreras) {
                Long carreraId = (Long) carrera[0];
                nombresCarreras.put(carreraId, (String) carrera[1]);
                totalesPorCarreraYAnio.put(carreraId, new TreeMap<>());
            }

            acumularTotales(inscriptosPorAnio, totalesPorCarreraYAnio, 0);
            acumularTotales(egresadosPorAnio, totalesPorCarreraYAnio, 1);

            List<ReporteCarreraDTO> reporte = new ArrayList<>();
            for (Map.Entry<Long, String> carrera : nombresCarreras.entrySet()) {
                TreeMap<Integer, long[]> totalesPorAnio = totalesPorCarreraYAnio.get(carrera.getKey());
                if (totalesPorAnio.isEmpty()) {
                    reporte.add(new ReporteCarreraDTO(carrera.getValue(), null, 0L, 0L));
                    continue;
                }

                for (Map.Entry<Integer, long[]> totalAnual : totalesPorAnio.entrySet()) {
                    long[] totales = totalAnual.getValue();
                    reporte.add(new ReporteCarreraDTO(
                            carrera.getValue(), totalAnual.getKey(), totales[0], totales[1]));
                }
            }

            imprimirReporte(reporte);
        } finally {
            if (em != null) em.close();
        }
    }

    private void acumularTotales(List<Object[]> filas,
                                 Map<Long, TreeMap<Integer, long[]>> totalesPorCarreraYAnio,
                                 int posicionTotal) {
        for (Object[] fila : filas) {
            Long carreraId = (Long) fila[0];
            Integer anio = (Integer) fila[1];
            Long cantidad = (Long) fila[2];
            long[] totales = totalesPorCarreraYAnio.get(carreraId)
                    .computeIfAbsent(anio, clave -> new long[2]);
            totales[posicionTotal] = cantidad;
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
