public class EstudianteCarreraRepositoryImpl implements EstudianteCarreraRepository {
    // cargar matriculas csv
    @Override
    public void matricularEstudiantes(List<EstudianteCarrera> estudiantesCarrera) {
        try {
            EntityManager em = JPAutil.getEntityManager();
            em.getTransaction().begin();
            for(EstudianteCarrera estudianteCarrera:estudiantesCarrera){
                em.persist(estudianteCarrera);
            }
            em.getTransaction().commit();
            em.close();
        }catch (Exception e){
            throw new RuntimeException("error al matricular los estudiantes",e);
        }
    }
}
