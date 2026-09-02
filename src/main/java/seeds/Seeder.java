package seeds;

import dao.Dao;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.Reader;

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
        try (Reader reader = new FileReader(fileName);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true)
                     .get().parse(reader);) {
        for (CSVRecord row : parser){
            T entity = this.getEntityFromCSV(row);
            this.dao.create(entity);
        }
        } catch (Exception e){
            System.out.println(e);
        }
    }

    public abstract T getEntityFromCSV(CSVRecord row);
}
