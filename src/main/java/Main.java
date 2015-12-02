import jade.core.AID;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;

import java.io.IOException;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws StaleProxyException {
        int boardSize = Integer.parseInt(args[0]);

        // Get JADE runtime
        Runtime rt = Runtime.instance();
        rt.setCloseVM(true);

        // Create main container with GUI
        rt.createMainContainer(new ProfileImpl()).createNewAgent("rma", "jade.tools.rma.rma", new Object[]{}).start();

        // Create agent containers
        AgentContainer c1 = rt.createAgentContainer(new ProfileImpl());
        for (int i = 0; i < boardSize; i++) {
            c1.createNewAgent("Q" + i, "QueenAgent", new Object[]{ i, boardSize }).start();
        }
        c1.createNewAgent("starter", "StartGame", new Object[]{}).start();

//        AgentContainer c2 = rt.createAgentContainer(new ProfileImpl());
//        for (int i = 0; i < 1; i++) {
//            c2.createNewAgent("Profiler" + i, PKG + "ProfilerAgent", new Object[]{new User()}).start();
//        }
    }
}
