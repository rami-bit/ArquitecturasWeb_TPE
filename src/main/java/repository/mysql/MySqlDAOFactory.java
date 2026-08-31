package repository.mysql;

import factory.DAOFactory;

import java.sql.Connection;

public class MySqlDAOFactory extends DAOFactory {
    @Override
    protected Connection getConnection() {
        return MySQLConnectionManager.getInstance().getConnection();
    }

    /** Cierre especifico de MySQL: delega en su propio gestor de conexiones. */
    @Override
    protected void doShutdown() {
        MySQLConnectionManager.getInstance().shutdown();
    }

}
