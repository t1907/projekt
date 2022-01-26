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
		dayOfMeeting = -1;
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
		private int agreeCnt = 0;
		private int bestDay = 0;
		private double currentSumOfPref = 0.0;
		private double bestSumOfPref = 0.0;

		@Override
		public void action() {
			switch (step) {
				case 0:
					if (dayOfMeeting >= 0 && dayOfMeeting < 30) {
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
							System.out.println("	" + reply.getSender().getLocalName() + ": agree for meeting on day " + dayOfMeeting
							+ " preference is " + reply.getContent());
							double agentPref = Double.parseDouble(reply.getContent());
							currentSumOfPref += agentPref;
							agreeCnt++;
						}
						else if (reply.getPerformative() == ACLMessage.REFUSE) {
							System.out.println("	" + reply.getSender().getLocalName() + ": refuse of meeting on day " + dayOfMeeting);
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
					if (agreeCnt < agentsList.length){
						currentSumOfPref = 0.0;
						repliesCnt = 0;
						agreeCnt = 0;
						dayOfMeeting++;
						step = 0;
					}
					else {
						if (currentSumOfPref > bestSumOfPref) {
							bestSumOfPref = currentSumOfPref;
							bestDay = dayOfMeeting;
						}
						currentSumOfPref = 0.0;
						repliesCnt = 0;
						agreeCnt = 0;
						dayOfMeeting++;
						step = 0;
						if (dayOfMeeting >= 30) {
							System.out.println("Best day to meet is " + bestDay + ", sum of preference is " + bestSumOfPref);
							calendar.getCalendarSlots().set(bestDay, 0.0);
							System.out.println(getAID().getLocalName() + " " + calendar);
							dayOfMeeting = -1;
							bestDay = -1;
							repliesCnt = 0;
							agreeCnt = 0;
							bestSumOfPref = 0.0;
							currentSumOfPref = 0.0;
							step = 3;
						}
						break;
					}
			}
		}

		@Override
		public boolean done() {
			if (step == 3)
				System.out.println(getAID().getLocalName() + ": request for meeting is done!");
			return (step == 3);
		}
	}

	private class ReceiveMeetingRequest extends CyclicBehaviour {

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				int day = Integer.parseInt(msg.getContent());
				ACLMessage reply = msg.createReply();
				if (day >=0 && day <30) {
					double agentPref = calendar.getCalendarSlots().get(day);
					if (agentPref > 0.0) {
						reply.setPerformative(ACLMessage.AGREE);
						reply.setContent(String.valueOf(agentPref));
					} else {
						reply.setPerformative(ACLMessage.REFUSE);
						reply.setContent("not available");
					}
				}
				myAgent.send(reply);
			} else {
				block();
			}
		}
	}
}


