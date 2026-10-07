package org.example.tp3.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.tp3.model.Estudiante;


@AllArgsConstructor
@Getter
public class EstudianteDTO {
    private Long dni;
    private String nombre;
    private String apellido;
    private int edad;
    private String genero;
    private String ciudad;
    private int nroLibreta;

    public static EstudianteDTO from(Estudiante e) {
        return new EstudianteDTO(
                e.getDni(),
                e.getNombre(),
                e.getApellido(),
                e.getEdad(),
                e.getGenero(),
                e.getCiudadResidencia(),
                e.getNumeroLibreta()
        );
    }

    @Override
    public String toString() {
        return "{" +
                "dni=" + dni +
                ", nombre='" + nombre + '\'' +
                ", apellido='" + apellido + '\'' +
                ", edad=" + edad +
                ", genero='" + genero + '\'' +
                ", ciudad='" + ciudad + '\'' +
                ", nroLibreta=" + nroLibreta +
                '}';
    }
}

