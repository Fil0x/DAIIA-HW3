import jade.core.AID;
import jade.core.Agent;
import jade.core.ContainerID;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.ContractNetResponder;
import jade.proto.SimpleAchieveREResponder;
import jade.wrapper.ControllerException;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Random;


public class CloningCuratorAgent extends Agent {
    static final String CONTAINER_HM = "container-hm";
    static final String CONTAINER_MG = "container-mg";

    private String name;
    private String senderType;
    private String originalName;

    private String serviceName;
    private AID originalCurator;
    private String originalContainer;

    public CloningCuratorAgent(){
        super();

        MessageTemplate mtartifact = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);

        addBehaviour(new ArtifactRequestREResponder(this, mtartifact));
    }

    protected void setup(){
        name = "(" + getLocalName() + ")";
        Object[] args = getArguments();
        originalName = "original-curator-" + args[0];
        originalCurator = getAID();
        try {
            originalContainer = getContainerController().getContainerName();
        } catch (ControllerException e) {
            e.printStackTrace();
        }

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if (getLocalName().equals(originalName)) {
                    ContainerID c = new ContainerID();
                    c.setName(CloningCuratorAgent.CONTAINER_HM);
                    serviceName = "curator-HM";
                    doClone(c, "curator-agent-HM-" + args[0]);
                }
            }
        });

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if (getLocalName().equals(originalName)) {
                    ContainerID c = new ContainerID();
                    c.setName(CloningCuratorAgent.CONTAINER_MG);
                    serviceName = "curator-MG";
                    doClone(c, "curator-agent-MG-" + args[0]);
                }
            }
        });
    }

    @Override
    protected void beforeClone() {
        System.out.println(name + ": Creating a clone...");
    }

    @Override
    protected void afterClone() {
        name = "(" + getLocalName() + ")";

        // Register both services to DF
        // Register interest for auctions
        ServiceDescription sd = new ServiceDescription();
        sd.setType("auction-bidder");
        sd.setName(serviceName);
        sd.addOntologies("bid-in-auctions");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        // Add the behaviour
        MessageTemplate mtauction = MessageTemplate.and(
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));
        addBehaviour(new AuctionREResponder(this, mtauction));
    }

    @Override
    protected void afterMove() {
        System.out.println(name + ": ARRIVED");
        // send a message to the original curator
        // Wait for the clones to return and report the results
        /*addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if(!getLocalName().equals(ORIGINAL))
                    removeBehaviour(this);
                if(getLocalName().equals(ORIGINAL)) {
                    System.out.println("Waiting for auctions to finish...");
                    boolean done = false;
                    int receivedMessages = 0;
                    while(!done) {
                        MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                        ACLMessage reply = blockingReceive(template);
                        if(reply != null) {
                            String content = reply.getContent();
                            String item = content.split(",")[0];
                            int price = Integer.parseInt(content.split(",")[1]);
                            String sender = reply.getSender().getLocalName();
                            if(item.equals("null"))
                                System.out.println(name + ": " + sender + " no sale");
                            else
                                System.out.println(name + ": " + sender + " sold " + item + "@" + price);
                            receivedMessages++;
                        }
                        done = receivedMessages == 2;
                    }
                }
            }
        });*/
    }

    private class ArtifactRequestREResponder  extends SimpleAchieveREResponder {

        public ArtifactRequestREResponder (Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        protected ACLMessage prepareResponse(ACLMessage request){
            ACLMessage reply = request.createReply();
            //System.out.println("(Curator) preparing response to request from: " + request.getSender().getLocalName());
            //System.out.println("(Curator) with content: " + request.getContent());
            senderType = request.getEnvelope().getComments();
            if ("platform".equals(senderType))
                reply.setPerformative(ACLMessage.AGREE);
            else if ("profiler".equals(senderType))
                reply.setPerformative(ACLMessage.AGREE);
            else
                reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
            return reply;
        }

        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response){

            ACLMessage reply = request.createReply();

            if("platform".equals(senderType)){
                User u;
                try {
                    u = (User)request.getContentObject();
                    reply.setContentObject((Serializable)(new ArtifactIndex()).searchArtifactIDs(u.getInterests()));

                } catch (UnreadableException e) {
                    System.err.println("(Curator) Could not deserialize user");
                } catch (IOException e) {
                    System.err.println("(Curator) Could not serialize artifact id list");
                }

            } else if ("profiler".equals(senderType)){
                List<Integer> artifactIDs;
                try {
                    artifactIDs = (List<Integer>)request.getContentObject();
                    reply.setContentObject((Serializable)(new ArtifactIndex()).searchArtifacts(artifactIDs));
                } catch (UnreadableException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            reply.setPerformative(ACLMessage.INFORM);
            reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);

            return reply;
        }
    }

    private class AuctionREResponder extends ContractNetResponder {

        private final double BID_MARGIN = 0.1;

        public AuctionREResponder(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
            System.out.println("Agent "+getLocalName()+": CFP received from "+cfp.getSender().getName());
            double proposal = new Random().nextDouble();
            if (proposal < BID_MARGIN) {
                // Retrieve the item and its price
                /*int price = 0;
                String name = "";
                try {
                    Artifact a = (Artifact) cfp.getContentObject();
                    // name = a.getName();
                    // price = a.getPrice();
                } catch (UnreadableException e) {
                    e.printStackTrace();
                }*/
                // We provide a proposal
                // System.out.println("Agent "+getLocalName()+": Proposing " + proposal + " Item:" +  name + "@" + price);
                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent(String.valueOf(proposal));
                return propose;
            }
            else {
                // We refuse to provide a proposal
                System.out.println("Agent "+getLocalName()+": Refuse");
                throw new RefuseException("evaluation-failed");
            }
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose,ACLMessage accept) {
            System.out.println("Agent "+getLocalName()+": I won the auction");
            ACLMessage inform = accept.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            ContainerID c = new ContainerID();
            c.setName(originalContainer);
            doMove(c);
            return inform;
        }

        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            System.out.println("Agent "+getLocalName()+": Proposal REJECTED");
        }
    }
}
