import org.knowm.xchart.QuickChart;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DashboardView extends JFrame implements SensorObserver {
    //Chart Components
    private final XYChart flowChart;
    private final XYChart pressureChart;
    private final XYChart temperatureChart;
    private final XChartPanel<XYChart> flowPanel;
    private final XChartPanel<XYChart> pressurePanel;
    private final XChartPanel<XYChart> temperaturePanel;

    //View Switching Components
    private JComboBox<String> graphSelector;
    private JPanel cardPanel;
    private CardLayout cardLayout;

    //DATA HISTORY LINKS
    //X-axis (time) shared by each graph
    private final List<Double> xData;
    //Graph 1: Airflow
    private final List<Double> cfm28Data;
    private final List<Double> cfmOrificeData;
    //Graph 2: Pressures
    private final List<Double> p1Data;
    private final List<Double> p2Data;
    private final List<Double> p3Data;
    //Graph 3: Temperatures
    private final List<Double> t1Data;
    private final List<Double> t2Data;
    private final List<Double> t3Data;
    //Temporary buffers to hold values until the "commit" tick
    private double currentT1, currentT2, currentT3;
    private double currentP1, currentP2, currentP3;

    //UI Controls (Inputs)
    private JTextField valveLiftInput;
    private JTextField orificeInput;
    private JTextField durationInput;
    private JTextArea commentsArea;

    //Logging
    private JLabel cfm28Label;
    private JLabel cfmOrificeLabel;
    private JLabel massFlowRateLabel;
    private JLabel testStatusLabel;
    private JLabel[] sensorStatusLabels;

    //Buttons
    private JButton runButton;
    private JButton stopButton;
    private JButton exportButton;

    //Logic State
    private boolean isLogging = false;
    private long startTime;
    private double targetDuration;

    public DashboardView(ArduinoModel model) {
        //Setup basic window behavior
        model.addObserver(this);
        this.setTitle ("RR FSAE Flow Bench Tester");
        this.setSize(1400, 900);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLayout(new BorderLayout());

        //Setup and initialize data lists
        xData = new ArrayList<>();
        cfm28Data = new ArrayList<>(); cfmOrificeData = new ArrayList<>();
        p1Data = new ArrayList<>(); p2Data = new ArrayList<>(); p3Data = new ArrayList<>();
        t1Data = new ArrayList<>(); t2Data = new ArrayList<>(); t3Data = new ArrayList<>();

        //Add dummy data only for startup (avoid XChart crashing)
        xData.add(0.0);
        cfm28Data.add(0.0); cfmOrificeData.add(0.0);
        p1Data.add(0.0); p2Data.add(0.0); p3Data.add(0.0);
        t1Data.add(0.0); t2Data.add(0.0); t3Data.add(0.0);

        //CHART CREATION
        //Graph 1: Airflow
        flowChart = QuickChart.getChart("Airflow Calculations", "Time (s)", "CFM", "CFM @ 28", xData, cfm28Data);
        flowChart.addSeries("CFM @ Orifice", xData, cfmOrificeData);
        flowChart.getStyler().setLegendVisible(true);
        flowChart.getStyler().setXAxisMin(0.0);
        flowChart.getStyler().setToolTipsEnabled(true);
        //Graph 2: Pressures
        pressureChart = QuickChart.getChart("Pressure Sensors", "Time (s)", "Pressure (hPa)", "P1", xData, p1Data);
        pressureChart.addSeries("P2", xData, p2Data);
        pressureChart.addSeries("P3", xData, p3Data);
        pressureChart.getStyler().setLegendVisible(true);
        pressureChart.getStyler().setXAxisMin(0.0);
        pressureChart.getStyler().setToolTipsEnabled(true);
        //Graph 3: Temperatures
        temperatureChart = QuickChart.getChart("Temperature Sensors", "Time (s)", "Temp (°C)", "T1", xData, t1Data);
        temperatureChart.addSeries("T2", xData, t2Data);
        temperatureChart.addSeries("T3", xData, t3Data);
        temperatureChart.getStyler().setLegendVisible(true);
        temperatureChart.getStyler().setXAxisMin(0.0);
        temperatureChart.getStyler().setToolTipsEnabled(true);

        //Wrap charts in panels
        flowPanel = new XChartPanel<>(flowChart);
        pressurePanel = new XChartPanel<>(pressureChart);
        temperaturePanel = new XChartPanel<>(temperatureChart);

        //CHART VIEW SWITCHING LOGIC
        //Card panel (holds 3 graphs on top of each other)
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(flowPanel, "Airflow Calculations");
        cardPanel.add(pressurePanel, "Pressure Sensors");
        cardPanel.add(temperaturePanel, "Temperature Sensors");
        //Selector Dropdown
        String[] options = {"Airflow Calculations", "Pressure Sensors", "Temperature Sensors"};
        graphSelector = new JComboBox<>(options);
        graphSelector.setFont(new Font("Arial", Font.BOLD, 14));
        graphSelector.addActionListener(e -> {
            String selected = (String) graphSelector.getSelectedItem();
            cardLayout.show(cardPanel, selected);
        });
        //Container for selector + graph
        JPanel centerContainer = new JPanel(new BorderLayout());
        //Small header panel for the dropdown
        JPanel selectorHeader = new JPanel(new FlowLayout(FlowLayout.CENTER));
        selectorHeader.setBackground(Color.lightGray);
        selectorHeader.add(new JLabel("Select Graph View: "));
        selectorHeader.add(graphSelector);
        centerContainer.add(selectorHeader, BorderLayout.NORTH);
        centerContainer.add(cardPanel, BorderLayout.CENTER);

        //Create layouts
        JPanel controlPanel = createControlPanel();

        //Add to window
        this.add(controlPanel, BorderLayout.NORTH); //Controls on top
        this.add(centerContainer, BorderLayout.CENTER); //Swappable graphs in the center

        this.setVisible(true);
    }

    /**
     * Helper method to clear all graph points.
     */
    private void resetDataLists() {
        xData.clear();
        cfm28Data.clear(); cfmOrificeData.clear();
        p1Data.clear(); p2Data.clear(); p3Data.clear();
        t1Data.clear(); t2Data.clear(); t3Data.clear();
    }

    /**
     * Helper method for creating the complex layout for the inputs and status.
     * @return The complete control panel layout
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10)); //Padding
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5); //Spacing between components
        c.fill = GridBagConstraints.BOTH;

        //Calculated results (orange boxes)
        cfm28Label = createStyledLabel("CFM at 28 in H20:", new Color(255, 200, 150)); //Light orange
        c.gridx = 0; c.gridy = 0; c.gridwidth = 1; panel.add(cfm28Label, c);

        cfmOrificeLabel = createStyledLabel("CFM at Orifice:", new Color(255, 200, 150)); //Light orange
        c.gridx = 1; c.gridy = 0; c.gridwidth = 1; panel.add(cfmOrificeLabel, c);

        massFlowRateLabel = createStyledLabel("Mass Flow Rate:", new Color(255, 200, 150)); //Light orange
        c.gridx = 2; c.gridy = 0; c.gridwidth = 1; panel.add(massFlowRateLabel, c);

        //ROW 1: Inputs and Duration
        //Valve lift
        JPanel valvePanel = new JPanel(new BorderLayout());
        valvePanel.setBorder(new TitledBorder("Valve Lift:"));
        valveLiftInput = new  JTextField("0.5");
        valvePanel.add(valveLiftInput);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 1; panel.add(valvePanel, c);

        //Orifice diameter
        JPanel orificePanel = new JPanel(new BorderLayout());
        orificePanel.setBorder(new TitledBorder("Orifice Diameter:"));
        orificeInput = new  JTextField("2.0");
        orificePanel.add(orificeInput);
        c.gridx = 1; c.gridy = 1; panel.add(orificePanel, c);

        //Testing duration
        JPanel durationPanel = new JPanel(new BorderLayout());
        durationPanel.setBorder(new TitledBorder("Testing Duration (s):"));
        durationInput = new  JTextField("10");
        durationPanel.add(durationInput);
        c.gridx = 2; c.gridy = 1; panel.add(durationPanel, c);

        //ROW 2: Instructions and Comments
        //Instructions label
        JLabel instructionsLabel = new JLabel("<html><center>Instructions:<br>Enter values,<br>then hit RUN</center></html>");
        instructionsLabel.setOpaque(true);
        instructionsLabel.setBackground(new Color(220, 220, 240)); //Light purple
        instructionsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        instructionsLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        c.gridx = 0; c.gridy = 2; c.gridheight = 1; panel.add(instructionsLabel, c);

        //Comments area
        commentsArea = new JTextArea(5, 20);
        commentsArea.setBorder(BorderFactory.createTitledBorder("Comments About Trial:"));
        JScrollPane commentScroll = new JScrollPane(commentsArea);
        c.gridx = 1; c.gridy = 2; c.gridwidth = 2; c.gridheight = 2; panel.add(commentScroll, c); //Spans 2 columns and rows

        //ROW 3: Test Status
        testStatusLabel = new JLabel("Status: STOPPED");
        testStatusLabel.setOpaque(true);
        testStatusLabel.setBackground(new Color(220, 220, 240));
        testStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        testStatusLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        c.gridx = 0; c.gridy = 3; c.gridwidth = 1; c.gridheight = 1; panel.add(testStatusLabel, c);

        //ROW 4: Buttons
        //Run test button
        runButton = new JButton("Run");
        runButton.setBackground(Color.GREEN);
        runButton.addActionListener(_ -> startLogging());
        c.gridx = 0; c.gridy = 4; c.gridwidth = 1; panel.add(runButton, c);

        //Stop test button
        stopButton = new JButton("Stop");
        stopButton.setBackground(Color.RED);
        stopButton.setForeground(Color.WHITE);
        stopButton.addActionListener(_ -> stopLogging());
        c.gridx = 1; c.gridy = 4; c.gridwidth = 1; panel.add(stopButton, c);

        //Export to CSV button
        exportButton = new JButton("Export CSV");
        exportButton.setBackground(Color.YELLOW);
        //*Export logic to be added later
        c.gridx = 2; c.gridy = 4; c.gridwidth = 1; panel.add(exportButton, c);

        //RIGHT COLUMN: Sensor Status
        JPanel statusPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        sensorStatusLabels = new JLabel[6];
        String[] statusNames = {
                "Pressure Diff Sensor 1 Status", "Pressure Diff Sensor 2 Status", "Pressure Diff Sensor 3 Status",
                "Temperature Sensor 1 Status", "Temperature Sensor 2 Status", "Temperature Sensor 3 Status"
        };

        for (int i = 0; i < 6; i++) {
            sensorStatusLabels[i] = new JLabel(statusNames[i]);
            sensorStatusLabels[i].setOpaque(true);
            sensorStatusLabels[i].setBackground(new Color(200, 240, 200)); //Light green
            sensorStatusLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
            sensorStatusLabels[i].setBorder(BorderFactory.createLineBorder(Color.GRAY));
            statusPanel.add(sensorStatusLabels[i]);
        }

        c.gridx = 3; c.gridy = 0; c.gridheight = 5; c.gridwidth = 1; panel.add(statusPanel, c);

        return panel;
    }

    /**
     * Helper method for consistent output label creation.
     * @param text The label text
     * @param bg The label background color
     * @return The newly created label
     */
    private JLabel createStyledLabel(String text, Color bg) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(bg);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(150, 40));
        return label;
    }

    /**
     * Method to set up UI and chart for logging.
     * Double checks if the inputted duration is valid.
     */
    private void startLogging() {
        try {
            //Check if duration is of type double
            double seconds = Double.parseDouble(durationInput.getText());
            if (seconds <= 0) throw new NumberFormatException();

            //Clear all data points
            resetDataLists();

            //Setup logging variables
            targetDuration = seconds;
            startTime = System.currentTimeMillis();
            isLogging = true;

            //Update UI
            toggleInputs(false);
            testStatusLabel.setText("Status: RUNNING");
            testStatusLabel.setBackground(Color.GREEN);
        }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter a valid duration!", "ERROR", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Method to reset UI upon completion of logging data
     */
    private void stopLogging() {
        isLogging = false;
        toggleInputs(true);
        testStatusLabel.setText("Status: STOPPED");
        testStatusLabel.setBackground(new Color(220, 220, 240));
    }

    /**
     * Helper method used to quickly toggle on/off all button functionality for trial running.
     * @param enabled True if the buttons should be on, and false if the buttons should be off
     */
    private void toggleInputs(boolean enabled) {
        runButton.setEnabled(enabled);
        stopButton.setEnabled(!enabled);
        exportButton.setEnabled(enabled);
    }

    /**
     * Adjusts the GUI depending on sensor reading updates.
     */
    @Override
    public void update(String sensorID, double value) {
        //Always run UI updates on the Swing thread
        SwingUtilities.invokeLater(() -> {
            //Buffer the incoming value into temporary variables
            switch (sensorID) {
                case "T1": currentT1 = value; break;
                case "T2": currentT2 = value; break;
                case "T3": currentT3 = value; break;
                case "P1": currentP1 = value; break;
                case "P2": currentP2 = value; break;
                case "P3": currentP3 = value; break;
            }

            if (isLogging) {
                //Use the last sensor (P3) as the trigger to add full set of data to the graph
                if (sensorID.equals("P3")) {
                    //Get the current time for x-axis
                    long now  = System.currentTimeMillis();
                    double elapsed = (now - startTime) / 1000.0;

                    if (elapsed >= targetDuration) {
                        stopLogging();
                        return;
                    }

                    //Add time to the shared x-axis
                    xData.add(elapsed);

                    //Add buffered values to all y-axes
                    t1Data.add(currentT1); t2Data.add(currentT2); t3Data.add(currentT3);
                    p1Data.add(currentP1); p2Data.add(currentP2); p3Data.add(currentP3);

                    //Calculate flow (using the buffered P1 values)
                    double calcCFM = Math.sqrt(Math.abs(currentP1)) * 0.5;
                    cfm28Data.add(calcCFM);
                    cfmOrificeData.add(calcCFM * 0.8);

                    //Update charts
                    temperatureChart.updateXYSeries("T1", xData, t1Data, null);
                    temperatureChart.updateXYSeries("T2", xData, t2Data, null);
                    temperatureChart.updateXYSeries("T3", xData, t3Data, null);
                    pressureChart.updateXYSeries("P1", xData, p1Data, null);
                    pressureChart.updateXYSeries("P2", xData, p2Data, null);
                    pressureChart.updateXYSeries("P3", xData, p3Data, null);
                    flowChart.updateXYSeries("CFM @ 28", xData, cfm28Data, null);
                    flowChart.updateXYSeries("CFM @ Orifice", xData, cfmOrificeData, null);

                    //Update labels and repaint
                    cfm28Label.setText(String.format("CFM @ 28: %.2f", calcCFM));
                    cfmOrificeLabel.setText(String.format("CFM @ Orifice: %.2f", calcCFM * 0.8));
                    flowPanel.repaint();
                    pressurePanel.repaint();
                    temperaturePanel.repaint();
                }
            }
        });
    }
}
