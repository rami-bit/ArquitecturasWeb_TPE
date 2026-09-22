package seeds;

import entity.Carrera;
import entity.Estudiante;
import entity.Inscripcion;
import service.Service;

public class DatabaseSeeder {
    public DatabaseSeeder(Service<Carrera> serviceCarrera,
                          Service<Estudiante> serviceEstudiante,
                          Service<Inscripcion> serviceInscripcion) {

        CarreraSeeder carreras = new CarreraSeeder(serviceCarrera);
        carreras.seed("csv/carreras.csv");

        EstudianteSeeder estudiantes = new EstudianteSeeder(serviceEstudiante);
        estudiantes.seed("csv/estudiantes.csv");

        InscripcionSeeder inscripciones = new InscripcionSeeder(serviceInscripcion, serviceEstudiante, serviceCarrera);
        inscripciones.seed("csv/estudianteCarrera.csv");
    }
}
