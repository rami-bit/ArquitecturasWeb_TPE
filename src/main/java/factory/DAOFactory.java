package factory;


//import  repository.derby.DerbyDAOFactory;

import repository.mysql.MySqlDAOFactory;

import java.sql.Connection;

public abstract class DAOFactory {
    /// Singleton del factory
    private static volatile DAOFactory instance;


    public static DAOFactory getInstance(DBType type) { // Realiza un DCL = Double-Checked Locking (bloqueo con doble verificación).
        if (instance == null) { // 1er chequeo
            synchronized (DAOFactory.class) { // Bloque sincronizado
                if (instance == null) { // 2do chequeo
                    switch (type) {
                        case MYSQL:
                            instance = new MySqlDAOFactory();
                            break;

                        case DERBY:
                            instance = new DerbyDAOFactory();
                            break;

                        // case POSTGRES:
                        //     instance = new PostgresAOFactory();
                        //     break;
                        default:
                            throw new IllegalArgumentException("DBType no soportado: " + type);
                    }
                }
            }
        }
        return instance;
    }


    public static DAOFactory getInstance() {
        String v = System.getProperty("db.type", "MYSQL");  // lee una “system property” llamada db.type. Si no existe, usa "MYSQL" como valor por defecto.
        DBType type = DBType.valueOf(v.toUpperCase());
        return getInstance(type);
    }


    protected abstract Connection getConnection();

    public final void shutdown() {
        doShutdown();
        synchronized (DAOFactory.class) {
            instance = null;
        }
    }

    /**
     * Cada fabrica concreta cierra SU gestor de conexiones.
     */
    protected abstract void doShutdown();

}
