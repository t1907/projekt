package projekt;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;

public class ArrangerAgent extends Agent {
    public AID[] meetAgentsList;
    private int dayOfMeeting;

    @Override
    protected void setup() {
        System.out.println("Hello! " + getAID().getLocalName() + " is ready for making meeting.");

        int interval = 20000;
        Object[] args = getArguments();
        if (args != null && args.length > 0) interval = Integer.parseInt(args[0].toString());

        dayOfMeeting = -1;

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("arranger");
        sd.setName("JADE-arranger");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new TickerBehaviour(this, interval) {
            protected void onTick() {

                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("meet");
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    meetAgentsList = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        meetAgentsList[i] = result[i].getName();
                    }
                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        });

        addBehaviour(new ReceiveMeetingRequest());
        addBehaviour(new HandleMeetingRequest());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Arranger agent " + getAID().getName() + " terminated.");
    }

    private class ReceiveMeetingRequest extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                System.out.println(getAID().getLocalName() + " received a meeting.");
                int day = Integer.parseInt(msg.getContent());
                String participant = msg.getSender().getLocalName();

                ACLMessage reply = msg.createReply();

                if (day >= 0 && day < 30) {
                    reply.setPerformative(ACLMessage.AGREE);
                    reply.setContent("OK");
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private class HandleMeetingRequest extends CyclicBehaviour {
        private double bestPreference = 0.0;
        private double tempPreference = 0.0;
        private int bestTimeSlot = -1;
        private int currTimeSlot = 0;
        private int replies = 0;
        private int step = 0;
        private MessageTemplate mt;
        private ArrayList<AID> elems = new ArrayList<>();

        @Override
        public void action() {
            if (step == 0) {
                if (dayOfMeeting > 0) {
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

                    for (AID el : meetAgentsList)
                        cfp.addReceiver(el);

                    cfp.setContent(Integer.toString(dayOfMeeting));
                    cfp.setConversationId("meeting-handle");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); //unique value
                    cfp.setSender(getAID()); // set which scheduler is sending
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("meeting-handle"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

                    elems.clear(); // remove all participants from previous meetings
                    replies = 0;
                    step = 1;
                }
            }
            else if (step == 1) {
                // add timeout

                ACLMessage reply = myAgent.receive(mt);

                if (reply != null) {
                    // Checks who wants to take part in meeting
                    if (reply.getPerformative() == ACLMessage.AGREE) {
                        System.out.println(getAID().getLocalName() + ": " + reply.getSender().getLocalName() +
                                " says that can meet on day " + dayOfMeeting);
                        elems.add(reply.getSender());
                    }
                    else if (reply.getPerformative() == ACLMessage.REFUSE)
                        System.out.println(getAID().getLocalName() + ": " + reply.getSender().getLocalName() +
                                " says that cannot attend meeting on day " + dayOfMeeting);

                    replies++;
                    if (replies >= meetAgentsList.length)
                        step = 2;
                } else {
                    block();
                }
            }
            else if (step == 2) {
                // if there any less than 2 participants then no meeting
                if (elems.size() <= 1) {
                    dayOfMeeting = -1; // -1 flag for not requesting meeting
                    step = 0;
                }
                else {
                    // Ask which is the preference for some timeslot meeting of given day
                    ACLMessage pp = new ACLMessage(ACLMessage.PROPOSE);

                    for (AID r : elems) {
                        pp.addReceiver(r);
                    }

                    pp.setContent(Integer.toString(currTimeSlot));
                    pp.setConversationId("timeslot-preference");
                    pp.setReplyWith("propose" + System.currentTimeMillis()); //unique value
                    pp.setSender(getAID()); // set which scheduler is sending
                    myAgent.send(pp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("timeslot-preference"),
                            MessageTemplate.MatchInReplyTo(pp.getReplyWith()));

                    System.out.println(getAID().getLocalName() + ": asking preference of day " + dayOfMeeting +
                            " for timeslot(" + currTimeSlot + ")");

                    tempPreference = 0.0; // reset current sum of preferences
                    replies = 0; // reset count of replies
                    step = 3;
                }
            }
            else if (step == 3) {
                ACLMessage reply = myAgent.receive(mt);

                if (reply != null) {
                    // Get preference for current timeslot and keep the best one*
                    if (reply.getPerformative() == ACLMessage.INFORM) {

                        double cpref = Double.parseDouble(reply.getContent());

                        System.out.println(getAID().getLocalName() + ": " + reply.getSender().getLocalName() + " says "
                                + "preference of day " + dayOfMeeting + " for timeslot(" + currTimeSlot + ") = " + cpref);

                        // At least one participant cannot meet on this timeslot
                        // move to next timeslot immediately
                        if (cpref == 0.0) {
                            currTimeSlot++;
                            step = 2;
                        }
                        else {
                            // sum preferences from all participants
                            tempPreference += cpref;

                            replies++;
                            if (replies >= elems.size()) {
                                // after all participants replies check if is a better preference
                                if (tempPreference > bestPreference) {
                                    bestPreference = tempPreference;
                                    bestTimeSlot = currTimeSlot; // save timeslot index
                                }
                                currTimeSlot++; // move to next timeslot
                                step = 2;
                            }
                        }

                        // check if timeslot pass is finished
                        if (currTimeSlot >= 30)
                            step = 4;
                    }
                } else {
                    block();
                }
            }
            else if (step == 4) {


                ACLMessage ap = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);

                for (AID r : elems) {
                    ap.addReceiver(r);
                }

                ap.setContent(Integer.toString(dayOfMeeting) + ";" + Integer.toString(bestTimeSlot));
                ap.setConversationId("meeting-details");
                ap.setReplyWith("meeting" + System.currentTimeMillis()); //unique value
                ap.setSender(getAID()); // set which scheduler is sending
                myAgent.send(ap);

                dayOfMeeting = -1; // -1 flag for not requesting meeting
                currTimeSlot = 0;
                bestPreference = 0.0;
                step = 0;
            }
        }
    }
}
