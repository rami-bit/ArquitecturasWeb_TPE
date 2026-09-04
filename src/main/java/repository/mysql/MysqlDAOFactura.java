package repository.mysql;

import dao.FacturaDao;
import entity.Factura;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;




public class MysqlDAOFactura implements FacturaDao{
    private final Connection cn; 

    public MysqlDAOFactura(Connection cn) {
        this.cn = cn;
        crearTablaSiNoExiste();
    }
    private void crearTablaSiNoExiste() {
        final String sql = "CREATE TABLE IF NOT EXISTS Factura (" +
                "idFactura BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "idCliente BIGINT NOT NULL," +
                "FOREIGN KEY (idCliente) REFERENCES cliente(idCliente)" +
                ")";
        try (Statement st = cn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando tabla 'factura'", e);
        }
    }
   
    @Override
    public void create(Factura factura) {
        String query = "INSERT INTO Factura (idFactura, idCliente) VALUES (?, ?)";
        try (PreparedStatement ps = cn.prepareStatement(query)) {
            ps.setInt(1, factura.getIdFactura());
            ps.setInt(2, factura.getIdCliente());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No se pudo insertar la factura.");
            }
            cn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error al insertar la factura", e);
        } 
    }
    @Override 
    public int insertFactura(int idFactura, int idCliente) throws SQLException {
        String query = "INSERT INTO Factura (idFactura, idCliente) VALUES ( ?, ?)";
        PreparedStatement ps = cn.prepareStatement(query);
        try{
            ps.setInt(1, idFactura);
            ps.setInt(2, idCliente);
            if (ps.executeUpdate() == 0) {
                throw new Exception("No se pudo insertar");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            ps.close();
            cn.commit();
        }
        return 0;
    }
    @Override 
    public void updateFactura(Factura factura) throws SQLException{
        String select = "SELECT * FROM Factura WHERE idFactura = ?";
        PreparedStatement psSelect = null;
        PreparedStatement psUpdate = null;
        ResultSet rs = null;
        try{
            psSelect = cn.prepareStatement(select);
            psSelect.setInt(1, factura.getIdFactura()); 
            rs = psSelect.executeQuery();
           // si la factura existe, actualizamos 
            if(rs.next()){
                // consulta para actualizar la factura
                String update = "UPDATE Factura SET idCliente = ? WHERE idFactura = ?";
                // preparando la actualizacion
                psUpdate = cn.prepareStatement(update); 
                psUpdate.setInt(1, factura.getIdCliente());
                psUpdate.setInt(2, factura.getIdFactura());
                // ejecutando la actualizacion
                int rowsAffected = psUpdate.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Factura actualizado con éxito.");
                } else {
                    System.out.println("No se pudo actualizar la factura.");
                }
            }else{
                System.out.println("La factura con id " + factura.getIdFactura() + " no existe.");
            }
        }catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // cerrar ResultSet y PreparedStatement
             if(rs != null) rs.close();
            if(psSelect != null) psSelect.close();
            if(psUpdate != null) psUpdate.close();
            cn.commit();
        }
    
    }
     
    @Override
    public void deleteFactura(int idFactura) throws SQLException {
        //Consulta para verificar si la factura existe
        String select = "SELECT * FROM Factura WHERE idFactura = ?";
        // consulta para eliminar la factura
        String delete = "DELETE FROM Factura WHERE idFactura = ?";
        PreparedStatement psSelect = null;
        PreparedStatement psDelete = null;
        ResultSet rs = null;
        try {
            // preparar el select para verificar si la factura existe
            psSelect = cn.prepareStatement(select); 
            psSelect.setInt(1, idFactura);
            rs = psSelect.executeQuery();
            // si la factura existe, eliminamos
            if (rs.next()) {
                // preparar el delete
                psDelete = cn.prepareStatement(delete);
                psDelete.setInt(1, idFactura);
                // ejecutar el delete
                int rowsAffected = psDelete.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Factura eliminada con éxito.");
                } else {
                    System.out.println("No se pudo eliminar la factura.");
                }
            } else {
                System.out.println("La factura con id " + idFactura + " no existe.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw e;
        } finally {
            // cerrar ResultSet y PreparedStatement
            if(rs != null) rs.close();
            if(psSelect != null) psSelect.close();
            if(psDelete != null) psDelete.close();
            cn.commit();
        }
    }
    @Override
    public Factura getFactura(int idFactura) throws SQLException {
        String select = "SELECT * FROM Factura WHERE idFactura = ?";
        PreparedStatement ps = cn.prepareStatement(select);
        ps.setInt(1, idFactura);
        ResultSet rs = ps.executeQuery();
        if(rs.next()){
            return new Factura(rs.getInt("idFactura"), rs.getInt("idCliente"));
        }
        else return null;
    }
    @Override
    public List<Factura> getAllFacturas() throws SQLException {
        ArrayList<Factura> facturas = new ArrayList<>();
        String select = "SELECT * FROM Factura";
        PreparedStatement ps = cn.prepareStatement(select);
        ResultSet rs = ps.executeQuery();
        while(rs.next()){
            facturas.add(new Factura(rs.getInt("idFactura"), rs.getInt("idCliente")));
        }
        return facturas;
    }

}
