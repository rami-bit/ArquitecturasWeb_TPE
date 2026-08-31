package repository.mysql;

import factory.DAOFactory;

import java.sql.Connection;

public class MySqlDAOFactory extends DAOFactory {
    /**
     * Implementacion MySQL del Factory Method de la conexion.
     * Toda la dependencia con MySQL (driver, URL, usuario, password) queda
     * encerrada en MySQLConnectionManager y solo esta clase lo conoce.
     */
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
