import jade.core.MainContainer;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.StaleProxyException;

public class MainClone {
    public static void main(String[] args) throws StaleProxyException {
        // Get JADE runtime
        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);

        // Create main container with GUI
        AgentContainer cm = rt.createMainContainer(new ProfileImpl());

        // Create the "Heritage Malta" container
        ProfileImpl p1 = new ProfileImpl();
        p1.setParameter(ProfileImpl.CONTAINER_NAME, CloningCuratorAgent.CONTAINER_HM);
        AgentContainer c1 = rt.createAgentContainer(p1);

        // Create the "museo galileo" container
        ProfileImpl p2 = new ProfileImpl();
        p2.setParameter(ProfileImpl.CONTAINER_NAME, CloningCuratorAgent.CONTAINER_MG);
        AgentContainer c2 = rt.createAgentContainer(p2);

        cm.createNewAgent("rma", "jade.tools.rma.rma", new Object[]{}).start();
        cm.createNewAgent(CloningCuratorAgent.ORIGINAL, "CloningCuratorAgent", new Object[]{}).start();
        cm.createNewAgent(CloningArtifactManagerAgent.ORIGINAL, "CloningArtifactManagerAgent", new Object[]{}).start();
    }
}
