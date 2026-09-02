package seeds;

import repository.mysql.MysqlDAOCliente;

import java.sql.Connection;

public class DatabaseSeeder {
    // Coordina los seeders
    public DatabaseSeeder(Connection connection){
        ClienteSeeder clientes = new ClienteSeeder(new MysqlDAOCliente(connection));
        clientes.seed("/csv/clientes.csv");
    }
}
