public class MySQLDAOPersona implements ProductoDao {
    
    void create(Producto p){

    };
    Producto findById(Long id){

    };
    @Override 
    public List<Producto> findAll() {
        final String sql = "SELECT id, nombre, stock, precio FROM productos";
        List<Producto> out = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        catch (SQLException e) { 
            throw new RuntimeException("Error en findAll", e); 
        }
        return out;
    }

    @Override 
    public void update(Producto p) {
        final String sql = "UPDATE productos SET nombre=?, stock=?, precio=? WHERE id=?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, p.getNombre());
            if (p.getStock() == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, p.getStock());
            if (p.getPrecio() == null) ps.setNull(3, Types.FLOAT); else ps.setFloat(3, p.getPrecio());
            ps.setLong(4, p.getId());
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
    };

}
