
package seeds;

import dao.Factura_ProductoDao;
import entity.Factura_Producto;
import org.apache.commons.csv.CSVRecord;

public class FacturaProductoSeeder extends Seeder<Factura_Producto> {

    public FacturaProductoSeeder(Factura_ProductoDao dao) {
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
