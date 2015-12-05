import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.ContractNetResponder;
import jade.proto.SimpleAchieveREResponder;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Random;


public class CuratorAgent extends Agent {
    private static final String NAME = "(Curator)";

    private String senderType;
    private AID sender;
    private ArtifactIndex artifactIndex = new ArtifactIndex();

    // A single state FSM(for now) that works like a cyclic behaviour.
    // The only behaviour is to wait for requests from either the profiler or platform.
    public CuratorAgent(){
        super();

        MessageTemplate mtartifact = MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
        MessageTemplate mtauction = MessageTemplate.and(
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));

        addBehaviour(new ArtifactRequestREResponder(this, mtartifact));
        addBehaviour(new AuctionREResponder(this, mtauction));
    }

    protected void setup(){
        // Register both services to DF
        // Artifact search
        ServiceDescription sd1 = new ServiceDescription();
        sd1.setType("artifact-search");
        sd1.setName("provide-artifact-search");
        sd1.addOntologies("request-ids");

        // Artifact lookup based on item ID
        ServiceDescription sd2 = new ServiceDescription();
        sd2.setType("artifact-lookup");
        sd2.setName("provide-artifact-lookup");
        sd2.addOntologies("request-iteminfo");

        // Register interest for auctions
        ServiceDescription sd3 = new ServiceDescription();
        sd3.setType("auction-bidder");
        sd3.setName("auction-participator");
        sd3.addOntologies("bid-in-auctions");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd1);
        dfd.addServices(sd2);
        dfd.addServices(sd3);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private class ArtifactRequestREResponder  extends SimpleAchieveREResponder {

        public ArtifactRequestREResponder (Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        protected ACLMessage prepareResponse(ACLMessage request){
            ACLMessage reply = request.createReply();
            //System.out.println("(Curator) preparing response to request from: " + request.getSender().getLocalName());
            ////System.out.println("(Curator) with content: " + request.getContent());
            senderType = request.getEnvelope().getComments();
            if ("platform".equals(senderType)){
                //System.out.println("(Curator) Message received from a platform called: " + request.getSender().getLocalName());
                reply.setPerformative(ACLMessage.AGREE);

            } else if ("profiler".equals(senderType)){
                //System.out.println("(Curator) Message received from a profiler called: "+ request.getSender().getLocalName());
                reply.setPerformative(ACLMessage.AGREE);

            } else {
                reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);

            }
            return reply;
        }

        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response){

            ACLMessage reply = request.createReply();

            if("platform".equals(senderType)){
                User u;
                try {
                    u = (User)request.getContentObject();
                    reply.setContentObject((Serializable)artifactIndex.searchArtifactIDs(u.getInterests()));

                } catch (UnreadableException e) {
                    System.err.println("(Curator) Could not deserialize user");
                } catch (IOException e) {
                    System.err.println("(Curator) Could not serialize artifact id list");
                }

            } else if ("profiler".equals(senderType)){
                List<Integer> artifactIDs;
                try {
                    artifactIDs = (List<Integer>)request.getContentObject();
                    reply.setContentObject((Serializable)artifactIndex.searchArtifacts(artifactIDs));
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
                int price = 0;
                String name = "";
                try {
                    Artifact a = (Artifact) cfp.getContentObject();
                    name = a.getName();
                    price = a.getPrice();
                } catch (UnreadableException e) {
                    e.printStackTrace();
                }
                // We provide a proposal
                System.out.println("Agent "+getLocalName()+": Proposing " + proposal + " Item:" +  name + "@" + price);
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
            System.out.println("Agent "+getLocalName()+": Action successfully performed");
            ACLMessage inform = accept.createReply();
            inform.setPerformative(ACLMessage.INFORM);
            return inform;
        }

        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            System.out.println("Agent "+getLocalName()+": Proposal rejected");
        }
    }
}
