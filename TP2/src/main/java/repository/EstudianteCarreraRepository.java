package repository;
import entity.EstudianteCarrera;

import java.util.List;

public interface EstudianteCarreraRepository {
       void matricularEstudiante(long nroLibreta, int carreraId, int inscripcion, int antiguedad, int graduacion);
       void matricularEstudiantes(List<EstudianteCarrera> estudiantesCarrera);
}
