package entity;
import lombok.*; 

@Getter
@Setter 
@AllArgsConstructor
@NoArgsConstructor
@ToString

public class Factura_Producto {

    private int idFactura;
    private Long idProducto;
    private int cantidad;
}