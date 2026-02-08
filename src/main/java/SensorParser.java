import com.fazecast.jSerialComm.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.Arrays;

/**
 * Acts as the initializer that binds the Model and View together.
 * Reads data from the appropriate USB port and parses the data.
 *
 * @author Rayyan Kashif
 * @author Abdullah Khan
 * @version 1.0
 */
public class SensorParser {
    public static void main(String[] args) {
        //Create the Model
        ArduinoModel model = new ArduinoModel();

        //Create the view (GUI) on the event dispatch thread (for thread safety)
        javax.swing.SwingUtilities.invokeLater(() -> new DashboardView(model));

        //Start data acquisition thread
        new Thread(() -> {
            SerialPort connectedPort = null;

            //Define potential USB ports (Linux, Windows)
            String[] potentialPorts = {"/dev/ttyACM0", "COM7"};

            //Iterate and try to connect
            for (String portName : potentialPorts) {
                SerialPort port = SerialPort.getCommPort(portName);
                port.setBaudRate(115200);
                port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);

                //If the port is found, stop looking
                if (port.openPort()) {
                    connectedPort = port;
                    System.out.println("Successfully connected to serial port: " + portName);
                    break;
                }
            }

            if (connectedPort != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connectedPort.getInputStream()));

                    while (true) {
                        try {
                            //Read from port
                            String line = reader.readLine();
                            if (line != null) {
                                //Parse CSV data (format: T1,T2,T3,P1,P2,P3)
                                String[] lineParts = line.trim().split(",");

                                //Validate data structure
                                if (lineParts.length == 6) {
                                    try {
                                        //Parse all values
                                        double t1 = Double.parseDouble(lineParts[0]);
                                        double t2 = Double.parseDouble(lineParts[1]);
                                        double t3 = Double.parseDouble(lineParts[2]);
                                        double p1 = Double.parseDouble(lineParts[3]);
                                        double p2 = Double.parseDouble(lineParts[4]);
                                        double p3 = Double.parseDouble(lineParts[5]);

                                        //Update model
                                        model.receiveReading("T1", round(t1));
                                        model.receiveReading("T2", round(t2));
                                        model.receiveReading("T3", round(t3));
                                        model.receiveReading("P1", round(p1));
                                        model.receiveReading("P2", round(p2));
                                        model.receiveReading("P3", round(p3));
                                    }
                                    catch (NumberFormatException nfe) {
                                        //If the second part isn't a number, just ignore the line
                                        System.err.println("Skipping malformed data: " + line);
                                    }
                                }
                            }
                        }
                        catch (Exception e) { e.printStackTrace(); }
                    }
                }
                catch (Exception e) { e.printStackTrace(); }
            }
            else {
                //If no known ports work, return failure message
                System.out.println("Failed to open port");
                System.out.println("Available Ports:");
                System.out.println(Arrays.toString(SerialPort.getCommPorts()));
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
