package entity;

import javax.persistence.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString

@Entity(name="EstudianteCarrera")
public class Inscripcion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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


    public Inscripcion(Estudiante estudiante, Carrera carrera, int inscripcion, int antiguedad, int graduacion) {
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.antiguedad = antiguedad;
        this.graduacion = graduacion;
    }


}
