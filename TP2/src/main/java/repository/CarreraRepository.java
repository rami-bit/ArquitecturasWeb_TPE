package repository;
import dto.CarreraInscriptos;

import entity.Carrera;
import dto.ReporteCarreraDTO;
import java.util.List;

public interface CarreraRepository {
    void addCarreras(List<Carrera> carreras);
    Carrera findById(int id);
    List<CarreraInscriptos> getCarrerasConInscriptos();
    void getReporteCarreras();

}