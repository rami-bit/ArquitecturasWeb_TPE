package entity;

import javax.persistence.*;
import lombok.*;

@NoArgsConstructor
@Getter
@ToString

@Entity
public class EstudianteCarrera {
    @Id
    private Long id;

    @ManyToOne
    private Estudiante estudiante;

    @ManyToOne
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
