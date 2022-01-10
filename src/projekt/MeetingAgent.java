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

import java.util.ArrayList;

public class MeetingAgent extends Agent {
	public AID[] meetAgentsList;
	private MeetingAgentGui myGui;
	private Calendar calendar;
	private int dayOfMeeting = -1;

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
				if (dayOfMeeting >= 0) {
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
		private int repliesCnt = 0;
		private double sumOfPreference;

		@Override
		public void action() {
			switch (step) {
				case 0:
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
					break;
				case 1:
					ACLMessage reply = myAgent.receive(mt);
					if (reply != null) {
						if (reply.getPerformative() == ACLMessage.AGREE) {
							System.out.println(getAID().getLocalName() + ": " + reply.getSender().getLocalName() +
									" says that can meet on day " + dayOfMeeting);
						} else if (reply.getPerformative() == ACLMessage.REFUSE) {
							System.out.println(getAID().getLocalName() + ": " + reply.getSender().getLocalName() +
									" says that cannot attend meeting on day " + dayOfMeeting);
						}
						repliesCnt++;
						if (repliesCnt >= meetAgentsList.length) {
							step = 2;
						}
					} else {
						block();
					}
					break;
				case 2:
					ACLMessage meeting = new ACLMessage(ACLMessage.PROPOSE);
					for (AID aid : meetAgentsList) {
						meeting.addReceiver(aid);
					}
					meeting.setContent(Integer.toString(dayOfMeeting));
					meeting.setConversationId("preference");
					meeting.setReplyWith("preference" + System.currentTimeMillis()); //unique value
					meeting.setSender(getAID()); // set which agent is sending
					myAgent.send(meeting);
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("preference"),
							MessageTemplate.MatchInReplyTo(meeting.getReplyWith()));
					step = 3;
					break;
				case 3:
					reply = myAgent.receive(mt);
					if (reply != null) {
						if (reply.getPerformative() == ACLMessage.INFORM) {
							double agentPref = Double.parseDouble(reply.getContent());
							System.out.println(getAID().getLocalName() + ": " + reply.getSender().getLocalName() + " says "
									+ "preference of day " + dayOfMeeting + " is " + agentPref);
						}
						step = 4;
					} else {
						block();
					}
					break;
			}
		}

		@Override
		public boolean done() {
			if (step == 4)
				System.out.println(getAID().getLocalName() + ": request for meeting is done!");
			return (step == 4);
		}
	}

	private class ResolveMeeting extends CyclicBehaviour {
		private MessageTemplate mt;
		private int step = 0;
		private int day = -1;


		@Override
		public void action() {
			if (step == 0) {
				mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
				ACLMessage msg = myAgent.receive(mt);
				if (msg != null) {
					int day = Integer.parseInt(msg.getContent());
					ACLMessage reply = msg.createReply();

					System.out.println(getAID().getLocalName() + ": " + msg.getSender().getLocalName() +
							" is asking if I can meet on day " + day);

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
				} else {
					block();
				}
			} else if (step == 1) {
				mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE),
						MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
				ACLMessage msg = myAgent.receive(mt);
				if (msg != null) {
					ACLMessage reply = msg.createReply();
					if (msg.getPerformative() == ACLMessage.PROPOSE) {
						day = Integer.parseInt(msg.getContent());
						if (day >= 0) {
							double pref = calendar.getCalendarSlots().get(day);
							reply.setPerformative(ACLMessage.INFORM);
							reply.setContent(String.valueOf(pref));
						} else {
							reply.setPerformative(ACLMessage.REFUSE);
							reply.setContent("not-available");
							step = 2;
						}
						myAgent.send(reply);
					} else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
						calendar.getCalendarSlots().set(day, 0.0);
						System.out.println(calendar.toString());
						step = 0;
					}
				} else {
					block();
				}
			}
		}
	}
}

