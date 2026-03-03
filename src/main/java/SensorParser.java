import com.fazecast.jSerialComm.*;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Acts as the initializer that binds the Model and View together.
 * Continuously scans for if the Arduino USB is connected.
 * Reads data from the appropriate USB port and parses the data.
 *
 * @author Rayyan Kashif
 * @author Abdullah Khan
 * @version 1.0
 */
public class SensorParser {
    private static DashboardView dashboard;

    public static void main(String[] args) {
        //Create the model
        ArduinoModel model = new ArduinoModel();

        //Create the view (ensure it's ready before we start scanning)
        try {
            SwingUtilities.invokeAndWait(() -> dashboard = new DashboardView(model));
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Start the connection manager thread
        new Thread(() -> runConnectionLoop(model)).start();
    }

    /**
     * The main loop that handles connecting, reading, and reconnecting.
     * @param model The simulated Arduino model
     */
    public static void runConnectionLoop(ArduinoModel model) {
        //Define potential USB ports
        String[] potentialPorts = {"/dev/ttyACM0", "COM7"}; //Linux, Windows
        SerialPort connectedPort = null;

        //Permanent loop to keep the app alive forever
        while (true) {
            //Searching for the Arduino
            dashboard.logMessage("Scanning for Arduino (" + String.join(", ", potentialPorts) + ")...");

            while (connectedPort == null) {
                //Iterate and try to connect
                for (String portName : potentialPorts) {
                    try {
                        SerialPort port = SerialPort.getCommPort(portName);
                        port.setBaudRate(115200);
                        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

                        //If the port is found, stop looking
                        if (port.openPort()) {
                            connectedPort = port;
                            dashboard.logMessage("Successfully connected to serial port: " + portName);

                            //Unblock the GUI buttons
                            SwingUtilities.invokeLater(() -> dashboard.setDeviceConnected(true));
                            break;
                        }
                    }
                    catch (Exception e) {
                        //Port likely doesn't exist or is busy, just keep trying
                    }
                }

                //If still not found, wait 2 seconds before scanning again to save CPU
                if (connectedPort == null) {
                    try {
                        Thread.sleep(2000);
                    }
                    catch (InterruptedException e) {
                        return;
                    }
                }
            }

            //If we are here, we are connected
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connectedPort.getInputStream()))) {
                //Discard the very first line caught mid-transmission
                reader.readLine();

                //Read loop
                while (connectedPort.isOpen()) {
                    String line = reader.readLine();

                    //Attempt to parse the line
                    if (line != null) {
                        System.out.println(line);
                        parseAndNotify(line, model);
                    }
                    else {
                        //If line is null, the stream has closed (device is unplugged)
                        throw new java.io.IOException("Device unplugged or stream ended");
                    }
                }
            }
            catch (Exception e) {
                dashboard.logMessage("ERROR! Connection Lost. (" + e.getMessage() + ")");
            }
            finally {
                //Cleanup
                connectedPort.closePort();
                connectedPort = null;

                //Reblock the GUI buttons
                SwingUtilities.invokeLater(() -> dashboard.setDeviceConnected(false));

                try {
                    //Give the system a second to breath before rescanning
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Helper method to parse the CSV line and update the model.
     * @param line The current line passed by the Arduino
     * @param model The simulated Arduino model
     */
    private static void parseAndNotify(String line, ArduinoModel model) {
        try {
            //Parse CSV data (format: T1,T2,T3,P1,P2,P3)
            String[] lineParts = line.trim().split(",");

            //Validate data structure
            if (lineParts.length == 6) {
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
        }
        catch (Exception e) {
            //Ignore malformed lines
        }
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
