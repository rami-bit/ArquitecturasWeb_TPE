package factory;

import java.sql.Connection;

/**
 * CONTRATO que debe cumplir el gestor de conexiones de CADA motor.
 */
public interface ConnectionManager {

    /** Devuelve la conexión abierta contra el motor correspondiente. */
    Connection getConnection();

    void shutdown();
}
