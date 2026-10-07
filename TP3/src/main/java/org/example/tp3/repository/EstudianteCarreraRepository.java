package org.example.tp3.repository;

import org.example.tp3.model.EstudianteCarrera;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstudianteCarreraRepository extends JpaRepository<EstudianteCarrera, Long> {
    boolean existsByEstudiante_DniAndCarrera_Id(Long dni, Long idCarrera);
}
