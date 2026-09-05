
package seeds;

import dao.Factura_ProductoDao;
import entity.Factura_Producto;
import org.apache.commons.csv.CSVRecord;

public class Factura_ProductoSeeder extends Seeder<Factura_Producto> {

    public Factura_ProductoSeeder(Factura_ProductoDao dao) {
        super(dao);
    }

    @Override
    public Factura_Producto getEntityFromCSV(CSVRecord row) {
 
        return new Factura_Producto(
                Integer.parseInt(row.get("idFactura")),
                Long.parseLong(row.get("idProducto")),
                Integer.parseInt(row.get("cantidad"))
        );
        
    }
}
