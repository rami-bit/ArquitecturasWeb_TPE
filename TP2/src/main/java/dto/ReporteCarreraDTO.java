package dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class ReporteCarreraDTO {
    private String carrera;
    private Integer anio;
    private Long inscriptos;
    private Long egresados;
}