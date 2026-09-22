import entity.Carrera;
import entity.Estudiante;
import entity.Inscripcion;
import repository.RepositoryCarrera;
import repository.RepositoryEstudiante;
import repository.RepositoryInscripcion;
import seeds.DatabaseSeeder;
import service.ServiceCarrera;
import service.ServiceEstudiante;
import service.ServiceInscripcion;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class Main {
    public static void main(String[] args) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("Tp2");
        EntityManager em = emf.createEntityManager();

        ServiceCarrera serviceCarrera = new ServiceCarrera(new RepositoryCarrera(em));
        ServiceEstudiante serviceEstudiante = new ServiceEstudiante(new RepositoryEstudiante(em));
        ServiceInscripcion serviceInscripcion = new ServiceInscripcion(new RepositoryInscripcion(em));

        em.getTransaction().begin();
        new DatabaseSeeder(serviceCarrera, serviceEstudiante, serviceInscripcion);
        em.getTransaction().commit();

        em.close();
        emf.close();
    }
}
