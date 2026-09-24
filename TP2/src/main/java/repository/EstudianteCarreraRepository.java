package repository;
import entity.EstudianteCarrera;

import java.util.List;

public interface EstudianteCarreraRepository {
       void matricularEstudiantes(List<EstudianteCarrera> estudiantesCarrera);
}
