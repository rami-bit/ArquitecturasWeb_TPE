package repository.mysql;

import dao.ProductoDao;
import entity.Producto;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLDAOProducto implements ProductoDao {
    private final Connection cn;

    public MySQLDAOProducto(Connection cn) {
        this.cn = cn;
        CreateTable();
    }

    @Override
    public void create(Producto p){
        final String sql = "INSERT INTO productos (nombre, valor) VALUES (?,?)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getNombre());
            if (p.getValor() == null) ps.setNull(2, Types.FLOAT); else ps.setFloat(2, p.getValor());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) p.setId(keys.getLong(1)); }
        } catch (SQLException e) { throw new RuntimeException("Error en create(producto)", e); }
    }

    @Override
    public Producto findById(Long id){
        final String sql = "SELECT id, nombre, valor FROM productos WHERE id=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        } catch (SQLException e) { throw new RuntimeException("Error en findById", e); }
    }

    public void CreateTable(){
        final String sql = "CREATE TABLE IF NOT EXISTS productos (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "nombre VARCHAR(120) NOT NULL," +
                "valor FLOAT" +
                ")";
        try (Statement st = cn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando tabla 'productos'", e);
        }
    }

    @Override
    public List<Producto> findAll() {
        final String sql = "SELECT id, nombre, valor FROM productos";
        List<Producto> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error en findAll", e);
        }
        return out;
    }

    @Override
    public void update(Producto p) {
        final String sql = "UPDATE productos SET nombre=?, valor=? WHERE id=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, p.getNombre());
            if (p.getValor() == null) ps.setNull(2, Types.FLOAT); else ps.setFloat(2, p.getValor());
            ps.setLong(3, p.getId());
            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException("Error en update(producto)", e);
        }
    }

    @Override
    public void delete(Long id) {
        final String sql = "DELETE FROM productos WHERE id=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, id); ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException("Error en delete(producto)", e);
        }
    }

    private Producto map(ResultSet rs) throws SQLException {
        Producto p = new Producto();
        p.setId(rs.getLong("id"));
        p.setNombre(rs.getString("nombre"));
        float f = rs.getFloat("valor");
        p.setValor(rs.wasNull() ? null : Float.valueOf(f));
        return p;
    }
}
