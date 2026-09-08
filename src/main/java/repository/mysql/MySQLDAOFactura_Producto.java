package repository.mysql;

import dao.Factura_ProductoDao;
import entity.Factura_Producto;
import jdk.jshell.spi.ExecutionControl;
import lombok.SneakyThrows;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLDAOFacturaProducto implements Factura_ProductoDao {

    private final Connection cn;

    public MySQLDAOFacturaProducto(Connection cn) {
        this.cn = cn;
        crearTablaSiNoExiste();
    }

    public void crearTablaSiNoExiste() {

        final String sql = "CREATE TABLE IF NOT EXISTS factura_producto (" +
                "idFactura BIGINT NOT NULL," +
                "idProducto BIGINT NOT NULL," +
                "cantidad INT NOT NULL," +
                "PRIMARY KEY (idFactura, idProducto)," +
                "FOREIGN KEY (idFactura) REFERENCES Factura(idFactura)," +
                "FOREIGN KEY (idProducto) REFERENCES productos(id)" +
                ")";

        try (Statement st = cn.createStatement()) {
            st.execute(sql);

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error creando tabla 'factura_producto'", e);
        }
    }

    @Override
    public void create(Factura_Producto fp) {

        final String sql = "INSERT INTO factura_producto " +
                "(idFactura, idProducto, cantidad) VALUES (?, ?, ?)" +
                "ON DUPLICATE KEY UPDATE cantidad = VALUES(cantidad)";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, fp.getIdFactura());
            ps.setLong(2, fp.getIdProducto());
            ps.setInt(3, fp.getCantidad());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error en create(factura_producto): " + fp, e);
        }
    }

    @Override
    public Factura_Producto findByFacturaAndProducto(
            int idFactura,
            Long idProducto) {

        final String sql = "SELECT idFactura, idProducto, cantidad " +
                "FROM factura_producto " +
                "WHERE idFactura=? AND idProducto=?";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, idFactura);
            ps.setLong(2, idProducto);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return map(rs);
                }

                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error en findByFacturaAndProducto", e);
        }
    }

    @Override
    public List<Factura_Producto> findByProducto(Long idProducto) {

        final String sql = "SELECT idFactura, idProducto, cantidad " +
                "FROM factura_producto " +
                "WHERE idProducto=?";

        List<Factura_Producto> out = new ArrayList<>();

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setLong(1, idProducto);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    out.add(map(rs));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error en findByProducto", e);
        }

        return out;
    }

    @Override
    public List<Factura_Producto> findByFactura(int idFactura) {

        final String sql = "SELECT idFactura, idProducto, cantidad " +
                "FROM factura_producto " +
                "WHERE idFactura=?";

        List<Factura_Producto> out = new ArrayList<>();

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, idFactura);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    out.add(map(rs));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error en findByFactura", e);
        }

        return out;
    }

    @Override
    public List<Factura_Producto> findAll() {

        final String sql = "SELECT idFactura, idProducto, cantidad " +
                "FROM factura_producto";

        List<Factura_Producto> out = new ArrayList<>();

        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(map(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error en findAll", e);
        }

        return out;
    }

    @Override
    public void update(Factura_Producto fp) {

        final String sql = "UPDATE factura_producto " +
                "SET cantidad=? " +
                "WHERE idFactura=? AND idProducto=?";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, fp.getCantidad());
            ps.setInt(2, fp.getIdFactura());
            ps.setLong(3, fp.getIdProducto());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error en update(factura_producto)", e);
        }
    }

    @Override
    public void delete(int idFactura, Long idProducto) {

        final String sql = "DELETE FROM factura_producto " +
                "WHERE idFactura=? AND idProducto=?";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, idFactura);
            ps.setLong(2, idProducto);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error en delete(factura_producto)", e);
        }
    }

    private Factura_Producto map(ResultSet rs) throws SQLException {

        Factura_Producto fp = new Factura_Producto();

        fp.setIdFactura(rs.getInt("idFactura"));
        fp.setIdProducto(rs.getLong("idProducto"));
        fp.setCantidad(rs.getInt("cantidad"));

        return fp;
    }
}
