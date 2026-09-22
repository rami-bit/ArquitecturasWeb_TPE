package dao;
import java.sql.SQLException;
import java.util.List;

import entity.Factura;

public interface FacturaDao extends Dao<Factura> {

    public int insertFactura(int idFactura, int idCliente) throws SQLException;
    public void updateFactura(Factura idFactura) throws SQLException;
    public void deleteFactura(int idFactura) throws SQLException;
    public Factura getFactura(int idFactura) throws SQLException;
    public List<Factura> getAllFacturas() throws SQLException;
    public void create(Factura factura);

    
}
