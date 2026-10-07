package org.example.tp3.controller;

import org.example.tp3.dto.EstudianteCarreraDTO;
import org.example.tp3.service.EstudianteCarreraService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inscripciones")
public class EstudianteCarreraController {
    private final EstudianteCarreraService service;

    public EstudianteCarreraController(EstudianteCarreraService service) {
        this.service = service;
    }

    @GetMapping
    public List<EstudianteCarreraDTO> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstudianteCarreraDTO matricular(@RequestBody EstudianteCarreraDTO dto) {
        return service.matricular(dto);
    }
}
