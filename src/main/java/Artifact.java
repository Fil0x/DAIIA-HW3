import java.io.Serializable;
import java.util.Date;
import java.util.Random;

/*
 Represents a single artifact which contains all the relevant information.
 A curator can create and manage randomly created artifacts based on files located in the resources folder.
*/
public class Artifact implements Serializable{
    private int id;
    private String name;
    private String creator;
    private Date creationDate;
    private String placeOfCreation;
    private String genre;
    private int price;

    public Artifact(int id, String name, String creator, Date creationDate,
                    String placeOfCreation, String genre) {
        this.id = id;
        this.name = name;

        this.creator = creator;
        this.creationDate = creationDate;
        this.placeOfCreation = placeOfCreation;
        this.genre = genre;
        this.price = new Random().nextInt(5000) + 1000;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getPlaceOfCreation() {
        return placeOfCreation;
    }

    public void setPlaceOfCreation(String placeOfCreation) {
        this.placeOfCreation = placeOfCreation;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }
}
