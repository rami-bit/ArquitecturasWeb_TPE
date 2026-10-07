package org.example.tp3.dto;

import lombok.*;
import org.example.tp3.model.EstudianteCarrera;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class EstudianteCarreraDTO {
    private Long id;
    private Long dni;
    private Long idCarrera;
    private int inscripcion;
    private int antiguedad;
    private int graduacion;

    public static EstudianteCarreraDTO from(EstudianteCarrera e) {
        return new EstudianteCarreraDTO(
                e.getId(),
                e.getEstudiante().getDni(),
                e.getCarrera().getId(),
                e.getInscripcion(),
                e.getAntiguedad(),
                e.getGraduacion()
        );
    }
}
