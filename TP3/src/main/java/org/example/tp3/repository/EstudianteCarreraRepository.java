package org.example.tp3.repository;

import org.example.tp3.model.EstudianteCarrera;

public interface EstudianteCarreraRepository extends RepoBase<EstudianteCarrera, Long> {
    boolean existsByEstudiante_DniAndCarrera_Id(Long dni, Long idCarrera);
}
