import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Envelope;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.SimpleAchieveREInitiator;
import jade.proto.states.MsgReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProfilerAgent extends Agent {

    private int TOTAL_TIME = 60000;
    private int SPAWN_TIME = 3000;
    private List<Integer> itemIDs;
    private List<Artifact> artifacts;
    private static final String NAME = "profiler";

    public ProfilerAgent() {
        super();
        // This behaviour will be used to create random tourists every SPAWN_TIME
        addBehaviour(new TourSpawner(this, SPAWN_TIME));
    }


    protected void setup() {
        //System.out.println("Agent:" + getAID().getName() + " is ready!");
    }

    protected void takeDown() {
        //System.out.println("Agent:" + getAID().getName() + " terminating...");
    }

    private class TourSpawner extends TickerBehaviour {
        public TourSpawner(Agent a, long period) {
            super(a, period);
        }

        @Override
        public void onStart() {
            //System.out.println("Agent:" + getAgent().getName() + "[Ticker:" + getPeriod() + "] is ready!");
        }

        @Override
        protected void onTick() {
            /*
             The sequential behaviour simulates the steps that an actual tourist would follow when visiting
             a museum.
             1 - Ask the main frame for a tour
             2 - wait for a reply
             3 - ask the curator for facts about an artifact
             */
            SequentialBehaviour sb = new SequentialBehaviour(getAgent());
            sb.addSubBehaviour(new RequestTourGuide(getAgent()));
            sb.addSubBehaviour(new WaitArtifactIDsFromPlatform(getAgent()));
            sb.addSubBehaviour(new AskCuratorForArtifacts(getAgent(), new ACLMessage(ACLMessage.INFORM)));
            getAgent().addBehaviour(sb);
        }
    }

    private class RequestTourGuide extends OneShotBehaviour {
        /*
         The first step is to send to the platform his interests in order to get a personalized
         guided tour.
         */
        public RequestTourGuide(Agent agent) {
            super(agent);
        }

        @Override
        public void action() {
            try {
                // Get the platform AID and ask the DF about the presence of the platform.
                AID platfom;
                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("provide-tour");
                dfd.addServices(sd);
                DFAgentDescription[] result = DFService.search(getAgent(), dfd);
                if (result.length>0) {
                    platfom = result[0].getName();
                    // Create a random user
                    User u = Utilities.getUser(5);

                    // send the first message to the platform to ask for interesting artifacts
                    //System.out.println("(Profiler) ---------------------------");
                    //System.out.println("(Profiler) Sending request to platform");
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    request.addReceiver(platfom);
                    request.setLanguage(FIPANames.ContentLanguage.FIPA_SL0);
                    request.setOntology(FIPANames.Ontology.SL0_ONTOLOGY);
                    request.setContentObject(u);
                    Envelope envelope = new Envelope();
                    // Used to distinguish between the request of a profiler and a platform in the curator.
                    envelope.setComments("profiler");
                    request.setEnvelope(envelope);
                    send(request);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (FIPAException e) {
                e.printStackTrace();
            }
        }
    }

    private class WaitArtifactIDsFromPlatform extends MsgReceiver {

        /*
         A msgreceiver to wait for INFINITE time for a reply from the platform containing the itemIDs.
         */
        public WaitArtifactIDsFromPlatform(Agent a) {
            super(a, MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                    MsgReceiver.INFINITE, new DataStore(), "key");
        }

        @Override
        protected void handleMessage(ACLMessage msg) {
            //System.out.println("(Profiler) Received msg");

            if(msg != null && "deliver-itemids".equals(msg.getOntology())) {
                try {
                    // IDs tailored to his interests.
                    itemIDs = (List<Integer>) msg.getContentObject();
                    StringBuilder sb = new StringBuilder();
                    sb.append("(Profiler) Got artifacts: ");
                    for (Integer i : itemIDs) {
                        sb.append(i);
                        sb.append(" ");
                    }
                    //System.out.println(sb.toString());
                } catch (UnreadableException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class AskCuratorForArtifacts extends SimpleAchieveREInitiator {

        /*
         Sends all the IDs to the curator in order to get detailed information about the artifacts
         during his visit in the museum.
         */
        public AskCuratorForArtifacts(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected ACLMessage prepareRequest(ACLMessage msg) {
            try {
                // Get the platform AID
                //System.out.println("(Profiler) Initiating conversation");
                AID curator;
                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("artifact-search");
                dfd.addServices(sd);
                DFAgentDescription[] result = DFService.search(getAgent(), dfd);
                if (result.length > 0) {
                    curator = result[0].getName();

                    // send the first message to the Curator to ask for interesting artifacts
                    ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                    request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    request.addReceiver(curator);
                    Envelope envelope = new Envelope();
                    // Used to distinguish between the request of a profiler and a platform in the curator.
                    envelope.setComments("profiler");
                    request.setEnvelope(envelope);
                    return request;
                }
                return null;
            } catch (FIPAException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void handleAgree(ACLMessage msg) {
            //System.out.println("(Profiler) Received AGREE from curator");
        }

        @Override
        protected void handleInform(ACLMessage msg) {

            //System.out.println("(Profiler) Received INFORM from curator");
            try {
                artifacts = (List<Artifact>) msg.getContentObject();
                //System.out.println("(Profiler) Received artifact information:");
                for(Artifact a: artifacts) {
                    //System.out.println(a.getName() + ", " + a.getGenre());
                }
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }
    }
}
