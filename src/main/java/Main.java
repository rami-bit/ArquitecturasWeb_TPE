import dao.ClienteDao;
import factory.DAOFactory;
import factory.DBType;

public class Main {
    DAOFactory factory = DAOFactory.getInstance(DBType.MYSQL);
    ClienteDao clienteDao = factory.create();
}
