package entity;

import javax.persistence.*;
import lombok.*;

@NoArgsConstructor
@Getter
@ToString

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_estudiante_carrera",
                columnNames = {"estudiante_dni", "carrera_id"}
        )
)
public class EstudianteCarrera {
    @Id
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estudiante_dni", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "carrera_id", nullable = false)
    private Carrera carrera;

    @Column
    private int inscripcion;

    @Column
    private int antiguedad;

    @Column
    private int graduacion;


    public EstudianteCarrera(Long id, Estudiante estudiante, Carrera carrera, int inscripcion, int graduacion, int antiguedad) {
        this.id = id;
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
        this.antiguedad = antiguedad;
    }

    public EstudianteCarrera(Estudiante estudiante, Carrera carrera, int inscripcion, int antiguedad, int graduacion) {
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.antiguedad = antiguedad;
        this.graduacion = graduacion;
    }


}
