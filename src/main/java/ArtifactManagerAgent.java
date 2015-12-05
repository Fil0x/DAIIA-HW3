import com.sun.org.apache.xpath.internal.SourceTree;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;


/*
For some reason the next iteration sees the previous state/variables.
 */
public class ArtifactManagerAgent extends Agent {

    private final int SPAWN_TIME = 10000;
    private static final String NAME = "(ArtifactManager)";
    private FSMBehaviour fsm;
    //0
    private static final String START_AUCTION = "A";
    //1
    private static final String CFP = "B";
    //2
    private static final String COMPLETE_AUCTION = "C";

    //State
    private ArrayList<AID> buyers;
    private int leastAcceptablePrice;
    private int reductionStep;
    private int round;
    private int nResponders;
    private volatile ACLMessage msgToSend;
    private Artifact itemToSell;

    public ArtifactManagerAgent() {
        super();

        addBehaviour(new AuctionSpawner(this, SPAWN_TIME));
    }

    protected void setup() {
        System.out.println("Agent:" + getAID().getName() + " is ready!");
        // Initialize the mesage only once
        msgToSend = new ACLMessage(ACLMessage.CFP);
    }

    protected void takeDown() {
        System.out.println("Agent:" + getAID().getName() + " terminating...");
    }

    private class AuctionSpawner extends TickerBehaviour {
        public AuctionSpawner(Agent a, long period) {
            super(a, period);
        }

        @Override
        public void onStart() {
            System.out.println("Agent:" + getAgent().getName() + "[Ticker:" + getPeriod() + "] is ready!");
        }

        @Override
        protected void onTick() {
            fsm = new FSMBehaviour();

            fsm.registerFirstState(new StartAuction(getAgent()), START_AUCTION);
            fsm.registerState(new HandleAuction(getAgent(), msgToSend), CFP);
            fsm.registerLastState(new CompleteAuction(), COMPLETE_AUCTION);

            fsm.registerDefaultTransition(START_AUCTION, CFP);
            fsm.registerDefaultTransition(CFP, COMPLETE_AUCTION);

            getAgent().addBehaviour(fsm);
        }
    }

    private class StartAuction extends OneShotBehaviour {
        public StartAuction(Agent a){
            super(a);
        }

        @Override
        public void action() {
            try {
                System.out.println("---------------------" + NAME + "---------------------");
                System.out.println(NAME + ": soon starting a new dutch auction");
                // Initialize the state variables
                buyers = new ArrayList<>();
                itemToSell = Utilities.getArtifact();
                leastAcceptablePrice = (int) (itemToSell.getPrice() * 0.4);
                round = 1;
                reductionStep = (int) (0.1 * itemToSell.getPrice());
                System.out.println(NAME + ": " + itemToSell.getName() + "@" + itemToSell.getPrice());
                // Wait for the other agents to boot
                doWait(1000);
                // Get the interested buyers-curators
                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("auction-bidder");
                dfd.addServices(sd);
                DFAgentDescription[] result = DFService.search(getAgent(), dfd);
                // Create a new message to broadcast to the interested bidders
                msgToSend.setSender(getAID());
                msgToSend.setContentObject(itemToSell);
                msgToSend.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                msgToSend.clearAllReceiver();
                if (result.length>0) {
                    System.out.println(NAME + ": Found " + result.length + " bidders");
                    nResponders = result.length;
                    for(DFAgentDescription r: result) {
                        msgToSend.addReceiver(r.getName());
                        buyers.add(r.getName());
                    }
                }
            } catch (FIPAException e) {
                    e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class HandleAuction extends ContractNetInitiator {

        public HandleAuction(Agent a, ACLMessage cfp) {
            super(a, cfp);
        }

        protected void handlePropose(ACLMessage propose, Vector v) {
            System.out.println(NAME + ": Agent "+propose.getSender().getName()+" proposed "+propose.getContent());
        }

        protected void handleRefuse(ACLMessage refuse) {
            System.out.println(NAME + ": Agent "+refuse.getSender().getName()+" refused");
        }

        protected void handleFailure(ACLMessage failure) {
            if (failure.getSender().equals(myAgent.getAMS())) {
                // FAILURE notification from the JADE runtime: the receiver
                // does not exist
                System.out.println("Responder does not exist");
            }
            else {
                System.out.println("Agent "+failure.getSender().getName()+" failed");
            }
            // Immediate failure --> we will not receive a response from this agent
            nResponders--;
        }

        protected void handleAllResponses(Vector responses, Vector acceptances) {
            if (responses.size() < nResponders) {
                // Some responder didn't reply within the specified timeout
                System.out.println("Timeout expired: missing "+(nResponders - responses.size())+" responses");
            }
            // Evaluate proposals
            double bestProposal = -1;
            AID winner = null;
            ACLMessage accept = null;
            Enumeration e = responses.elements();
            while (e.hasMoreElements()) {
                ACLMessage msg = (ACLMessage) e.nextElement();
                if (msg.getPerformative() == ACLMessage.PROPOSE) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    acceptances.addElement(reply);
                    double proposal = Double.parseDouble(msg.getContent());
                    if (proposal > bestProposal) {
                        bestProposal = proposal;
                        winner = msg.getSender();
                        accept = reply;
                    }
                }
            }
            // Accept the proposal of the best proposer
            if (accept != null) {
                System.out.println(NAME + ": Accepting proposal "+bestProposal+" from responder "+winner.getName());
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            }
            else {
                int newPrice = itemToSell.getPrice() - reductionStep;
                if(newPrice < leastAcceptablePrice) {
                    System.out.println(String.format("%s: New price(%d), Lowest price(%d)", NAME, newPrice, leastAcceptablePrice));
                    System.out.println(NAME + ": Terminating auction...");
                }
                else {
                    System.out.println("~~~~REDUCING~~~~" + NAME + "~~~~PRICE~~~~");

                    itemToSell.setPrice(newPrice);
                    System.out.println(NAME + ": Price " + itemToSell.getPrice());
                    Vector msgs = new Vector();
                    try {
                        msgToSend.setContentObject(itemToSell);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    msgs.add(msgToSend);

                    newIteration(msgs);
                }
            }
        }

        protected void handleInform(ACLMessage inform) {
            System.out.println("Agent "+inform.getSender().getName()+" is the winner of the auction!");
            System.out.println("Agent "+inform.getSender().getName()+" Item:" + itemToSell.getName() + "@" + itemToSell.getPrice());
        }
    }

    private class CompleteAuction extends OneShotBehaviour{

        @Override
        public void action() {
            System.out.println(NAME + ": Auction Completed.");
        }
    }
}
