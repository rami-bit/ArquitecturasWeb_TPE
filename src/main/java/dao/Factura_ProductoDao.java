package dao;

import entity.Factura_Producto;
import java.util.List;

public interface Factura_ProductoDao extends Dao<Factura_Producto> {

    Factura_Producto findByFacturaAndProducto(int idFactura, Long idProducto);
    List<Factura_Producto> findAll();
    List<Factura_Producto> findByFactura(int idFactura);
    List<Factura_Producto> findByProducto(Long idProducto);

    void update(Factura_Producto fp);

    void delete(int idFactura, Long idProducto);

    void create(Factura_Producto fp);
}
