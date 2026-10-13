package org.example.tp3.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CarreraInscriptosDTO {
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
