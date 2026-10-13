package org.example.tp3.repository;

import org.example.tp3.dto.CarreraInscriptosDTO;
import org.example.tp3.model.Carrera;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CarreraRepository extends RepoBase<Carrera, Long> {

    // 2.f Carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos
   @Query("SELECT new org.example.tp3.dto.CarreraInscriptosDTO(ec.carrera.id, ec.carrera.carrera, COUNT(ec.estudiante)) " +
           "FROM EstudianteCarrera ec " +
           "GROUP BY ec.carrera.id, ec.carrera.carrera " +
           "HAVING COUNT(ec.estudiante) > 0 " +
           "ORDER BY COUNT(ec.estudiante) DESC")
   List<CarreraInscriptosDTO> getCarrerasConInscriptos();


}
