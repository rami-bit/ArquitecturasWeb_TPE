package seeds;

import entity.Carrera;
import org.apache.commons.csv.CSVRecord;
import service.Service;

public class CarreraSeeder extends Seeder<Carrera> {

    public CarreraSeeder(Service<Carrera> service) {
        super(service);
    }

    @Override
    public Carrera getEntityFromCSV(CSVRecord row) {
        return new Carrera(
                row.get("carrera"),
                Integer.parseInt(row.get("duracion"))
        );
    }
}
