package org.example.tp3.controller;

import org.example.tp3.dto.EstudianteDTO;
import org.example.tp3.service.EstudianteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiantes")
public class EstudianteController {
    private final EstudianteService service;

    public EstudianteController(EstudianteService service) {
        this.service = service;
    }

    @GetMapping
    public List<EstudianteDTO> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstudianteDTO save(@RequestBody EstudianteDTO estudiante) {
        return service.save(estudiante);
    }
}
