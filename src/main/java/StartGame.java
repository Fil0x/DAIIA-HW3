import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;

import java.io.IOException;
import java.util.ArrayList;

public class StartGame extends Agent{
    protected void setup() {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                System.out.println("STARTING A NEW GAME");

                AID queen0;
                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("queen");
                sd.setName("0");
                dfd.addServices(sd);
                DFAgentDescription[] result = new DFAgentDescription[0];
                try {
                    result = DFService.search(getAgent(), dfd);
                } catch (FIPAException e) {
                    e.printStackTrace();
                }
                if (result.length > 0) {
                    queen0 = result[0].getName();

                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(queen0);
                    msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    try {
                        msg.setContentObject(new ArrayList<Integer>());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    send(msg);
                }
            }
        });
    }
}
