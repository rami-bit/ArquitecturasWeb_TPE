package entity;
import lombok.*; 

@Getter
@Setter 
@AllArgsConstructor
@NoArgsConstructor
@ToString

public class Factura {
    private int idFactura;
    private int idCliente;
}
