public class SensorApp {
    /**
     * Loops and simulates the Arduino sending data.
     * In the real project, the random number generation can be replaced with code that reads from the Raspberry Pi's Serial/USB port.
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
                //Initial base values for smooth random walking
                double t1 = 25.0, t2 = 30.0, t3 = 60.0;
                double p1 = 1000.0, p2 = 1010.0, p3 = 990.0;

                while (true) {
                    //Simulate temperatures (walking values)
                    t1 += (Math.random() - 0.5); //Varies by +/- 0.5
                    t2 += (Math.random() - 0.5);
                    t3 += (Math.random() - 0.5);

                    model.recieveReading("T1", round(t1));
                    model.recieveReading("T2", round(t2));
                    model.recieveReading("T3", round(t3));

                    //Simulates pressures
                    p1 = 1000 + (Math.random() * 20);
                    p2 = 1010 + (Math.random() * 5);
                    p3 = 990 + (Math.random() * 5);

                    model.recieveReading("P1", round(p1));
                    model.recieveReading("P2", round(p2));
                    model.recieveReading("P3", round(p3));

                    //Send data every 100ms
                    Thread.sleep(100);
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Helper to round to simulated numbers.
     * @param value The double to be round
     * @return The double rounded to the 1st decimal place
     */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
