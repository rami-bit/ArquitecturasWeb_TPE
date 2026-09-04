package seeds;

import dao.ProductoDao;
import entity.Producto;
import org.apache.commons.csv.CSVRecord;

public class ProductoSeeder extends Seeder<Producto> {
    public ProductoSeeder (ProductoDao dao){super(dao);}

    @Override
    public Producto getEntityFromCSV(CSVRecord row) {
        return new Producto(
                Long.parseLong(row.get("idProducto")),
                row.get("nombre"),
                Float.parseFloat(row.get("valor"))
        );
    }
}
