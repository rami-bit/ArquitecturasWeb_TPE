package utils;
import entity.Carrera;
import entity.Estudiante;
import entity.EstudianteCarrera;
import factory.JPAutil;
import repository.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import javax.persistence.EntityManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class CargarDatos {

    public void run(){
        if (tablaVacia(Estudiante.class)) {
            cargarEstudiantes("csv/estudiantes.csv");
        }
        if (tablaVacia(Carrera.class)) {
            cargarCarreras("csv/carreras.csv");
        }
        if (tablaVacia(EstudianteCarrera.class)) {
            cargarMatriculas("csv/estudianteCarrera.csv");
        }
    }

    private boolean tablaVacia(Class<?> entidad) {
        EntityManager em = JPAutil.getEntityManager();
        try {
            Long count = em.createQuery("SELECT COUNT(e) FROM " + entidad.getSimpleName() + " e", Long.class)
                    .getSingleResult();
            return count == 0;
        } finally {
            em.close();
        }
    }

    private CSVParser openParser(String ubicacion) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(ubicacion);
        if (is == null) {
            throw new RuntimeException("No se encontró el recurso: " + ubicacion);
        }
        return CSVFormat.DEFAULT.withHeader().parse(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("deprecation")
    private void cargarEstudiantes(String ubicacion){
        try(CSVParser registros = openParser(ubicacion)){
            ArrayList<Estudiante> estudiantes = new ArrayList<>();
            for(CSVRecord registro:registros){
                Estudiante estudiante=new Estudiante(
                        Long.parseLong(registro.get(0)),
                        Integer.parseInt(registro.get("LU")),
                        registro.get("nombre"),
                        registro.get("apellido"),
                        Integer.parseInt(registro.get("edad")),
                        registro.get("genero"),
                        registro.get("ciudad")
                );
                estudiantes.add(estudiante);
            }
            EstudianteRepository er = new EstudianteRepositoryImpl();
            er.addEstudiantes(estudiantes);
        }catch(Exception e){
            System.err.println("ERROR al cargar los estudiantes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    private void cargarMatriculas(String ubicacion){
        try(CSVParser registros = openParser(ubicacion)){
            EstudianteCarreraRepositoryImpl ecr = new EstudianteCarreraRepositoryImpl();
            EstudianteRepositoryImpl er = new EstudianteRepositoryImpl();
            CarreraRepositoryImpl cr = new CarreraRepositoryImpl();
            ArrayList<EstudianteCarrera> matriculas = new ArrayList<>();
            for(CSVRecord registro:registros){
                Carrera carrera = cr.findById(Integer.parseInt(registro.get(2)));
                Estudiante estudiante = er.findById(Long.parseLong(registro.get(1)));
                EstudianteCarrera matricula = new EstudianteCarrera(
                        Long.parseLong(registro.get("id")),
                        estudiante,
                        carrera,
                        Integer.parseInt(registro.get("inscripcion")),
                        Integer.parseInt(registro.get("graduacion")),
                        Integer.parseInt(registro.get("antiguedad"))

                );
                matriculas.add(matricula);
            }
            ecr.matricularEstudiantes(matriculas);
        }catch(Exception e){
            System.err.println("ERROR al cargar las matriculas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    private void cargarCarreras(String ubicacion){
        try(CSVParser registros = openParser(ubicacion)){
            ArrayList<Carrera> carreras = new ArrayList<>();
            CarreraRepository cr = new CarreraRepositoryImpl();
            for(CSVRecord registro:registros){
                Carrera carrera = new Carrera(
                        Long.parseLong(registro.get(0)),
                        registro.get(1),
                        Integer.parseInt(registro.get(2))
                );
                carreras.add(carrera);
            }
            cr.addCarreras(carreras);
        }catch(Exception e){
            System.err.println("ERROR al cargar las carreras: " + e.getMessage());
            e.printStackTrace();
        }

    }
}

