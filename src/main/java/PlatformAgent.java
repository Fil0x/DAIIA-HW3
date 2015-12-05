import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
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
import java.io.Serializable;
import java.util.List;

public class PlatformAgent extends Agent {

    private static final String NAME = "platform";
    private FSMBehaviour fsm;

    private AID profiler;
    private String conversationID;
    private User user;
    private List<Integer> artifactIDs;


    /*
     A finite state machine to simulate the procedure that a platform would follow:
     A - Wait for requests from a client
     B - Ask the backend for the data
     C - Reply with the results to the client
     Repeat.
     */
    public PlatformAgent(){
        super();

        fsm = new FSMBehaviour(this);
        fsm.registerFirstState(new WaitRequestsFromProfiler(this), "A");
        fsm.registerState(new AskCurator(this, new ACLMessage(ACLMessage.INFORM)), "B");
        fsm.registerState(new SendItemIDS(this), "C");

        fsm.registerDefaultTransition("A", "B");
        fsm.registerDefaultTransition("B", "C");
        // The extra argument below is used to reset the AskCurator behaviour.
        fsm.registerDefaultTransition("C", "A", new String[]{"B"});

        addBehaviour(fsm);
    }

    protected void setup(){
        // Register to the DF to advertise your presence.
        ServiceDescription sd = new ServiceDescription();
        sd.setName(getLocalName());
        sd.setType("provide-tour");
        sd.addOntologies("get-tour-guide");
        register(sd);
    }

    private void register(ServiceDescription sd) {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private class WaitRequestsFromProfiler extends MsgReceiver {

        public WaitRequestsFromProfiler(Agent a) {
            super(a, MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                    MsgReceiver.INFINITE, new DataStore(), "key");
        }

        @Override
        protected void handleMessage(ACLMessage msg) {
            //System.out.println("(Platform) Received msg");
            /*
              Since we don't have intra behaviour communication we have to store the received
              information for the next behaviour in line to pick them up.
            */
            profiler = msg.getSender();
            conversationID = msg.getConversationId();
            try {
                user = (User) msg.getContentObject();
                //System.out.println("(Platform) Got user with name: " + user.getFullname());
            } catch (UnreadableException e) {
                System.err.println("(Platform) Could not deserialize user object");
            }

        }
    }

    private class AskCurator extends SimpleAchieveREInitiator {

        /*
           Initiate a convertation with the curator to get the information for the tourists'
           interests.
        */
        public AskCurator(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected ACLMessage prepareRequest(ACLMessage msg) {
            try {
                // Get the platform AID
                //System.out.println("(Platform) Initiating conversation");
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
                    request.setContentObject(user);
                    Envelope envelope = new Envelope();
                    // Distinguish between profiler and platform
                    envelope.setComments("platform");
                    request.setEnvelope(envelope);
                    return request;
                }
                return null;
            } catch (FIPAException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                System.err.println("(Platform) Couldn't serialize user info");
                return null;
            }
        }

        @Override
        protected void handleAgree(ACLMessage msg) {
            //System.out.println("Platform: Received AGREE");
        }

        @Override
        protected void handleInform(ACLMessage msg) {

            //System.out.println("Platform: Received INFORM");
            // Store it in the class for the SendItemIDS to send them to the Profiler
            try {
                artifactIDs = (List<Integer>)msg.getContentObject();
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }

    }

    private class SendItemIDS extends OneShotBehaviour {

        public SendItemIDS(Agent agent) {
            super(agent);
        }

        @Override
        public void action() {
            //System.out.println("(Platform) Gonna send some IDs back");
            // send the first message to the Curator to ask for interesting artifacts
            ACLMessage result = new ACLMessage(ACLMessage.INFORM);
            result.addReceiver(profiler);
            result.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            result.setOntology("deliver-itemids");
            try {

                result.setContentObject((Serializable) artifactIDs);
                send(result);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

























