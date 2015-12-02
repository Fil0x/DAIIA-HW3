import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.states.MsgReceiver;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class QueenAgent extends Agent {

    private int BOARD_SIZE;

    private int id, pos;
    private List<Integer> predPos;
    private Set<Integer> triedPos;
    private int solNum = 1;

    protected void setup() {



        Object[] args = getArguments();
        if (args == null){
            System.err.println("WHAT THE FUCK");
        }
        this.id = Integer.valueOf((String)args[0]);
        this.predPos = new ArrayList<>();
        this.triedPos = new TreeSet<>();
        this.BOARD_SIZE = Integer.valueOf((String) args[1]);

        addBehaviour(new DoOnReceive(this));

        System.out.println("QueenAgent: " + id + " has started.");

        // Register queen N to the DF
        ServiceDescription sd1 = new ServiceDescription();
        sd1.setType("queen");
        sd1.setName("" + id);

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd1);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        if (id == 0){
            doWait(2000);
            ACLMessage start = new ACLMessage(ACLMessage.INFORM);
            try {
                start.setContentObject(new ArrayList<Integer>());
            } catch (IOException e) {
                e.printStackTrace();
            }
            start.addReceiver(getAID());
            //start.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            send(start);
        }
    }

    protected void takeDown() {
        System.out.println("QueenAgent: " + id + " has stopped.");
    }

    private class DoOnReceive extends MsgReceiver {
        public DoOnReceive(Agent a) {
            super(a, MessageTemplate.MatchAll(), //IPANames.InteractionProtocol.FIPA_REQUEST),
                    MsgReceiver.INFINITE, new DataStore(), "key");
        }

        private boolean isSafe(List<Integer> prev, int row, int col) {
            int bDown = row + col;
            int bUp = row - col;
            for (int i = 0; i < prev.size(); i++) {
                // Diagonals
                if(prev.get(i) == (i + bUp) || prev.get(i) == (-i + bDown)) // y = x + b, y = -x + b
                    return false;
                // Rows
                else if(prev.get(i) == row)
                    return false;
            }
            return true;
        }

        private int findNextPos(List<Integer> prev, Set<Integer> triedPos) {
            for (int i = 0; i < BOARD_SIZE; i++) {
                if (triedPos.contains(i)) continue;
                // i is the row
                // The upwards diagonal , (cartesian) y = i, x = id
                if(isSafe(prev, i, id))
                    return i;
            }

            return -1;
        }

        private void move(ACLMessage request) {

            int new_pos = findNextPos(predPos, triedPos);

            if(new_pos == -1) {
                // BACKTRACK
                AID pred = getPred();
                if (pred == null){
                    System.out.println("Could not find solution.");
                } else {
                    ACLMessage backtrack = new ACLMessage(ACLMessage.REQUEST);
                    backtrack.addReceiver(pred);
                    send(backtrack);
                }
            }
            else {
                AID succ = getSucc();
                if(succ == null) {
                    // we have found a solution

                    System.out.println("Solution " + solNum++);
                    ArrayList<Integer> solution = new ArrayList<>(predPos);
                    solution.add(new_pos);
                    triedPos.add(new_pos);
                    pprint(solution);
                    ACLMessage backtrack = new ACLMessage(ACLMessage.REQUEST);
                    AID pred = getPred();
                    if (pred == null){
                        System.out.println("We did it ladiez");
                    } else {
                        backtrack.addReceiver(getPred());
                        send(backtrack);
                    }

                }
                else {
                    ACLMessage go = new ACLMessage(ACLMessage.INFORM);
                    //go.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    go.addReceiver(succ);
                    triedPos.add(new_pos);
                    // Copy the current list and add ourselves
                    List<Integer> updatedPredPos = new ArrayList<>(predPos);
                    updatedPredPos.add(new_pos);
                    try {
                        go.setContentObject((Serializable) updatedPredPos);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    send(go);
                }
            }
        }

        private AID getSucc() {
            AID successor;
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("queen");
            sd.setName("" + (id + 1));
            dfd.addServices(sd);
            DFAgentDescription[] result = new DFAgentDescription[0];
            try {
                result = DFService.search(getAgent(), dfd);
            } catch (FIPAException e) {
                e.printStackTrace();
            }
            if (result.length > 0) {
                // We have a successor
                successor = result[0].getName();
            }
            else {
                // Last one
                successor = null;
            }

            return successor;
        }

        private void pprint(List<Integer> results) {
            StringBuilder s = new StringBuilder();

            for (int i = 0; i < results.size(); i++)
                s.append(" _");
            s.append("\n");
            for(int col = 0; col < results.size(); col++) {
                for (int row = 0; row < results.size(); row++) {
                    s.append("|");
                    if (results.get(row) == col) {
                        s.append(row);
                    } else {
                        s.append("_");
                    }
                }
                s.append("|\n");
            }

            System.out.println(s.toString());
        }

        private AID getPred() {
            AID pred;
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("queen");
            sd.setName("" + (id - 1));
            dfd.addServices(sd);
            DFAgentDescription[] result = new DFAgentDescription[0];
            try {
                result = DFService.search(getAgent(), dfd);
            } catch (FIPAException e) {
                e.printStackTrace();
            }
            if (result.length > 0) {
                // We have a successor
                pred = result[0].getName();
            }
            else {
                // Last one
                pred = null;
            }

            return pred;
        }

        @Override
        protected void handleMessage(ACLMessage msg) {
            ACLMessage reply = msg.createReply();
            //reply.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
            // GO MESSAGE
            if(msg.getPerformative() == ACLMessage.INFORM) {
                //System.out.println("Queen: "+id+" got \"GO\" mesage");
                try {
                    // Get the positions from the message
                    predPos = (List<Integer>)msg.getContentObject();
                    triedPos = new TreeSet<>();
                } catch (UnreadableException e) {
                    e.printStackTrace();
                }

                move(msg);
            }
            // BACKTRACK MESSAGE
            else if(msg.getPerformative() == ACLMessage.REQUEST) {
                //System.out.println("Queen: "+id+" got \"BACKTRACK\" mesage");
                move(msg);
            }

            getAgent().addBehaviour(new DoOnReceive(getAgent()));
        }
    }
}
