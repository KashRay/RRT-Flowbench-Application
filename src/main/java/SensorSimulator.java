import com.fazecast.jSerialComm.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Acts as the initializer that binds the Model and View together.
 * Loops and simulates the Arduino sending data.
 * In the real project, the random number generation can be replaced with code that reads from the Raspberry Pi's Serial/USB port.
 *
 * @author Rayyan Kashif
 * @version 1.0
 */
public class SensorSimulator {
    public static void main(String[] args) {
        //Create the Model
        ArduinoModel model = new ArduinoModel();

        //Create the view (GUI) on the event dispatch thread (for thread safety)
        javax.swing.SwingUtilities.invokeLater(() -> new DashboardView(model));

        //Get data from arduino
        new Thread(() -> {
            try {
                //Open Serial Port
                SerialPort port = SerialPort.getCommPort("/dev/ttyACM0");
                port.setBaudRate(9600);
                port.setComPortTimeouts(
                    SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                    1000,
                    0
                );

                if (!port.openPort()) { 
                    System.out.println("Connected to serial port");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(port.getInputStream()));

                    while (true) {
                        try {
                            //Read from Port
                            String line = reader.readLine();

                            if (line != null) {
                                System.out.println(line);

                                // //Send data every 100ms
                                // model.recieveReading("T1", round(t1));
                                // model.recieveReading("T2", round(t2));
                                // model.recieveReading("T3", round(t3));
                                // model.recieveReading("P1", round(p1));
                                // model.recieveReading("P2", round(p2));
                                // model.recieveReading("P3", round(p3));
                                Thread.sleep(100);
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                else System.out.println("Failed to open port");
            }
            catch (Exception e) {
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
