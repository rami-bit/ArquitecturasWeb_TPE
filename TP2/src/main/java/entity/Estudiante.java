package entity;

import javax.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;


@NoArgsConstructor
@Getter
@ToString


@Entity
public class Estudiante {
    @Id
    private Long dni;

    @Column(unique = true)
    private String numeroLibreta;

    @Column
    private String nombre;

    @Column
    private String apellido;

    @Column
    private int edad;

    @Column
    private String genero;

    @Column(nullable = false)
    private String ciudadResidencia;

    @OneToMany(mappedBy = "estudiante")
    private List<Inscripcion> inscripciones;

    public Estudiante(Long dni,String numeroLibreta, String nombre, String apellido, int edad, String genero, String ciudadResidencia) {
        this.dni = dni;
        this.numeroLibreta = numeroLibreta;
        this.nombre = nombre;
        this.apellido = apellido;
        this.edad = edad;
        this.genero = genero;
        this.ciudadResidencia = ciudadResidencia;
        this.inscripciones = new ArrayList<>();
    }


}
