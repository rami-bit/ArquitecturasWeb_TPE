package org.example.tp3.repository;

import org.example.tp3.model.EstudianteCarrera;
import java.util.List;

public interface EstudianteCarreraRepository extends RepoBase<EstudianteCarrera, Long> {
    boolean existsByEstudiante_DniAndCarrera_Id(Long dni, Long idCarrera);

    List<EstudianteCarrera> findByCarrera_CarreraAndEstudiante_CiudadResidencia(String carrera, String ciudad);
}
