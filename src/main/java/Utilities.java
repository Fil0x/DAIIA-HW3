import java.util.*;

public class Utilities {
    public static int generateItemId() {
        Random r = new Random();
        int id = r.nextInt(90000)+10000;
        return id;
    }

    // Generate a random user every interval of the ProfilerAgent.
    public static User getUser(int num_interests) {
        MyFileReader fr = MyFileReader.getInstance();
        List<String> allUsers = fr.getPersons();
        List<String> allGenres = fr.getGenre();
        Random r = new Random();

        String name = allUsers.get(r.nextInt(allUsers.size()));
        int age = r.nextInt(50);

        Set<String> s = new TreeSet<String>();
        while(s.size() != num_interests)
            s.add(allGenres.get(r.nextInt(allGenres.size())));
        List<String> interests = new ArrayList<String>();
        interests.addAll(s);

        return new User(name, age, interests);
    }

    /*
     Generate a random artifact when either platform asks for them based on the tourists' interests or
     when the profiler asks for more information of an artifact given the ID.
      */
    public static Artifact getArtifact() {
        MyFileReader fr = MyFileReader.getInstance();
        int id = generateItemId();
        List<String> allGenres = fr.getGenre();
        List<String> allNames = fr.getArtifacts();
        List<String> allCities = fr.getCities();
        List<String> allCreators = fr.getCreators();
        Random r = new Random();

        String name = allNames.get(r.nextInt(allNames.size()));
        String creator = allCreators.get(r.nextInt(allCreators.size()));
        String placeOfCreation = allCities.get(r.nextInt(allCities.size()));
        String genre = allGenres.get(r.nextInt(allGenres.size()));

        Date dateOfCreation = new Date(Math.abs(System.currentTimeMillis() - r.nextLong()));

        return new Artifact(id, name, creator, dateOfCreation, placeOfCreation, genre);
    }
}
