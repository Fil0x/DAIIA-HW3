import java.util.ArrayList;
import java.util.List;

public class ArtifactIndex{

    /*
     Search and return artifact IDs based on the profilers' interests.
     This request comes from the platform.
     For now we just return random artifacts.
    */
    public List<Integer> searchArtifactIDs(List<String> interests){
        //TODO dummy implementation
        ArrayList<Integer> result = new ArrayList<>();
        result.add(Utilities.getArtifact().getId());
        result.add(Utilities.getArtifact().getId());
        result.add(Utilities.getArtifact().getId());

        return result;


    }

    /*
     Return randomly created information about a list of artifact IDs.
     This request comes from the Profiler.
     For now we just return random artifacts.
    */
    public List<Artifact> searchArtifacts(List<Integer> artifactIDs){
        //TODO dummy implementation
        ArrayList<Artifact> result = new ArrayList<>();
        result.add(Utilities.getArtifact());
        result.add(Utilities.getArtifact());
        result.add(Utilities.getArtifact());

        return result;
    }
}
