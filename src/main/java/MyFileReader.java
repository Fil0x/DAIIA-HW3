import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class MyFileReader {
    private static MyFileReader instance = null;
    private static List<String> persons, artifacts, cities, creators, genre;

    // Handles all the resource file loading and statically keeps it in memory.
    private MyFileReader() {
        // Initialize the arrays
        persons = new ArrayList<String>();
        genre = new ArrayList<String>();
        creators = new ArrayList<String>();
        cities = new ArrayList<String>();
        artifacts = new ArrayList<String>();

        // Read the files
        readFile(persons, "persons.txt");
        readFile(artifacts, "artifacts.txt");
        readFile(cities, "cities.txt");
        readFile(creators, "creators.txt");
        readFile(genre, "genre.txt");
    }

    private void readFile(List<String> array, String name) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource(name).getFile());
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null){
                array.add(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MyFileReader getInstance(){
        if (instance == null) {
            instance = new MyFileReader();
        }
        return instance;
    }

    public static List<String> getPersons() {
        return persons;
    }

    public static List<String> getArtifacts() {
        return artifacts;
    }

    public static List<String> getCities() {
        return cities;
    }

    public static List<String> getCreators() {
        return creators;
    }

    public static List<String> getGenre() {
        return genre;
    }
}
