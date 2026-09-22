package seeds;

import factory.DAOFactory;

public class DatabaseSeeder {
    // Coordina los seeders
    public DatabaseSeeder(DAOFactory factory){
        ClienteSeeder clientes = new ClienteSeeder(factory.createClienteDao());
        clientes.seed("csv/clientes.csv");

        ProductoSeeder productos = new ProductoSeeder(factory.createProductoDao());
        productos.seed("csv/productos.csv");

        FacturaSeeder facturas = new FacturaSeeder(factory.createFacturaDao());
        facturas.seed("csv/facturas.csv");

        FacturaProductoSeeder facturaProducto = new FacturaProductoSeeder(factory.createFacturaProductoDao());
        facturaProducto.seed("csv/facturas-productos.csv");
    }
}
