package org.example.tp3.model;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString

@Entity
public class Carrera {
    @Id
    private Long id;

    @Column(nullable = false)
    private String carrera;

    @Column(nullable = false)
    private int duracion;

    public Carrera(String carrera, int duracion) {
        this.carrera = carrera;
        this.duracion = duracion;
    }

    @OneToMany(mappedBy = "carrera")
    private List<EstudianteCarrera> inscripciones;

    public Carrera(Long id, String carrera, int duracion) {
        this.id = id;
        this.carrera = carrera;
        this.duracion = duracion;
    }

}
