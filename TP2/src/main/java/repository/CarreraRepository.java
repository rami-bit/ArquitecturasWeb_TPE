package repository;
import dto.CarreraInscriptos;
import entity.Carrera;

import java.util.List;

public interface CarreraRepository {
    void addCarreras(List<Carrera> carreras);
    Carrera findById(int id);
    List<CarreraInscriptos> getCarrerasConInscriptos();
} 