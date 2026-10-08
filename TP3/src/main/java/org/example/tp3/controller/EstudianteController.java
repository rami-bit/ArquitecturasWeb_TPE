package org.example.tp3.controller;

import org.example.tp3.dto.EstudianteDTO;
import org.example.tp3.service.EstudianteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/orden")
    public List<EstudianteDTO> ordenar(@RequestParam String orden) {
        return service.findAllOrderBy(orden);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?>getOne(@PathVariable Long id){
        try{
            return ResponseEntity.status(HttpStatus.OK).body(service.findById(id));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\":\"Error. No se encuentra el objeto buscado" +
                    ".\"}");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstudianteDTO save(@RequestBody EstudianteDTO estudiante) {
        return service.save(estudiante);
    }
}
