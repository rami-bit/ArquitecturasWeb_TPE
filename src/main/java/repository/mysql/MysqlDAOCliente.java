package repository.mysql;

import dao.ClienteDao;
import entity.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MysqlDAOCliente implements ClienteDao {

    private final Connection cn;

    public MysqlDAOCliente(Connection cn) {
        this.cn = cn;
        crearTablaSiNoExiste();
    }

    private void crearTablaSiNoExiste() {
        final String sql = "CREATE TABLE IF NOT EXISTS cliente (" +
                "idCliente BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "nombre VARCHAR(500) NOT NULL," +
                "email VARCHAR(150) NOT NULL " +
                ")";
        try (Statement st = cn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando tabla 'usuarios'", e);
        }
    }

    @Override
    public void create(Cliente c) {
        final String sql = "INSERT INTO cliente (idCliente, nombre,email) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), email = VALUES(email)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, c.getId());
            ps.setString(2, c.getNombre());
            ps.setString(3,c.getEmail());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) c.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error en create", e);
        }
    }

    @Override
    public List<Cliente> findAll() {
        final String sql = "SELECT idCliente, nombre,email FROM cliente";
        List<Cliente> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error en findAll", e);
        }
        return out;
    }


    // ---- mapper privado ----
    private Cliente map(ResultSet rs) throws SQLException {
        Cliente c = new Cliente();
        c.setId(rs.getLong("idCliente"));
        c.setNombre(rs.getString("nombre"));
        c.setEmail(rs.getString("email"));
        return c;
    }

    public List<Cliente> findAllOrderedByFacturas() {

        final String sql = "SELECT c.idCliente, c.nombre, c.email, " +
                "SUM(p.valor * fp.cantidad) as totalFacturado " +
                "FROM cliente c " +
                "LEFT JOIN Factura f ON c.idCliente = f.idCliente " +
                "LEFT JOIN factura_producto fp ON f.idFactura = fp.idFactura " +
                "LEFT JOIN productos p ON fp.idProducto = p.id " +
                "GROUP BY c.idCliente " +  // ← espacio al final
                "ORDER BY totalFacturado DESC";

        List<Cliente> out = new ArrayList<>();

        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Cliente c = new Cliente();
                c.setId(rs.getLong("idCliente"));
                c.setNombre(rs.getString("nombre"));
                c.setEmail(rs.getString("email"));
                out.add(c);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en findAllOrderedByFacturas", e);
        }

        return out;
    }
}
