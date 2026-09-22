package repository;
import entity.Carrera;
public interface CarreraRepository {
    void addCarreras(List<Carrera> carreras);
    Carrera findById(int id);
    
} 