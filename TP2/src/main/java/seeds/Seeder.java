package seeds;

import service.Service;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public abstract class Seeder<T> {
    protected Service<T> service;

    public Seeder(Service<T> service) {
        this.service = service;
    }

    public void seed(String fileName) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .withHeader()
                     .withSkipHeaderRecord(true)
                     .parse(reader)) {
            for (CSVRecord row : parser) {
                T entity = this.getEntityFromCSV(row);
                this.service.save(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al cargar datos desde " + fileName, e);
        }
    }

    public abstract T getEntityFromCSV(CSVRecord row);
}
