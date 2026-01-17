public class SensorApp {
    /**
     * Loops and simulates the Arduino sending data.
     * In the real project, the random number generation can be replaced with code
     * that reads from the Raspberry Pi's Serial/USB port.
     * @param args Main method arguments
     */
    public static void main(String[] args) {
        //Create the Model
        ArduinoModel model = new ArduinoModel();

        //Create the view (GUI) on the event dispatch thread (for thread safety)
        javax.swing.SwingUtilities.invokeLater(() -> new DashboardView(model));

        //Simulate Arduino Input Loop
        new Thread(() -> {
            try {
                while (true) {
                    //Sensor 1 - Simulate Temperature (20-35°C)
                    double simulatedTemp = 20 + Math.random() * 15;
                    model.recieveReading("Temperature", Math.round(simulatedTemp * 10.0) / 10.0);

                    //Sensor 2 - Simulate Humidity (40-80%)
                    double simulatedHumidity = 40 + Math.random() * 40;
                    model.recieveReading("Humidity", Math.round(simulatedHumidity * 10.0) / 10.0);

                    //Sensor 3 - Simulate Pressure (1000 - 1020hPa)
                    double pressure = 1000 + Math.random() * 20;
                    model.recieveReading("Pressure", Math.round(pressure * 10.0) / 10.0);

                    //Send data every 100ms
                    Thread.sleep(100);
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
