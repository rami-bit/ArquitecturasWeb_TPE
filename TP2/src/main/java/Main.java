import entity.Carrera;
import entity.Estudiante;
import entity.Inscripcion;
import repository.RepositoryCarrera;
import repository.RepositoryEstudiante;
import repository.RepositoryInscripcion;
import repository.CarreraRepositoryImpl;
import repository.EstudianteRepositoryImpl;
import repository.EstudianteCarreraRepositoryImpl;
import repository.EstudianteCarreraRepository;
import dto.EstudianteDTO;
import utils.CargarDatos;
import java.util.List;
import java.util.Scanner;


import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class Main {
    public static void main(String[] args) {
        CargarDatos cd = new CargarDatos();
        cd.run();
        CarreraRepository cr = new CarreraRepositoryImpl();
        EstudianteRepository er = new EstudianteRepositoryImpl();
        EstudianteCarreraRepository ecr = new EstudianteCarreraRepositoryImpl(); 
        boolean condicion = true;
        while(condicion){
            System.out.println("1. Dar de alta un estudiante");
            System.out.println("2. Matricular un estudiante en una carrera");
            System.out.println("3. Recuperar todos los estudiantes, y especificar algún criterio de ordenamiento simple");
            System.out.println("4. Recuperar un estudiante, en base a su número de libreta universitaria");
            System.out.println("5. Recuperar todos los estudiantes, en base a su género");
            System.out.println("6. Recuperar las carreras con estudiantes inscriptos, y ordenar por cantidad de inscriptos");
            System.out.println("7. Recuperar los estudiantes de una determinada carrera, filtrado por ciudad de residencia");
            System.out.println("8. Generar reporte de carreras");
            System.out.println("9. Salir");
            System.out.println("\n");
            System.out.print("Ingrese una opción: ");
            Scanner scanner = new Scanner(System.in);
            int opcion = scanner.nextInt();
            switch (opcion) {
                case 1:
                    // Dar de alta un estudiante
                    System.out.println("Ingrese los datos del estudiante:");
                    System.out.println("Nombre: ");
                    String nombre = scanner.next();
                    System.out.println("Apellido: ");
                    String apellido = scanner.next();
                    System.out.println("DNI: ");
                    Long dni = scanner.nextLong();
                    System.out.println("Edad: ");
                    int edad = scanner.nextInt();
                    System.out.println("Genero: ");
                    String genero = scanner.next();
                    System.out.println("Ciudad: ");
                    String ciudad = scanner.next();
                    System.out.println("Nro Libreta: ");
                    int nroLibreta = scanner.nextInt();
                    
                    Estudiante estudiante = new Estudiante(dni, nombre, apellido, edad, genero, ciudad, nroLibreta);
                    er.addEstudiante(estudiante);
                    System.out.println("Estudiante agregado: " + estudiante);
                    System.out.println("\n");
                break; 
             
                case 3:
                      // Recuperar todos los estudiantes, y especificar algún criterio de ordenamiento
                    // simple
                    System.out.println("Ingrese el criterio de ordenamiento (nombre, apellido, ciudad): ");
                    String criterio = scanner.next();
                    try {
                        List<EstudianteDTO> estudiantes = er.getEstudiantesSorted(criterio);
                        System.out.println("Estudiantes ordenados por " + criterio + ": ");
                        for (EstudianteDTO est : estudiantes) {
                            System.out.println(est);
                        }

                    } catch (Exception e) {
                        System.out.println("Criterio de ordenamiento no válido.");
                    }
                   
                    System.out.println("\n");
                break;
                case 4:
                    // Recuperar un estudiante, en base a su número de libreta universitaria
                    System.out.println("Ingrese el nro de libreta del estudiante a buscar: ");
                    int nroLibretaBuscar = scanner.nextInt();
                    EstudianteDTO estudianteBuscar = er.getEstudianteLU(nroLibretaBuscar);
                    if (estudianteBuscar != null) {
                        System.out.println("Estudiante encontrado: " + estudianteBuscar);
                    } else {
                        System.out.println("Estudiante no encontrado.");
                    }
                    System.out.println("\n");
                    break;
                case 5:
                    // Recuperar todos los estudiantes, en base a su género
                    System.out.println("Ingrese el género de los estudiantes a buscar: ");
                    String generoBuscar = scanner.next();
                    List<EstudianteDTO> estudiantesGenero = er.getEstudiantesByGenero(generoBuscar);
                    if (estudiantesGenero != null) {
                        System.out.println("Estudiantes de género " + generoBuscar + ": ");
                        for (EstudianteDTO estGenero : estudiantesGenero) {
                            System.out.println(estGenero);
                        }
                    } else {
                        System.out.println("No se encontraron estudiantes de ese género.");
                    }
                    System.out.println("\n");
                    break;
                case 7:
                    // Recuperar los estudiantes de una determinada carrera, filtrado por ciudad de
                    // residencia
                    System.out.println("Ingrese el nombre de la carrera: ");
                    String nombreCarrera = scanner.next();
                    System.out.println("Ingrese la ciudad de residencia: ");
                    String ciudadResidencia = scanner.next();
                    List<EstudianteDTO> estudiantesCarrera = er.getEstudiantesByCarreraAndCiudad(nombreCarrera,ciudadResidencia);
                    if (estudiantesCarrera != null) {
                        System.out.println("Estudiantes de la carrera " + nombreCarrera + " y ciudad "
                                + ciudadResidencia + ": ");
                        for (EstudianteDTO estCarrera : estudiantesCarrera) {
                            System.out.println(estCarrera);
                        }
                    } else {
                        System.out.println("No se encontraron estudiantes para esa carrera y ciudad.");
                    }
                    System.out.println("\n");
                break;
                case 9:
                    scanner.close();
                    System.out.println("Saliendo...");
                    condicion = false;
                    break;
                default:
                    System.out.println("Opción no válida");

        }
      
        
        


    }
    }
}
