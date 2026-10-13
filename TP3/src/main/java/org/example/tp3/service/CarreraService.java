package org.example.tp3.service;

import org.example.tp3.dto.CarreraInscriptosDTO;
import org.example.tp3.dto.ReporteAnioDTO;
import org.example.tp3.dto.ReporteCarreraDTO;
import org.example.tp3.model.Carrera;
import org.example.tp3.repository.CarreraRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CarreraService {

    @Autowired
    private CarreraRepository carreraRepository;

   public List<CarreraInscriptosDTO> getCarrerasConInscriptos() {
        return carreraRepository.getCarrerasConInscriptos();
    }
}