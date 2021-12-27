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
	public AID[] meetAgents;

	private MeetingAgentGui myGui;
	private Calendar calendar;
	private int dayOfMeeting = -1;


	@Override
	protected void setup() {
		System.out.println("Hello! " + getAID().getLocalName() + " is ready for making meeting.");
		calendar = new Calendar(30);

		myGui = new MeetingAgentGui(this);
		myGui.display();

		int interval = 20000;
		Object[] args = getArguments();
		if (args != null && args.length > 0) interval = Integer.parseInt(args[0].toString());

		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("meetAgent");
		sd.setName("meetAgent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		addBehaviour(new TickerBehaviour(this, interval)
		{
			protected void onTick()
			{
				if (dayOfMeeting > 0){
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("meetAgent");
					template.addServices(sd);
					try {
						System.out.println(getAID().getLocalName() + ": the following agents have been found");
						DFAgentDescription[] result = DFService.search(myAgent, template);
						meetAgents = new AID[result.length];
						for (int i = 0; i < result.length; ++i) {
							meetAgents[i] = result[i].getName();
							System.out.println(meetAgents[i].getLocalName());
						}
					} catch (FIPAException fe) {
						fe.printStackTrace();
					}
					myAgent.addBehaviour(new RequestMeeting());
				}
			}
		});
	}

	public void requestMeeting(final int index)
	{
		addBehaviour(new OneShotBehaviour()
		{
			public void action()
			{
				dayOfMeeting = index;
				System.out.println(getAID().getLocalName() + ": request meeting for " + dayOfMeeting + " accepted");
			}
		});
	}

	public boolean isDayAvailable(int day) {
		Double preference = calendar.getCalendarSlots().get(day);
		if (preference != null || preference > 0.0) {
			return true;
			}
		return false;
	}

	public double getPreference(int dayOfMeeting){
		Double preference = calendar.getCalendarSlots().get(dayOfMeeting);
		return preference;
	}

	@Override
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		System.out.println("Meeting agent " + getAID().getName()+ " terminating.");
	}

	private class RequestMeeting extends Behaviour {
		private MessageTemplate mt;
		private int step = 0;
		private int repliesCnt = 0;


		public void action() {
			switch (step) {
				case 0:
					ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
					for (int i = 0; i < meetAgents.length; ++i) {
						cfp.addReceiver(meetAgents[i]);
					}
					cfp.setContent(Double.toString(getPreference(dayOfMeeting)));
					cfp.setConversationId("meetAgent");
					cfp.setReplyWith("cfp" + System.currentTimeMillis()); //unique value
					cfp.setSender(getAID());
					myAgent.send(cfp);
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("meetAgent"),
							MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
					step = 1;
				case 1:
					ACLMessage reply = myAgent.receive(mt);
					if (reply != null) {
						if (reply.getPerformative() == ACLMessage.PROPOSE) {
							double agentPref = Double.parseDouble(reply.getContent());
							if (bestSeller == null || price < bestPrice) {
								bestPrice = price;
								bestSeller = reply.getSender();
							}
						}
						repliesCnt++;
						if (repliesCnt >= meetAgents.length) {
							step = 2;
						}
					}
					else {
						block();
					}
					break;
			}
		}
		public boolean done() {
			if (step == 2) {
				System.out.println("Attempt failed: " + dayOfMeeting + " not available for meet");
			}
			return ((step == 2) || step == 4);
		}
	}
}
