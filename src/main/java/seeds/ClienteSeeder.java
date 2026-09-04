package seeds;


import dao.ClienteDao;
import entity.Cliente;
import org.apache.commons.csv.CSVRecord;

public class ClienteSeeder extends Seeder<Cliente>{

    public ClienteSeeder(ClienteDao dao) {
        super(dao);
    }
    @Override
    public Cliente getEntityFromCSV(CSVRecord row) {
        Cliente cliente = new Cliente();
        cliente.setId(Long.parseLong(row.get("idCliente")));
        cliente.setNombre(row.get("nombre"));
        cliente.setEmail(row.get("email"));
        return cliente;
    }
}
