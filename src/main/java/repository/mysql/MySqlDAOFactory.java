package repository.mysql;

import dao.ClienteDao;
import dao.FacturaDao;
import dao.ProductoDao;
import dao.Factura_ProductoDao;
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

    @Override
    public ClienteDao createClienteDao() { return new MysqlDAOCliente(getConnection()); }

    @Override
    public ProductoDao createProductoDao() { return new MySQLDAOProducto(getConnection()); }

    @Override
    public FacturaDao createFacturaDao() { return new MysqlDAOFactura(getConnection()); }


    public Factura_ProductoDao createFactura_ProductoDao() { return new MySQLDAOFactura_Producto(getConnection()); }
}
