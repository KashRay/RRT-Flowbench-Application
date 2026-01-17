public interface SensorObserver {
    /**
     *  Called when the Arduino sends new readings.
     * @param sensorID The ID or name of the sensor (e.g., "Temp", "Pressure")
     * @param value The new value measured
     */
    void update(String sensorID, double value);
}
