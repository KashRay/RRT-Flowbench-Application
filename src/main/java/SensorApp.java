/**
 * Acts as the initializer that binds the Model and View together.
 * Loops and simulates the Arduino sending data.
 * In the real project, the random number generation can be replaced with code that reads from the Raspberry Pi's Serial/USB port.
 *
 * @author Rayyan Kashif
 * @version 1.0
 */
public class SensorApp {
    public static void main(String[] args) {
        //Create the Model
        ArduinoModel model = new ArduinoModel();

        //Create the view (GUI) on the event dispatch thread (for thread safety)
        javax.swing.SwingUtilities.invokeLater(() -> new DashboardView(model));

        //Simulate Arduino Input Loop
        new Thread(() -> {
            try {
                //Initial base values
                double t1 = 24.0, t2 = 25.0, t3 = 40.0; //Celsius
                double p1 = 0.0; //Differential pressure (starts at 0)
                double p2 = 1013.25; //Atmospheric pressure (hPa)
                double p3 = 0.0;

                boolean rampingUp = true;

                while (true) {
                    //Simulate pressure sweep (P1)
                    if (rampingUp) {
                        p1 += 0.5 + Math.random();
                        if (p1 > 72.0) rampingUp = false;
                    } else {
                        p1 -= 0.5 + Math.random();
                        if (p1 < 0.0) {
                            p1 = 0.0;
                            rampingUp = true;
                        }
                    }

                    //Simulate atmospheric fluctuation (P2)
                    p2 = 1013.0 + (Math.random() - 0.5);

                    //Simulate temperature rise (vacuum motors heating up)
                    if (t1 < 60) t1 += 0.05 * Math.random();
                    t2 += (Math.random() - 0.5) * 0.1;

                    //Send data every 100ms
                    model.recieveReading("T1", round(t1));
                    model.recieveReading("T2", round(t2));
                    model.recieveReading("T3", round(t3));
                    model.recieveReading("P1", round(p1));
                    model.recieveReading("P2", round(p2));
                    model.recieveReading("P3", round(p3));
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
