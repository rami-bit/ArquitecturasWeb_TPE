package seeds;

import entity.Estudiante;
import org.apache.commons.csv.CSVRecord;
import service.Service;

public class EstudianteSeeder extends Seeder<Estudiante> {

    public EstudianteSeeder(Service<Estudiante> service) {
        super(service);
    }

    @Override
    public Estudiante getEntityFromCSV(CSVRecord row) {
        return new Estudiante(
                Long.parseLong(row.get("DNI")),
                row.get("LU"),
                row.get("nombre"),
                row.get("apellido"),
                Integer.parseInt(row.get("edad")),
                row.get("genero"),
                row.get("ciudad")
        );
    }
}
