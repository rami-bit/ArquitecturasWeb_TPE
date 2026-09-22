package seeds;

import dao.Dao;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public abstract class Seeder <T> {
    // open CSV
    // skip header
    // parse rows
    // // create entity
    // // call DAO
    // handle parsing/database
    protected Dao<T> dao;
    public Seeder(Dao<T> dao){
        this.dao = dao;
    }
    public void seed(String fileName){
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true)
                     .get().parse(reader);) {
        for (CSVRecord row : parser){
            T entity = this.getEntityFromCSV(row);
            this.dao.create(entity);
        }
        } catch (Exception e){
            throw new RuntimeException("Error al cargar datos desde " + fileName, e);
        }
    }

    public abstract T getEntityFromCSV(CSVRecord row);
}
