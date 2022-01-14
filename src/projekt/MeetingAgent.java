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
	public AID[] agentsList;
	private MeetingAgentGui myGui;
	private Calendar calendar;
	private int dayOfMeeting;

	@Override
	protected void setup() {
		calendar = new Calendar();
		myGui = new MeetingAgentGui(this);
		myGui.display();

		int interval = 10000;
		Object[] args = getArguments();
		if (args != null && args.length > 0) interval = Integer.parseInt(args[0].toString());

		System.out.println(getAID().getLocalName() + " " + calendar);

		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("meetingAgent");
		sd.setName("JADE-meetingAgent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		addBehaviour(new TickerBehaviour(this, interval) {
			protected void onTick() {
				if (dayOfMeeting >= 0 && dayOfMeeting < 30) {
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("meetingAgent");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template);
						agentsList = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							agentsList[i] = result[i].getName();
						}
					} catch (FIPAException fe) {
						fe.printStackTrace();
					}
					myAgent.addBehaviour(new RequestMeeting());
				}
			}
		});
		addBehaviour(new ReceiveMeetingRequest());
	}

	public void requestMeeting(final int index) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				dayOfMeeting = index;
				System.out.println(getAID().getLocalName() + ": request meeting for " + dayOfMeeting + " accepted");
			}
		});
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
		private int bestDay;
		private double agentPref;
		private double currentSumOfPref = 0.0;
		private double bestSumOfPref = 0.0;

		@Override
		public void action() {
			switch (step) {
				case 0:
					if (dayOfMeeting > 0) {
						System.out.println(getAID().getLocalName() + ": is looking for meeting on day " + dayOfMeeting);
						ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
						for (AID aid : agentsList) {
							cfp.addReceiver(aid);
						}
						cfp.setContent(Integer.toString(dayOfMeeting));
						cfp.setConversationId("meeting");
						cfp.setReplyWith("cfp " + System.currentTimeMillis());
						cfp.setSender(getAID());
						myAgent.send(cfp);
						mt = MessageTemplate.and(MessageTemplate.MatchConversationId("meeting"),
								MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
						step = 1;
					}
					break;
				case 1:
					ACLMessage reply = myAgent.receive(mt);
					if (reply != null) {
						if (reply.getPerformative() == ACLMessage.AGREE) {
							System.out.println(reply.getSender().getLocalName() + " agree for meeting " + dayOfMeeting +
									" preference " + reply.getContent());
							agentPref = Double.parseDouble(reply.getContent());
							currentSumOfPref += agentPref;
						}
						else if (reply.getPerformative() == ACLMessage.REFUSE) {
							System.out.println(reply.getSender().getLocalName() + " refuse of meeting  " + dayOfMeeting);
						}
						repliesCnt++;
						if (repliesCnt >= agentsList.length) {
							step = 2;
						}
					} else {
						block();
					}
					break;
				case 2:
					System.out.println("Current sum of preference is " + currentSumOfPref);
					if (currentSumOfPref > bestSumOfPref){
						bestSumOfPref = currentSumOfPref;
						bestDay = dayOfMeeting;
					}
					dayOfMeeting = 1;
					currentSumOfPref = 0.0;
					step = 0;

					if (dayOfMeeting > 30){
						step = 3;
					}
					break;
				case 3:
					step = 4;
					break;
				case 4:
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

	private class ReceiveMeetingRequest extends CyclicBehaviour {
		private MessageTemplate mt;
		private int step = 0;
		private int day;

		@Override
		public void action() {
			if (step == 0) {
				mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
				ACLMessage msg = myAgent.receive(mt);
				if (msg != null) {
					day = Integer.parseInt(msg.getContent());
					ACLMessage reply = msg.createReply();
					System.out.println(getAID().getLocalName() + ": " + msg.getSender().getLocalName() +
							" is asking if I can meet on day " + day);

					double agentPref = calendar.getCalendarSlots().get(day);
					if (agentPref > 0.0) {
						reply.setPerformative(ACLMessage.AGREE);
						reply.setContent(String.valueOf(agentPref));
						step = 1;
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("not-available");
						step = 2;
					}
					myAgent.send(reply);
				} else {
					block();
				}
			}
		}
	}
}


