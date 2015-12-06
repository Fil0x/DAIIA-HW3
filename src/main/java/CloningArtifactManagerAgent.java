import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import jade.wrapper.ControllerException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;


/*
For some reason the next iteration sees the previous state/variables.
 */
public class CloningArtifactManagerAgent extends Agent {

    private String name;
    private String originalContainer;
    static final String ORIGINAL = "originalArtifactManagerAgent";
    private FSMBehaviour fsm;
    //0
    private static final String START_AUCTION = "A";
    //1
    private static final String CFP = "B";
    //2
    private static final String COMPLETE_AUCTION = "C";

    //State
    //Used by the clones to report back
    private static int itemsSold = 0;
    private static volatile int clonesReturned = 0;
    private int clonesCreated;
    private int leastAcceptablePrice;
    private int reductionStep;
    private int nResponders;
    private volatile ACLMessage msgToSend;
    private Artifact itemToSell;
    private ArrayList<AID> buyers;
    private boolean done;
    private String serviceName;

    protected void setup() {
        doWait(1500);
        System.out.println("Agent:" + getAID().getName() + " is ready!");
        name = "(" + getLocalName() + ")";
        try {
            originalContainer = getContainerController().getContainerName();
        } catch (ControllerException e) {
            e.printStackTrace();
        }

        // Clone first museum
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if (getLocalName().equals(ORIGINAL)) {
                    ContainerID c = new ContainerID();
                    c.setName(CloningCuratorAgent.CONTAINER_HM);
                    serviceName = "curator-HM";
                    doClone(c, "auction-agent-1");
                    clonesCreated++;
                }
           }
        });

        // Clone second museum
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if (getLocalName().equals(ORIGINAL)) {
                    ContainerID c = new ContainerID();
                    c.setName(CloningCuratorAgent.CONTAINER_MG);
                    serviceName = "curator-MG";
                    doClone(c, "auction-agent-2");
                    clonesCreated++;
                }
            }
        });

        // Initialize the message only once
        msgToSend = new ACLMessage(ACLMessage.CFP);

        // Wait for the clones to return and report the results
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                if(!getLocalName().equals(ORIGINAL))
                    removeBehaviour(this);
                if (!done && 2 == clonesReturned) {
                    System.out.println(name +": My trusty clones sold " + itemsSold + " items.");
                    done = !done;
                }
            }
        });
    }

    protected void takeDown() {
        System.out.println("Agent:" + getAID().getName() + " terminating...");
    }

    @Override
    protected void beforeClone() {
        System.out.println(name + " Creating a clone...");
    }

    @Override
    protected void afterClone() {
        if(!getLocalName().equals(ORIGINAL)) {
            fsm = new FSMBehaviour();

            fsm.registerFirstState(new StartAuction(this), START_AUCTION);
            fsm.registerState(new HandleAuction(this, msgToSend), CFP);
            fsm.registerLastState(new CompleteAuction(), COMPLETE_AUCTION);

            fsm.registerDefaultTransition(START_AUCTION, CFP);
            fsm.registerDefaultTransition(CFP, COMPLETE_AUCTION);

            addBehaviour(fsm);

            name = "(" + getLocalName() + ")";
        }
    }

    @Override
    protected void afterMove() {
        clonesReturned++;
    }

    private class StartAuction extends OneShotBehaviour {
        public StartAuction(Agent a){
            super(a);
        }

        @Override
        public void action() {
            try {
                System.out.println("---------------------" + name + "---------------------");
                System.out.println(name + ": soon starting a new dutch auction");
                // Initialize the state variables
                buyers = new ArrayList<>();
                itemToSell = Utilities.getArtifact();
                leastAcceptablePrice = (int) (itemToSell.getPrice() * 0.4);
                reductionStep = (int) (0.1 * itemToSell.getPrice());
                System.out.println(name + ": " + itemToSell.getName() + "@" + itemToSell.getPrice());
                // Wait for the other agents to boot
                doWait((new Random()).nextInt(1000) + 2000);
                // Get the interested buyers-curators
                DFAgentDescription dfd = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("auction-bidder");
                sd.setName(serviceName);
                dfd.addServices(sd);
                DFAgentDescription[] result = DFService.search(getAgent(), dfd);
                // Create a new message to broadcast to the interested bidders
                msgToSend.setSender(getAID());
                msgToSend.setContentObject(itemToSell);
                msgToSend.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
                msgToSend.clearAllReceiver();
                if (result.length>0) {
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
            // System.out.println(name + ": Agent "+propose.getSender().getName()+" proposed "+propose.getContent());
        }

        protected void handleRefuse(ACLMessage refuse) {
            // System.out.println(name + ": Agent "+refuse.getSender().getName()+" refused");
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
                        accept = reply;
                    }
                }
            }
            // Accept the proposal of the best proposer
            if (accept != null) {
                // System.out.println(name + ": Accepting proposal "+bestProposal+" from responder "+winner.getName());
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                itemsSold++;
            }
            else {
                int newPrice = itemToSell.getPrice() - reductionStep;
                if(newPrice < leastAcceptablePrice) {
                    System.out.println(String.format("%s: New price(%d), Lowest price(%d)", name, newPrice, leastAcceptablePrice));
                    System.out.println(name + ": Terminating auction...");
                }
                else {
                    System.out.println("~~~~REDUCING~~~~" + name + "~~~~PRICE~~~~");

                    itemToSell.setPrice(newPrice);
                    System.out.println(name + ": Price " + itemToSell.getPrice());
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
            System.out.println(name + ": Auction Completed.");


            ContainerID c = new ContainerID();
            c.setName(originalContainer);
            doMove(c);
        }
    }
}
