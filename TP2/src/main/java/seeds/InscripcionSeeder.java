package seeds;

import entity.Carrera;
import entity.Estudiante;
import entity.Inscripcion;
import org.apache.commons.csv.CSVRecord;
import service.Service;

public class InscripcionSeeder extends Seeder<Inscripcion> {
    private Service<Estudiante> serviceEstudiante;
    private Service<Carrera> serviceCarrera;

    public InscripcionSeeder(Service<Inscripcion> service,
                             Service<Estudiante> serviceEstudiante,
                             Service<Carrera> serviceCarrera) {
        super(service);
        this.serviceEstudiante = serviceEstudiante;
        this.serviceCarrera = serviceCarrera;
    }

    @Override
    public Inscripcion getEntityFromCSV(CSVRecord row) {
        Estudiante estudiante = serviceEstudiante.findById(Long.parseLong(row.get("id_estudiante")));
        Carrera carrera = serviceCarrera.findById(Long.parseLong(row.get("id_carrera")));
        return new Inscripcion(
                estudiante,
                carrera,
                Integer.parseInt(row.get("inscripcion")),
                Integer.parseInt(row.get("antiguedad")),
                Integer.parseInt(row.get("graduacion"))
        );
    }
}
