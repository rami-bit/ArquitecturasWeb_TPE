package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CarreraInscriptos {
    private String carrera;
    private Long cantidadInscriptos;

    @Override
    public String toString() {
        return "CarreraInscriptos{" +
                "carrera='" + carrera + '\'' +
                ", cantidadInscriptos=" + cantidadInscriptos +
                '}';
    }
}
