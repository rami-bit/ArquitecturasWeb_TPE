package utils;
import entity.Carrera;
import entity.Estudiante;
import entity.Inscripcion;
import repository.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.util.ArrayList;

public class CargarDatos {

    public void run(){
        cargarEstudiantes("src/main/resources/csv/estudiantes.csv");
        cargarCarreras("src/main/resources/csv/carreras.csv");
        cargarMatriculas("src/main/resources/csv/estudianteCarrera.csv");
    }

    @SuppressWarnings("deprecation")
    private void cargarEstudiantes(String ubicacion){
        try{
            ArrayList<Estudiante> estudiantes = new ArrayList<>();
            CSVParser registros = CSVFormat.DEFAULT.withHeader().parse(new FileReader(ubicacion));
            for(CSVRecord registro:registros){
                Estudiante estudiante=new Estudiante(
                        Long.parseLong(registro.get(0)),
                        registro.get(1),
                        registro.get(2),
                        Integer.parseInt(registro.get(3)),
                        registro.get(4),
                        registro.get(5),
                        Integer.parseInt(registro.get(6))
                );
                estudiantes.add(estudiante);
            }
            EstudianteRepository er = new EstudianteRepositoryImpl();
            er.addEstudiantes(estudiantes);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    private void cargarMatriculas(String ubicacion){
        try{
            EstudianteCarreraRepositoryImpl ecr = new EstudianteCarreraRepositoryImpl();
            EstudianteRepositoryImpl er = new EstudianteRepositoryImpl();
            CarreraRepositoryImpl cr = new CarreraRepositoryImpl();
            ArrayList<EstudianteCarrera> matriculas = new ArrayList<>();
            CSVParser registros = CSVFormat.DEFAULT.withHeader().parse(new FileReader(ubicacion));
            for(CSVRecord registro:registros){
                Carrera carrera = cr.findById(Integer.parseInt(registro.get(2)));
                Estudiante estudiante = er.findById(Long.parseLong(registro.get(1)));
                EstudianteCarrera matricula = new EstudianteCarrera(
                        Long.parseLong(registro.get("id")),
                        Integer.parseInt(registro.get("inscripcion")),
                        Integer.parseInt(registro.get("graduacion")),
                        Integer.parseInt(registro.get("antiguedad")),
                        estudiante,
                        carrera
                );
                matriculas.add(matricula);
            }
            ecr.matricularEstudiantes(matriculas);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    private void cargarCarreras(String ubicacion){
        try {
            ArrayList<Carrera> carreras = new ArrayList<>();
            CarreraRepository cr = new CarreraRepositoryImpl();
            CSVParser registros = CSVFormat.DEFAULT.withHeader().parse(new FileReader(ubicacion));
            for(CSVRecord registro:registros){
                Carrera carrera = new Carrera(
                        Integer.parseInt(registro.get(0)),
                        registro.get(1),
                        Integer.parseInt(registro.get(2))
                );
                carreras.add(carrera);
            }
            cr.addCarreras(carreras);
        }catch(Exception e){
            e.printStackTrace();
        }

    }
}

