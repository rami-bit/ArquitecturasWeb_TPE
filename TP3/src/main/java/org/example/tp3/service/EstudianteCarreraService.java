package org.example.tp3.service;

import org.example.tp3.dto.EstudianteCarreraDTO;
import org.example.tp3.model.Carrera;
import org.example.tp3.model.Estudiante;
import org.example.tp3.model.EstudianteCarrera;
import org.example.tp3.repository.CarreraRepository;
import org.example.tp3.repository.EstudianteCarreraRepository;
import org.example.tp3.repository.EstudianteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EstudianteCarreraService {
    private final EstudianteCarreraRepository repository;
    private final EstudianteRepository estudianteRepository;
    private final CarreraRepository carreraRepository;

    public EstudianteCarreraService(EstudianteCarreraRepository repository,
                                    EstudianteRepository estudianteRepository,
                                    CarreraRepository carreraRepository) {
        this.repository = repository;
        this.estudianteRepository = estudianteRepository;
        this.carreraRepository = carreraRepository;
    }

    @Transactional(readOnly = true)
    public List<EstudianteCarreraDTO> findAll() {
        return repository.findAll().stream()
                .map(EstudianteCarreraDTO::from)
                .toList();
    }

    @Transactional
    public EstudianteCarreraDTO matricular(EstudianteCarreraDTO dto) {
        if (dto.getDni() == null || dto.getIdCarrera() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dni e idCarrera son obligatorios");
        }

        Estudiante estudiante = estudianteRepository.findById(dto.getDni())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe estudiante con dni " + dto.getDni()));

        Carrera carrera = carreraRepository.findById(dto.getIdCarrera())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe carrera con id " + dto.getIdCarrera()));

        if (repository.existsByEstudiante_DniAndCarrera_Id(dto.getDni(), dto.getIdCarrera())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "el estudiante ya esta inscripto en esa carrera");
        }

        EstudianteCarrera inscripcion = new EstudianteCarrera(
                estudiante,
                carrera,
                dto.getInscripcion(),
                dto.getAntiguedad(),
                dto.getGraduacion()
        );
        return EstudianteCarreraDTO.from(repository.save(inscripcion));
    }
}
