package org.example.tp3.controller;

import org.example.tp3.dto.CarreraInscriptosDTO;
import org.example.tp3.service.CarreraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carreras")
public class CarreraController {

    @Autowired
    private CarreraService carreraService;

    // Endpoint REST para el punto 2.f
    @GetMapping("/inscriptos")
    public ResponseEntity<List<CarreraInscriptosDTO>> getCarrerasConInscriptos() {
        return ResponseEntity.ok(carreraService.getCarrerasConInscriptos());
    }
}