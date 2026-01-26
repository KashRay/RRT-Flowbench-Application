import org.knowm.xchart.QuickChart;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DashboardView extends JFrame implements SensorObserver {
    //Data history
    private final List<Double> xData;
    private final List<Double> yData;

    //UI Controls
    private final JComboBox<String> sensorSelector;
    private final JTextField durationInput;
    private final JButton startButton;
    private final JLabel statusLabel;

    //Logging State
    private boolean isLogging = false;
    private String selectedSensor = "Temperature"; //Default
    private long startTime;
    private double targetDuration;

    //Chart
    private final XYChart chart;
    private final XChartPanel<XYChart> chartPanel;

    public DashboardView(ArduinoModel model) {
        //Setup basic window behavior
        model.addObserver(this);
        this.setSize(900, 600);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);

        //Setup data lists
        xData = new ArrayList<>();
        yData = new ArrayList<>();

        //Add initial starting point (for empty graph)
        xData.add(0.0);
        yData.add(0.0);

        //Create a Chart using the library
        chart = QuickChart.getChart("Sensor Log", "Time (s)", "Temp (°C)", "Selected Sensor", xData, yData);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisMin(0.0);

        //Setup top panel (controls)
        this.setLayout(new BorderLayout());
        JPanel controlPanel = new JPanel(new FlowLayout());

        //Sensor selector
        controlPanel.add(new JLabel("Sensor:"));
        String[] sensors = {"Temperature", "Humidity", "Pressure"};
        sensorSelector = new JComboBox<>(sensors);
        controlPanel.add(sensorSelector);

        //Duration Input
        controlPanel.add(new JLabel("Duration (sec):"));
        durationInput = new JTextField("10", 5);
        controlPanel.add(durationInput);

        //Start button
        startButton = new JButton("Start Log");
        controlPanel.add(startButton);

        //Status
        statusLabel = new JLabel("Status: Ready");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
        controlPanel.add(statusLabel);

        this.add(controlPanel, BorderLayout.NORTH);

        //Setup center panel (graph)
        chartPanel = new XChartPanel<>(chart);
        this.add(chartPanel, BorderLayout.CENTER);

        //Button logic
        startButton.addActionListener(_ -> startLogging());
    }

    /**
     * Method to set up UI and chart for logging. Double checks if the inputted duration is valid.
     */
    private void startLogging() {
        try {
            //Parse the input time
            double seconds = Double.parseDouble(durationInput.getText());
            if (seconds <= 0) {
                JOptionPane.showMessageDialog(this, "ERROR! Please enter a positive number.");
                return;
            }

            //Lock in the selected sensor
            selectedSensor = (String)  sensorSelector.getSelectedItem();

            //Update chart visuals for new sensor
            chart.setTitle(selectedSensor + " Log");
            chart.setYAxisTitle(getUnit(selectedSensor));
            chart.getStyler().setLegendVisible(false);

            //Reset data
            xData.clear();
            yData.clear();

            //XChart requires at least one point to not crash, so we add the starting point
            xData.clear();
            yData.clear();

            //Reset logic state
            targetDuration = seconds;
            startTime = System.currentTimeMillis();
            isLogging = true;

            //Update UI controls
            startButton.setEnabled(false);
            durationInput.setEnabled(false);
            sensorSelector.setEnabled(false);
            statusLabel.setText("Status: Recording " + selectedSensor + "...");
            statusLabel.setForeground(Color.RED);
        }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "ERROR! Invalid duration.");
        }
    }

    /**
     * Method to reset UI upon completion of logging data.
     */
    private void stopLogging() {
        isLogging = false;
        startButton.setEnabled(true);
        durationInput.setEnabled(true);
        sensorSelector.setEnabled(true);
        statusLabel.setText("Status: Finished " + selectedSensor);
        statusLabel.setForeground(Color.GREEN);
    }

    /**
     * Helper method used to adjust the axes depending on the unit of measurement associated with the sensor reading.
     * @param sensor The sensor that is to be read
     * @return The unit of measurement associated with the sensor reading
     */
    private String getUnit(String sensor) {
        return switch (sensor) {
            case "Temperature" -> "Temp (°C)";
            case "Humidity" -> "Humidity (%)";
            case "Pressure" -> "Pressure (hPa)";
            default -> "Value";
        };
    }

    /**
     * Adjusts the GUI depending on sensor reading updates.
     */
    @Override
    public void update(String sensorID, double value) {
        //Always run UI updates on the Swing thread
        SwingUtilities.invokeLater(() -> {
            //Ignore data if we are not currently logging
            if (!isLogging) return;

            //Only log data if it matches the user's selection
            if (sensorID.equals(selectedSensor)) {
                //Calculate elapsed time in seconds
                long now = System.currentTimeMillis();
                double elapsedSeconds = (now - startTime) / 1000.0;

                //Check if the time is up
                if (elapsedSeconds >= targetDuration) {
                    stopLogging();
                    return;
                }

                //Add data
                xData.add(elapsedSeconds);
                yData.add(value);

                //Update the chart with the full history list
                chart.updateXYSeries("Selected Sensor", xData, yData, null);

                // Force repaint
                chartPanel.repaint();
            }
        });
    }
}
