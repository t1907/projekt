package projekt;

import java.util.ArrayList;
import java.util.Random;

public class Calendar {
    private int numberOfSlots;
    private ArrayList<Double> calendarSlots;

    public Calendar(int numberOfSlots) {
        this.numberOfSlots = numberOfSlots;
        calendarSlots = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 30; i++) {
            double preference = random.nextInt(100) / 100.0;
            calendarSlots.add(i, preference);
        }
    }

    public int getNumberOfSlots() {
        return numberOfSlots;
    }

    public void setNumberOfSlots(int numberOfSlots) {
        this.numberOfSlots = numberOfSlots;
    }

    public ArrayList<Double> getCalendarSlots() {
        return calendarSlots;
    }

    public void setCalendarSlots(ArrayList<Double> calendarSlots) {
        this.calendarSlots = calendarSlots;
    }

    @Override
    public String toString() {
        return "Calendar: " + calendarSlots;
    }
}
