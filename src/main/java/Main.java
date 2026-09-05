import factory.DAOFactory;
import factory.DBType;
import seeds.DatabaseSeeder;

import java.sql.SQLException;

public class Main {
    public static void main (String [] args) throws Exception {
        DAOFactory factory = DAOFactory.getInstance(DBType.MYSQL);
        new DatabaseSeeder(factory);
        System.out.println(factory.createClienteDao().findAll().stream().findFirst());
        System.out.println(factory.createProductoDao().findAll().stream().findFirst());
        System.out.println(factory.createFacturaDao().getAllFacturas().stream().findFirst());
        System.out.println(factory.createFacturaProductoDao().findAll().stream().findFirst());

    }

}
