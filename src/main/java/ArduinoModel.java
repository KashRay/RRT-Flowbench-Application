import java.util.*;

public class ArduinoModel {
    private final List<SensorObserver> observers;

    public ArduinoModel() {
        this.observers = new ArrayList<>();
    }

    /**
     * Used to add listeners.
     * @param observer The listener to be added
     */
    public void addObserver(SensorObserver observer) {
        observers.add(observer);
    }

    /**
     * Notification system for informing listeners of sensor updates.
     * @param sensorID The ID of the sensor that was updated
     * @param value The current reading of the sensor
     */
    private void notifyObservers(String sensorID, double value) {
        for (SensorObserver observer : observers) {
            observer.update(sensorID, value);
        }
    }

    /**
     * The system that repeatedly fetches new readings from the Arduino and passes it to the listeners.
     * @param sensorID The ID of the sensor that was updated
     * @param value The current reading of the sensor
     */
    public void recieveReading(String sensorID, double value) {
        notifyObservers(sensorID, value);
    }
}
