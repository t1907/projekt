package projekt;

import java.util.ArrayList;
import java.util.Random;

public class Calendar {
    private final int numberOfSlots;
    private ArrayList<Double> calendarSlots;

    public Calendar() {
        numberOfSlots = 30;
        calendarSlots = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < numberOfSlots; i++) {
            double preference = random.nextInt(100) / 100.0;
            calendarSlots.add(i, preference);
        }
    }

    public int getNumberOfSlots() {
        return numberOfSlots;
    }

    public ArrayList<Double> getCalendarSlots() {
        return calendarSlots;
    }

    public void setCalendarSlots(ArrayList<Double> calendarSlots) {
        this.calendarSlots = calendarSlots;
    }

    @Override
    public String toString() {
        return "Calendar{" +
                "numberOfSlots=" + numberOfSlots +
                ", calendarSlots=" + calendarSlots +
                '}';
    }
}
