# Diagrama de objetos — TP2

Diagrama de clases/estructura de objetos del TP2: los tres tipos con sus atributos y tipos Java, y las relaciones según las anotaciones JPA. A diferencia del DER (que muestra el nivel físico MySQL con tipos y claves), este diagrama muestra el modelo de objetos de la aplicación. Los valores de instancia concretos (datos de los CSV) no se representan acá.

```mermaid
classDiagram
    Estudiante "1" --> "0..*" EstudianteCarrera : estudiante
    Carrera "1" --> "0..*" EstudianteCarrera : carrera

    class Estudiante {
        -Long dni
        -int numeroLibreta
        -String nombre
        -String apellido
        -int edad
        -String genero
        -String ciudadResidencia
        +Estudiante(dni, numeroLibreta, nombre, apellido, edad, genero, ciudadResidencia)
    }

    class Carrera {
        -Long id
        -String carrera
        -int duracion
        +Carrera(id, carrera, duracion)
        +Carrera(carrera, duracion)
    }

    class EstudianteCarrera {
        -Long id
        -Estudiante estudiante
        -Carrera carrera
        -int inscripcion
        -int graduacion
        -int antiguedad
        +EstudianteCarrera(id, estudiante, carrera, inscripcion, graduacion, antiguedad)
        +EstudianteCarrera(estudiante, carrera, inscripcion, antiguedad, graduacion)
    }
```

## Notas

- **Cardinalidades**: 1 estudiante → 0..* matrículas; 1 carrera → 0..* inscriptos; cada matrícula → exactamente 1 estudiante y 1 carrera (derivable de `@OneToMany(mappedBy)` + `@ManyToOne`).
- **`EstudianteCarrera`** es la entidad asociativa de la relación N:N Estudiante–Carrera; sus atributos (`inscripcion`, `graduacion`, `antiguedad`) son los datos propios de la inscripción.
- Atributos visibles como `private` (los accesos son vía Lombok `@Getter`).
- Las colecciones `inscripciones` del lado inverso (`mappedBy`) no se dibujan: están representadas por los enlaces.
- `graduacion = 0` significa **en curso** (convención de datos de los CSV).
- Todos los atributos son de tipo primitivo/objeto Java (`Long`, `int`, `String`); los tipos físicos (`bigint`, `int`, `varchar`) están en el [DER](DER.md).
