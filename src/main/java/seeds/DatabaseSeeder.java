package seeds;

import factory.DAOFactory;

public class DatabaseSeeder {
    // Coordina los seeders
    public DatabaseSeeder(DAOFactory factory){
        ClienteSeeder clientes = new ClienteSeeder(factory.createClienteDao());
        clientes.seed("src/main/resources/csv/clientes.csv");

        ProductoSeeder productos = new ProductoSeeder(factory.createProductoDao());
        productos.seed("src/main/resources/csv/productos.csv");

        FacturaSeeder facturas = new FacturaSeeder(factory.createFacturaDao());
        facturas.seed("src/main/resources/csv/facturas.csv");

        FacturaProductoSeeder facturaProducto = new FacturaProductoSeeder(factory.createFacturaProductoDao());
        facturaProducto.seed("src/main/resources/csv/facturas-productos.csv");
    }
}
