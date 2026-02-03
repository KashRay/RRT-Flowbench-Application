/**
 * Called when the Arduino sends new readings.
 *
 * @author Rayyan Kashif
 * @version 1.0
 */
public interface SensorObserver {
    /**
     *
     * @param sensorID The ID or name of the sensor (e.g., "Temp", "Pressure")
     * @param value The new value measured
     */
    void update(String sensorID, double value);
}
