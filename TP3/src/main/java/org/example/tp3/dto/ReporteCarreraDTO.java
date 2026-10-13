package org.example.tp3.dto;

import java.util.List;

public record ReporteCarreraDTO(String carrera, List<ReporteAnioDTO> anios) {}