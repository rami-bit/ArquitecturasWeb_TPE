package seeds;

import dao.FacturaDao;
import entity.Factura;
import org.apache.commons.csv.CSVRecord;

public class FacturaSeeder extends Seeder<Factura> {

    public FacturaSeeder(FacturaDao dao) { super(dao);}
    @Override
    public Factura getEntityFromCSV(CSVRecord row) {

        
        return new Factura(
                Integer.parseInt(row.get("idFactura")),
                Integer.parseInt(row.get("idCliente"))
        );
    }
}
