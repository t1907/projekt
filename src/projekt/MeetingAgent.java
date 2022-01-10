package projekt;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class MeetingAgent extends Agent {
	public AID[] meetAgentsList;
	private MeetingAgentGui myGui;
	private Calendar calendar;
	private int dayOfMeeting;

	@Override
	protected void setup() {
		System.out.println("Hello! " + getAID().getLocalName() + " is ready for making meeting.");
		calendar = new Calendar();
		myGui = new MeetingAgentGui(this);
		myGui.display();

		int interval = 20000;
		Object[] args = getArguments();
		if (args != null && args.length > 0) interval = Integer.parseInt(args[0].toString());

		System.out.println(getAID().getLocalName() + " " + calendar);

		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("meetingAgent");
		sd.setName("meetingAgent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		addBehaviour(new TickerBehaviour(this, interval) {
			protected void onTick() {
				if (dayOfMeeting > 0) {
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("meetingAgent");
					template.addServices(sd);
					try {
						System.out.println(getAID().getLocalName() + ": the following agents have been found");
						DFAgentDescription[] result = DFService.search(myAgent, template);
						meetAgentsList = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							meetAgentsList[i] = result[i].getName();
							System.out.println(meetAgentsList[i].getLocalName());
						}
					} catch (FIPAException fe) {
						fe.printStackTrace();
					}
					myAgent.addBehaviour(new RequestMeeting());
				}
			}
		});
		addBehaviour(new ResolveMeeting());
	}

	public void requestMeeting(final int index) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				dayOfMeeting = index;
				System.out.println(getAID().getLocalName() + ": request meeting for " + dayOfMeeting + " accepted");
			}
		});
	}

	public boolean isDayAvailable(int day) {
		Double preference = calendar.getCalendarSlots().get(day);
		return preference != 0.0;
	}

	public double getPreference(int dayOfMeeting) {
		return calendar.getCalendarSlots().get(dayOfMeeting);
	}

	@Override
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		myGui.dispose();
		System.out.println("Meeting agent " + getAID().getName() + " terminating.");
	}

	private class RequestMeeting extends Behaviour {
		private MessageTemplate mt;
		private int step = 0;

		@Override
		public void action() {
			if (step == 0) {
				if (dayOfMeeting > 0) {
					System.out.println(getAID().getLocalName() + ": is looking for meeting on day " + dayOfMeeting);

					ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
					for (int i = 0; i < meetAgentsList.length; ++i) {
						cfp.addReceiver(meetAgentsList[i]);
					}
					cfp.setContent(Integer.toString(dayOfMeeting));
					cfp.setConversationId("meeting");
					cfp.setReplyWith("cfp " + System.currentTimeMillis()); //unique value
					cfp.setSender(getAID());
					myAgent.send(cfp);
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("meeting"),
							MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

					step = 1;
				}
			} else if (step == 1) {
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					if (reply.getPerformative() == ACLMessage.AGREE) {
						if (reply.getContent().equals("OK"))
							step = 2;
					}else if (reply.getPerformative() == ACLMessage.REFUSE) {
						if (reply.getContent().equals("NOT")){
							System.out.println(dayOfMeeting + " is not available.");
						}
					}
				} else
					block();
			}
		}

		@Override
		public boolean done() {
			if (step == 2)
				System.out.println(getAID().getLocalName() + ": request for meeting is done!");
			return (step == 2);
		}
	}

	private class ResolveMeeting extends CyclicBehaviour {
		private MessageTemplate mt;
		private int step = 0;
		private int day;
		private int slot;

		@Override
		public void action() {
			if (step == 0) {
				mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
				ACLMessage msg = myAgent.receive(mt);
				if (msg != null) {
					day = Integer.parseInt(msg.getContent());
					ACLMessage reply = msg.createReply();

					System.out.println(getAID().getLocalName() + ": " + msg.getSender().getLocalName() + " is asking if I can meet on day " + day);

					if (isDayAvailable(day)) {
						reply.setPerformative(ACLMessage.AGREE);
						reply.setContent("OK");
						step = 1;
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("NOT");
						step = 2;
					}
					myAgent.send(reply);
				}else {
					block();
				}
			}
			if (step == 1){
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);

				ACLMessage msg = myAgent.receive(mt);
				if (msg != null){
					System.out.println(msg);
				}
			}
		}
	}
}

