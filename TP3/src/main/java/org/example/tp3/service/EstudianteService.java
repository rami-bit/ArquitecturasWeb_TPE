package org.example.tp3.service;

import org.example.tp3.dto.EstudianteDTO;
import org.example.tp3.model.Estudiante;
import org.example.tp3.repository.EstudianteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EstudianteService {
    private final EstudianteRepository repository;

    public EstudianteService(EstudianteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EstudianteDTO> findAll() {
        return repository.findAll().stream()
                .map(EstudianteDTO::from)
                .toList();
    }

    @Transactional
    public List<EstudianteDTO> findAllOrderBy(String orden) {
        if (!orden.equals("nombre") && !orden.equals("dni")&& !orden.equals("apellido")&& !orden.equals("edad")) {
            orden = "dni";
        }
        Sort sort = Sort.by(orden);
        return repository.findAll(sort).stream()
                .map(EstudianteDTO::from)
                .toList();

    }

    @Transactional
    public EstudianteDTO findById(Long id){
        Optional<Estudiante> estudiante = repository.findById(id);
        return estudiante
                .map(EstudianteDTO::from)
                .orElseThrow(() -> new RuntimeException(
                        "No se encuentra el Estudiante con el id: " + id
                ));

    }

    @Transactional
    public EstudianteDTO save(EstudianteDTO dto) {
        Estudiante estudiante = new Estudiante(
                dto.getDni(),
                dto.getNroLibreta(),
                dto.getNombre(),
                dto.getApellido(),
                dto.getEdad(),
                dto.getGenero(),
                dto.getCiudad()
        );
        return EstudianteDTO.from(repository.save(estudiante));
    }
}
