package org.example.tp3.repository;

import org.example.tp3.model.Estudiante;
import org.springframework.data.domain.Sort;
import java.util.List;

public interface EstudianteRepository extends RepoBase<Estudiante, Long> {

    List<Estudiante> findAll(Sort sort);

}
