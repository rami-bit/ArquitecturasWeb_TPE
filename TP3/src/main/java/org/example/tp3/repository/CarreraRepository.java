package org.example.tp3.repository;

import org.example.tp3.model.Carrera;

public interface CarreraRepository extends RepoBase<Carrera, Long> {

    // 2.f Carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos
   @Query("SELECT new org.example.tp3.dto.CarreraInscriptosDTO(ec.carrera.id, ec.carrera.nombre, COUNT(ec.estudiante)) " +
           "FROM EstudianteCarrera ec " +
           "GROUP BY ec.carrera.id, ec.carrera.nombre " +
           "HAVING COUNT(ec.estudiante) > 0 " +
           "ORDER BY COUNT(ec.estudiante) DESC")
    List<CarreraInscriptosDTO> getCarrerasConInscriptos();


}
