import org.knowm.xchart.QuickChart;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Objects;

/**
 * Handles the visualization (charts, labels, layouts).
 * Implements SensorObserver to listen to the arduino model.
 *
 * @author Rayyan Kashif
 * @author Abdullah Khan
 * @version 1.0
 */
public class DashboardView extends JFrame implements SensorObserver {
    //Constants
    public static double DISCHARGE_COEFFICIENT = 0.61;
    public static double PIPE_INNER_DIAMETER = 4.0;
    public static double EXPANSIBILITY_FACTOR = 1;
    public static double SPECIFIC_GAS_CONSTANT = 287.1;
    public static double INCHES_TO_METERS = 0.0254;
    public static double CELSIUS_TO_KELVIN = 273.15;
    public static double KgPerS_TO_CFM = 2118.88;
    public static double DIFFERENTIAL_PRESSURE_IN_H20_TO_Pa = 249.09;

    //Chart Components
    private XYChart flowChart;
    private XYChart pressureChart;
    private XYChart temperatureChart;
    private XChartPanel<XYChart> flowPanel;
    private XChartPanel<XYChart> pressurePanel;
    private XChartPanel<XYChart> temperaturePanel;

    //View Switching Components
    private JComboBox<String> runSelector;
    private JComboBox<String> graphSelector;
    private JPanel cardPanel;
    private CardLayout cardLayout;

    //DATA HISTORY LINKS
    //X-axis (time) shared by each graph
    private List<Double> xData;
    //Graph 1: Airflow
    private List<Double> cfm28Data;
    private List<Double> cfmOrificeData;
    private List<Double> massFlowData;
    //Graph 2: Pressures
    private List<Double> p1Data;
    private List<Double> p2Data;
    private List<Double> p3Data;
    //Graph 3: Temperatures
    private List<Double> t1Data;
    private List<Double> t2Data;
    private List<Double> t3Data;
    //Temporary buffers to hold values until the "commit" tick
    private double currentT1, currentT2, currentT3;
    private double currentP1, currentP2, currentP3;
    //Test run storage (for exporting to CSV)
    private final List<RunSnapshot> sessionRuns = new ArrayList<>();

    //UI Controls
    private JTextField valveLiftInput;
    private JComboBox<String> orificeInput;
    private JTextField durationInput;
    private JTextArea commentsArea;

    //Logging
    private JLabel cfm28Label;
    private JLabel cfmOrificeLabel;
    private JLabel massFlowRateLabel;
    private JLabel testStatusLabel;
    private JLabel[] sensorStatusLabels;
    private JTextArea activityLog;

    //Buttons
    private JButton runButton;
    private JButton stopButton;
    private JButton exportButton;

    //Logic State
    private boolean isLogging = false;
    private long startTime;
    private double targetDuration;
    private double currentValveLift;
    private double currentOrificeDiameter;

    public DashboardView(ArduinoModel model) {
        //Setup basic window behavior
        model.addObserver(this);
        this.setTitle ("RR FSAE Flow Bench Tester");
        this.setSize(1600, 900);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLayout(new BorderLayout());

        //Create GUI with helper methods
        initializeData();
        initializeCharts();
        setupLayout();
        this.setVisible(true);

        //Send initial startup log message
        logMessage("System Initialized. Ready for testing.");

        //Lock the UI immediately until the SensorParser finds the port
        setDeviceConnected(false);
    }

    // === INITIALIZATION HELPER METHODS

    /**
     * Helper method to initialize and add dummy points for data buffers.
     */
    private void initializeData() {
        //Setup and initialize data lists
        xData = new ArrayList<>();
        cfm28Data = new ArrayList<>(); cfmOrificeData = new ArrayList<>(); massFlowData = new ArrayList<>();
        p1Data = new ArrayList<>(); p2Data = new ArrayList<>(); p3Data = new ArrayList<>();
        t1Data = new ArrayList<>(); t2Data = new ArrayList<>(); t3Data = new ArrayList<>();

        //Add dummy data only for startup
        xData.add(0.0);
        cfm28Data.add(0.0); cfmOrificeData.add(0.0); massFlowData.add(0.0);
        p1Data.add(0.0); p2Data.add(0.0); p3Data.add(0.0);
        t1Data.add(0.0); t2Data.add(0.0); t3Data.add(0.0);
        currentOrificeDiameter = 1.00;
    }

    /**
     * Helper method to initialize, stylize, and wrap all 3 graphs.
     */
    private void initializeCharts() {
        //Graph 1: Airflow
        flowChart = QuickChart.getChart("Airflow Calculations", "Time (s)", "CFM", "Flowrate at 28\" in H20", xData, cfm28Data);
        flowChart.addSeries("Flowrate at Orifice", xData, cfmOrificeData);
        flowChart.getStyler().setLegendVisible(true);
        flowChart.getStyler().setXAxisMin(0.0);
        flowChart.getStyler().setToolTipsEnabled(true);
        flowChart.getStyler().setZoomEnabled(true);

        //Graph 2: Pressures
        pressureChart = QuickChart.getChart("Pressure Sensors", "Time (s)", "Pressure (hPa)", "P1", xData, p1Data);
        pressureChart.addSeries("P2", xData, p2Data);
        pressureChart.addSeries("P3", xData, p3Data);
        pressureChart.getStyler().setLegendVisible(true);
        pressureChart.getStyler().setXAxisMin(0.0);
        pressureChart.getStyler().setToolTipsEnabled(true);
        pressureChart.getStyler().setZoomEnabled(true);

        //Graph 3: Temperatures
        temperatureChart = QuickChart.getChart("Temperature Sensors", "Time (s)", "Temp (°C)", "T1", xData, t1Data);
        temperatureChart.addSeries("T2", xData, t2Data);
        temperatureChart.addSeries("T3", xData, t3Data);
        temperatureChart.getStyler().setLegendVisible(true);
        temperatureChart.getStyler().setXAxisMin(0.0);
        temperatureChart.getStyler().setToolTipsEnabled(true);
        temperatureChart.getStyler().setZoomEnabled(true);

        //Wrap charts in panels
        flowPanel = new XChartPanel<>(flowChart);
        pressurePanel = new XChartPanel<>(pressureChart);
        temperaturePanel = new XChartPanel<>(temperatureChart);
    }

    /**
     * Helper method to set up GUI layout.
     */
    private void setupLayout() {
        //Set up center graph card panel (holds 3 graphs on top of each other)
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(flowPanel, "Airflow Calculations");
        cardPanel.add(pressurePanel, "Pressure Sensors");
        cardPanel.add(temperaturePanel, "Temperature Sensors");
        JPanel selectorHeader = createSelectorHeader();
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.add(selectorHeader, BorderLayout.NORTH);
        centerContainer.add(cardPanel, BorderLayout.CENTER);

        //Set up top controls and activity log area
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(createControlPanel(), BorderLayout.CENTER);
        topContainer.add(createLogPanel(), BorderLayout.EAST);

        //Add containers to frame
        this.add(topContainer, BorderLayout.NORTH);
        this.add(centerContainer, BorderLayout.CENTER);
    }

    /**
     * Helper method to create the graph container header.
     * @return A header with a run selector and a graph selector
     */
    private JPanel createSelectorHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER));
        header.setBackground(Color.lightGray);

        //Create run selector dropdown menu
        runSelector = new JComboBox<>();
        runSelector.addItem("No Runs Recorded");
        runSelector.setFont(new Font("Arial", Font.BOLD, 14));
        runSelector.addActionListener(_ -> updateDisplayRun());

        //Create graph selector dropdown menu
        graphSelector = new JComboBox<>(new String[]{"Airflow Calculations", "Pressure Sensors", "Temperature Sensors"});
        graphSelector.setFont(new Font("Arial", Font.BOLD, 14));
        graphSelector.addActionListener(_ -> cardLayout.show(cardPanel, (String) graphSelector.getSelectedItem()));

        //Finalize selection header creation
        header.add(new JLabel("Select Run:"));
        header.add(runSelector);
        header.add(Box.createHorizontalStrut(20));
        header.add(new JLabel("Select Graph View: "));
        header.add(graphSelector);
        return header;
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

        //Allow columns to grow to fill the screen
        c.weightx = 0.5;
        c.weighty = 0.5;

        //Calculated results (light orange boxes)
        cfm28Label = createStyledLabel("CFM at 28 in H20:", new Color(255, 200, 150));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 1; panel.add(cfm28Label, c);
        cfmOrificeLabel = createStyledLabel("CFM at Orifice:", new Color(255, 200, 150));
        c.gridx = 1; c.gridy = 0; c.gridwidth = 1; panel.add(cfmOrificeLabel, c);
        massFlowRateLabel = createStyledLabel("Mass Flow Rate:", new Color(255, 200, 150));
        c.gridx = 2; c.gridy = 0; c.gridwidth = 1; panel.add(massFlowRateLabel, c);

        //ROW 1: Inputs and Duration
        //Valve lift
        valveLiftInput = new  JTextField("0.5");
        valveLiftInput.setFont(new Font("Arial", Font.PLAIN, 14));
        c.gridx = 0; c.gridy = 1; c.gridwidth = 1; panel.add(createInputSubPanel("Valve Lift:", valveLiftInput), c);
        //Orifice diameter
        orificeInput = new JComboBox<>(new String[]{"1.00", "1.50", "2.125"});
        orificeInput.setFont(new Font("Arial", Font.PLAIN, 14));
        orificeInput.setEditable(false);
        orificeInput.addActionListener(_ -> {
            try {
                String selected = (String) orificeInput.getSelectedItem();
                assert selected != null;
                currentOrificeDiameter = Double.parseDouble(selected);
            }
            catch (Exception ex) { ex.printStackTrace(); }
        });
        c.gridx = 1; c.gridy = 1; panel.add(createInputSubPanel("Orifice Diameter (\"):", orificeInput), c);
        //Testing duration
        durationInput = new  JTextField("10");
        durationInput.setFont(new Font("Arial", Font.PLAIN, 14));
        c.gridx = 2; c.gridy = 1; panel.add(createInputSubPanel("Testing Duration (s):", durationInput), c);

        //ROW 2: Instructions and Comments
        //Instructions label
        JLabel instructionsLabel = new JLabel("<html><center>Instructions:<br>Enter initial values and then hit RUN.<br>Hit STOP to suspend LOGGING.<br>Hit EXPORT CSV to export pending runs.</center></html>");
        instructionsLabel.setOpaque(true);
        instructionsLabel.setBackground(new Color(220, 220, 240)); //Light purple
        instructionsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        instructionsLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        c.gridx = 0; c.gridy = 2; c.gridheight = 1; panel.add(instructionsLabel, c);

        //Comments area
        commentsArea = new JTextArea(5, 20);
        commentsArea.setBorder(BorderFactory.createTitledBorder("Comments About Previous Trial:"));
        commentsArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateLastRunComment(); }
            public void removeUpdate(DocumentEvent e) { updateLastRunComment(); }
            public void changedUpdate(DocumentEvent e) { updateLastRunComment(); }
        });
        c.gridx = 1; c.gridy = 2; c.gridwidth = 2; c.gridheight = 2; panel.add(new JScrollPane(commentsArea), c); //Spans 2 columns and rows

        //ROW 3: Test Status
        testStatusLabel = new JLabel("Status: STOPPED");
        testStatusLabel.setOpaque(true);
        testStatusLabel.setBackground(new Color(220, 220, 240));
        testStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        testStatusLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        testStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
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
        stopButton.addActionListener(_ -> stopLogging(true));
        c.gridx = 1; c.gridy = 4; c.gridwidth = 1; panel.add(stopButton, c);

        //Export to CSV button
        exportButton = new JButton("Export CSV");
        exportButton.setBackground(Color.YELLOW);
        exportButton.addActionListener(_ -> exportToCSV());
        c.gridx = 2; c.gridy = 4; c.gridwidth = 1; panel.add(exportButton, c);

        //RIGHT COLUMN: Sensor Status Lights
        c.gridx = 3; c.gridy = 0; c.gridheight = 5; c.gridwidth = 1; c.weightx = 0.5; panel.add(createSensorStatusPanel(), c);

        return panel;
    }

    /**
     * Helper method for repeated creation of input sub panels.
     * @param title The title of the input panel
     * @param component The type of input
     * @return The complete input panel
     */
    private JPanel createInputSubPanel(String title, JComponent component) {
        JPanel subPanel = new JPanel(new BorderLayout());
        subPanel.setBorder(new TitledBorder(title));
        subPanel.add(component);
        return subPanel;
    }

    /**
     * Helper method to create column of live sensor readings.
     * @return The complete panel of sensor status labels
     */
    private JPanel createSensorStatusPanel() {
        JPanel statusPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        sensorStatusLabels = new JLabel[6];
        String[] statusNames = {
                "Pressure Diff #1 (Orifice):", "Pressure Diff #2 (Vertical):", "Pressure Diff #3 (Bore):",
                "Temperature #1 (Orifice):", "Temperature #2 (Vertical):", "Temperature #3 (Vacuum):"
        };

        for (int i = 0; i < 6; i++) {
            sensorStatusLabels[i] = new JLabel(statusNames[i]);
            sensorStatusLabels[i].setOpaque(true);
            sensorStatusLabels[i].setBackground(new Color(200, 240, 200));
            sensorStatusLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
            sensorStatusLabels[i].setBorder(BorderFactory.createLineBorder(Color.GRAY));
            statusPanel.add(sensorStatusLabels[i]);
        }
        return statusPanel;
    }

    /**
     * Helper method for creating the complex layout of the activity log
     * @return The complete activity log layout
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10)); //Padding

        //Titled border
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Session Activity Log"));

        activityLog = new JTextArea();
        activityLog.setEditable(false);
        activityLog.setFont(new Font("Monospaced", Font.PLAIN, 12));

        //Auto scroll to bottom
        DefaultCaret caret = (DefaultCaret) activityLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(activityLog);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setPreferredSize(new Dimension(280, 250));

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // === LOGIC METHODS ===

    /**
     * Helper method used to record a message on the activity log at the current timestamp.
     * @param message The message to be recorded on the activity log
     */
    public void logMessage(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        activityLog.append("[" + timestamp + "] " + message + "\n");
    }

    /**
     * Controls the UI state based on whether the Arduino is connected.
     * Blocks the buttons if disconnected.
     * @param connected True if connected, false if not connected
     */
    public void setDeviceConnected(boolean connected) {
        //If connection is lost while logging, force a stop immediately to save data
        if (!connected && isLogging) {
            logMessage("WARNING! Device disconnected. Stopping run...");
            stopLogging(true); //Treat as a manual stop to save the partial data
        }

        //Toggle buttons
        runButton.setEnabled(connected);
        stopButton.setEnabled(false); //Always disabled until a run actually starts

        //Toggle inputs
        valveLiftInput.setEnabled(connected);
        orificeInput.setEnabled(connected);
        durationInput.setEnabled(connected);
        runSelector.setEnabled(connected);

        //Update status label
        if (connected) {
            testStatusLabel.setText("Status: DEVICE READY");
            testStatusLabel.setBackground(Color.GREEN);
            logMessage("Connection established. Controls enabled.");
        } else {
            testStatusLabel.setText("Status: WAITING FOR DEVICE...");
            testStatusLabel.setBackground(Color.ORANGE);
            logMessage("Waiting for Arduino connection...");
        }
    }

    /**
     * Helper method to clear all graph points.
     */
    private void resetDataLists() {
        xData.clear();
        cfm28Data.clear(); cfmOrificeData.clear(); massFlowData.clear();
        p1Data.clear(); p2Data.clear(); p3Data.clear();
        t1Data.clear(); t2Data.clear(); t3Data.clear();
    }

    /**
     * Switches graphs to selected run and refreshes data.
     */
    public void updateDisplayRun() {
        int index = runSelector.getSelectedIndex();
        String selectedItem =  (String) runSelector.getSelectedItem();

        //If index is -1 (cleared), do nothing
        if (index == -1 || selectedItem == null || selectedItem.equals("No Runs Recorded")) return;

        //SessionRuns is 0-indexed
        if (index < sessionRuns.size()) {
            RunSnapshot snap = sessionRuns.get(index);
            refreshChartsWithData(snap.time, snap.p1, snap.p2, snap.p3, snap.t1, snap.t2, snap.t3, snap.cfmIn28OfH20, snap.cfmAtOrifice);
        }
    }

    /**
     * Helper method to push specific data lists to the charts
     * @param x The list of timestamps
     * @param p1 The list of recorded P1 sensor readings
     * @param p2 The list of recorded P2 sensor readings
     * @param p3 The list of recorded P3 sensor readings
     * @param t1 The list of recorded T1 sensor readings
     * @param t2 The list of recorded T2 sensor readings
     * @param t3 The list of recorded T3 sensor readings
     * @param cfm28 The list of recorded CFM at 28 in H20 calculations
     * @param cfmO The list of recorded CFM at orifice calculations
     */
    public void refreshChartsWithData(List<Double> x,
                                      List<Double> p1, List<Double> p2, List<Double> p3,
                                      List<Double> t1, List<Double> t2, List<Double> t3,
                                      List<Double> cfm28, List<Double> cfmO) {
        temperatureChart.updateXYSeries("T1", x, t1, null);
        temperatureChart.updateXYSeries("T2", x, t2, null);
        temperatureChart.updateXYSeries("T3", x, t3, null);

        pressureChart.updateXYSeries("P1", x, p1, null);
        pressureChart.updateXYSeries("P2", x, p2, null);
        pressureChart.updateXYSeries("P3", x, p3, null);

        flowChart.updateXYSeries("Flowrate at 28\" in H20", x, cfm28, null);
        flowChart.updateXYSeries("Flowrate at Orifice", x, cfmO, null);

        flowPanel.repaint();
        pressurePanel.repaint();
        temperaturePanel.repaint();
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
        label.setFont(new Font("Arial", Font.BOLD, 14));
        label.setPreferredSize(new Dimension(220, 60));
        label.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        return label;
    }

    /**
     * Helper method to update the comment of the last saved run in memory when not currently logging.
     * This allows the user to edit the comment after the run finishes.
     */
    private void updateLastRunComment() {
        //Update the record in memory
        if (!isLogging && !sessionRuns.isEmpty()) sessionRuns.getLast().comment = commentsArea.getText().replace("\n", " ").replace(",", ";").trim();
    }

    private void bufferSensorData(String sensorID, double value) {
        switch (sensorID) {
            case "T1": currentT1 = value; break;
            case "T2": currentT2 = value; break;
            case "T3": currentT3 = value; break;
            case "P1": currentP1 = value; break;
            case "P2": currentP2 = value; break;
            case "P3": currentP3 = value; break;
        }
    }

    /**
     * Helper method to record all data points at the period in time.
     * @param elapsed The timestamp of recording
     */
    private void recordDataPoint(double elapsed, FlowResult flowData) {
        xData.add(elapsed);
        t1Data.add(currentT1); t2Data.add(currentT2); t3Data.add(currentT3);
        p1Data.add(currentP1); p2Data.add(currentP2); p3Data.add(currentP3);

        //Use previously calculated values
        cfm28Data.add(flowData.cfm28()); cfmOrificeData.add(flowData.cfmOrifice()); massFlowData.add(flowData.massFlow());
    }

    /**
     * Helper method used to quickly toggle on/off all button functionality for trial running.
     * @param enabled True if the buttons should be on, and false if the buttons should be off
     */
    private void toggleInputs(boolean enabled) {
        runButton.setEnabled(enabled);
        stopButton.setEnabled(!enabled);
        exportButton.setEnabled(enabled);
        valveLiftInput.setEnabled(enabled);
        orificeInput.setEnabled(enabled);
        durationInput.setEnabled(enabled);
        runSelector.setEnabled(enabled);
    }

    /**
     * Updates the individual sensor status lights with the current value.
     * @param sensorID The sensor the new reading came from
     * @param value The new sensor reading
     */
    private void updateStatusDisplay(String sensorID, double value) {
        int index = -1;
        String unit = "";
        String sensor = "";

        //Temperature sensor #3 threshold
        double tempWarning = 50.0; //Warning if over 50C

        switch (sensorID) {
            case "P1": sensor = "Pressure Diff #1 (Orifice)"; index = 0; unit = "hPa"; break;
            case "P2": sensor = "Pressure Diff #2 (Vertical)"; index = 1; unit = "hPa"; break;
            case "P3": sensor = "Pressure Diff #3 (Bore)"; index = 2; unit = "hPa"; break;
            case "T1": sensor = "Temperature #1 (Orifice)"; index = 3; unit = "°C"; break;
            case "T2": sensor = "Temperature #2 (Vertical)"; index = 4; unit = "°C"; break;
            case "T3": sensor = "Temperature #3 (Vacuum)"; index = 5; unit = "°C"; break;
        }

        if (index != -1) {
            sensorStatusLabels[index].setText(String.format("%s: %.1f %s", sensor, value, unit));

            //Safety check logic for temperature sensor 3
            if (sensorID.equals("T3") && value > tempWarning) {
                sensorStatusLabels[index].setBackground(Color.RED);
                sensorStatusLabels[index].setForeground(Color.WHITE);
            }
            else {
                sensorStatusLabels[index].setBackground(new Color(200, 240, 200)); //Reset to green
                sensorStatusLabels[index].setForeground(Color.BLACK);
            }
        }
    }

    /**
     * Helper method to calculate physics values, update UI labels, and return the results.
     * @return A package of calculated physics values to prevent recalculations
     */
    private FlowResult performRealTimeCalculations() {
        //Safety check
        if (currentP1 <= 0 || currentP2 <= 0 || currentOrificeDiameter <= 0) return new FlowResult(0.0, 0.0, 0.0);

        //Calculate fluid density (rho)
        double rho = calculateRho(currentP2, currentT1);

        //Calculate mass flow rate
        double massFlowrate = calculateMassFlowRate(currentP1, rho, currentOrificeDiameter);

        //Calculate actual volumetric flow (CFM)
        double cfmOrifice = calculateCFMatOrifice(massFlowrate, rho);

        //Calculate corrected flow (CFM @ 28" in H20)
        double cfm28 = calculateCFMat28inH20(cfmOrifice, currentP1);

        //Update result labels immediately
        cfm28Label.setText(String.format("Flowrate at 28\" in H20: %.2f CFM", cfm28));
        cfmOrificeLabel.setText(String.format("Flowrate at Orifice: %.2f CFM", cfmOrifice));
        massFlowRateLabel.setText(String.format("Mass Flowrate: %.4f kg/s", massFlowrate));

        //Return package of calculated values
        return new FlowResult(cfmOrifice, massFlowrate, cfm28);
    }

    /**
     * Method to set up UI and chart for logging.
     * Double checks if the inputted values are valid.
     */
    private void startLogging() {
        try {
            //Validate duration
            double seconds = Double.parseDouble(durationInput.getText());
            if (seconds <= 0) throw new NumberFormatException();

            //Validate valve lift
            double lift = Double.parseDouble(valveLiftInput.getText());
            if (lift < 0) throw new NumberFormatException();

            //Validate orifice diameter
            double orifice = Double.parseDouble((String) Objects.requireNonNull(orificeInput.getSelectedItem()));
            if (orifice <= 0) throw new NumberFormatException();

            //Setup logging variables
            this.targetDuration = seconds;
            this.currentValveLift = lift;
            this.currentOrificeDiameter = orifice;

            //Start test
            resetDataLists(); //Clear all data values
            startTime = System.currentTimeMillis();
            isLogging = true;

            //Update UI
            toggleInputs(false);
            testStatusLabel.setText("Status: RUNNING");
            testStatusLabel.setBackground(Color.GREEN);

            //Log action
            logMessage("Started Run #" + (sessionRuns.size() + 1) + " (Lift: " + lift + ", Orifice: " + orifice + ", Duration: " + seconds + "s)");
        }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please ensure your input values are valid numbers!", "ERROR", JOptionPane.ERROR_MESSAGE);
            logMessage("ERROR: Start failed due to invalid input.");
        }
    }

    /**
     * Method to reset UI upon completion of logging data.
     * Triggers graph generation upon logging completion.
     * Save current session data.
     * @param manualStop True if the user pressed the STOP button. False if timer finished
     */
    private void stopLogging(boolean manualStop) {
        if (!isLogging) return; //Prevent double saving

        //Record actual duration of run
        double actualDuration = (System.currentTimeMillis() - startTime) / 1000.0;

        //Update UI
        isLogging = false;
        toggleInputs(true);
        testStatusLabel.setText("Status: STOPPED. Data Saved");
        testStatusLabel.setBackground(new Color(220, 220, 240));

        //Generate graphs for the run that just finished
        refreshChartsWithData(xData, p1Data, p2Data, p3Data, t1Data, t2Data, t3Data, cfm28Data, cfmOrificeData);

        //Clean comment for CSV
        String safeComment = commentsArea.getText().replace("\n", " ").replace(",", ";").trim();

        //Create a snapshot of the current run and add to session history
        RunSnapshot run = new RunSnapshot(currentValveLift, currentOrificeDiameter, xData, p1Data, p2Data, p3Data, t1Data, t2Data, t3Data, cfm28Data, cfmOrificeData, massFlowData, safeComment);
        sessionRuns.add(run);

        //If this is the first run, remove the "No Runs Recorded" placeholder
        if (runSelector.getItemAt(0).equals("No Runs Recorded")) runSelector.removeAllItems();

        //Add the finished run to the run selector
        runSelector.addItem("Run #" + sessionRuns.size());

        //Select the current run automatically for the user to see
        runSelector.setSelectedIndex(runSelector.getItemCount() - 1);

        //Log action depending on if the run was interrupted or not
        if (manualStop) logMessage("Run #" + sessionRuns.size() + " STOPPED by user after " + String.format("%.1f", actualDuration) + "s.");
        else logMessage("Run #" + sessionRuns.size() + " COMPLETED (" + targetDuration + "s).");
        logMessage("Total Runs Pending Export: " + sessionRuns.size());

        //Update status to show how many runs are pending export
        JOptionPane.showMessageDialog(this, "Run #" + sessionRuns.size() + " recorded!\nAdjust values and press RUN for next trial,\nor press EXPORT to save all.");
    }

    /**
     * Method for exporting all recorded test runs to a CSV file.
     */
    private void exportToCSV() {
        if (sessionRuns.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No session recorded! Run a test first.", "ERROR", JOptionPane.ERROR_MESSAGE);
            logMessage("Export failed: No runs in memory.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Flow Bench Data");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            //Ensure .csv extension
            if (!file.getName().toLowerCase().endsWith(".csv")) file = new File(file.getParentFile(), file.getName() + ".csv");

            try (PrintWriter writer = new PrintWriter(file)) {
                //CSV header
                writer.println("Run ID,Valve Lift,Orifice Diameter,Time,P1 (hPa),P2 (hPa),P3 (hPa),T1 (C),T2 (C),T3 (C),Flowrate at 28\" in H20 (CFM),Flowrate at Orifice,Mass Flowrate (kg/s),Comments");

                //Loop through all saved runs
                for (int i = 0; i < sessionRuns.size(); i++) {
                    RunSnapshot run = sessionRuns.get(i);
                    int runID = i + 1;

                    //Loop through data points in this run
                    for (int j = 0; j < run.time.size(); j++) {
                        writer.printf("%d,%s,%s,%.3f,%.2f,%.2f,%.2f,%.1f,%.1f,%.1f,%.2f,%.2f,%.5f,%s%n",
                                runID,
                                run.valveLift,
                                run.orificeDiameter,
                                run.time.get(j),
                                run.p1.get(j), run.p2.get(j), run.p3.get(j),
                                run.t1.get(j), run.t2.get(j), run.t3.get(j),
                                run.cfmIn28OfH20.get(j), run.cfmAtOrifice.get(j), run.massFlowrate.get(j),
                                (j == 0) ? run.comment : ""
                        );
                    }
                }
                JOptionPane.showMessageDialog(this, "Export Successful!\nSession cleared.");
                logMessage("Exported " + sessionRuns.size() + " runs to: " + file.getName());
            }
            catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
            }
        }
    }

    /**
     * Sends sensor updates to GUI labels.
     * Performs calculations for CFM and Flow Rate labels.
     * Records all data values in data history links.
     * @param sensorID The ID of the sensor currently being read
     * @param value The value of the sensor currently being read
     */
    @Override
    public void update(String sensorID, double value) {
        //Always run UI updates on the Swing thread
        SwingUtilities.invokeLater(() -> {
            //Buffer the incoming value into temporary variables
           bufferSensorData(sensorID, value);
            //Update status boxes in real-time
            updateStatusDisplay(sensorID, value);
            //Update the calculated boxes in real-time
            FlowResult results = performRealTimeCalculations();

            //Use the last sensor as the trigger to add full set of data to the graph, otherwise continue logging
            if (isLogging && sensorID.equals("P3")) {
                //Get the current time for x-axis
                long now = System.currentTimeMillis();
                double elapsed = (now - startTime) / 1000.0;

                //Status sensor countdown logic
                testStatusLabel.setText(String.format("Remaining time: %.1f seconds", Math.max(0, targetDuration - elapsed)));

                //If the timer ran out without any interrupts, communicate that in the activity log
                if (elapsed >= targetDuration) stopLogging(false);
                else recordDataPoint(elapsed, results);
            }
        });
    }

    // === MATH HELPER METHODS ===

    /**
     * Helper method for calculating the beta ratio for the orifice plate.
     * @param dInches The current orifice diameter in inches
     * @return The dimensionless beta ratio
     */
    private double calculateBeta(double dInches) {
        return dInches / PIPE_INNER_DIAMETER;
    }

    /**
     * Helper method for calculating the air density based on the current absolute pressure and temperature.
     * @param pAbsHPa The current absolute pressure (P2) in hPa
     * @param tempC The current temperature (T1) in C
     * @return The current calculated air density in kg/m^3
     */
    private double calculateRho(double pAbsHPa, double tempC) {
        //Unit conversions
        double pAbsPa = pAbsHPa * 100;
        double tempK = tempC + CELSIUS_TO_KELVIN;

        // Avoid divide by 0
        if (tempK == 0) return 0;

        //Formula: rho = p_1 / (R * T_1)
        return pAbsPa / (SPECIFIC_GAS_CONSTANT * tempK);
    }

    /**
     * Helper method for calculating the current mass flow rate using the standard orifice equation.
     * @param deltaPHPa The current differential pressure (P1) in hPa
     * @param rho The air density in kg/m^3
     * @param dInches The current orifice diameter in inches
     * @return The current mass flow rate in kg/s
     */
    private double calculateMassFlowRate(double deltaPHPa, double rho, double dInches) {
        //Unit conversions
        double deltaPPa = deltaPHPa * 100.0;
        double dMeters = dInches * INCHES_TO_METERS;

        //Geometry
        double beta = calculateBeta(dInches);
        double area = (Math.PI / 4.0) * Math.pow(dMeters, 2.0);

        //Formula: m_dot = (Cd * E * A * sqrt(2 * rho * dP)) / sqrt(1 - beta^4)
        double numerator = DISCHARGE_COEFFICIENT * EXPANSIBILITY_FACTOR * area * Math.sqrt(2 * rho * deltaPPa);
        double denominator = Math.sqrt(1 - Math.pow(beta, 4));
        return numerator / denominator;
    }

    /**
     * Helper method for converting the current mass flow rate to the current volumetric flow (CFM) at the measured density.
     * @param massFlowKgPerS The current mass flow rate in kg/s
     * @param rho The air density in kg/m^3
     * @return The current volumetric flow in CFM
     */
    private double calculateCFMatOrifice(double massFlowKgPerS, double rho) {
        //Avoid divide by 0
        if (rho <= 0) return 0.0;

        //Unit conversions
        double volFlowM3perS = massFlowKgPerS / rho;
        return volFlowM3perS * KgPerS_TO_CFM;
    }

    /**
     * Helper method for correcting the actual CFM to a standard pressure drop of 28 inches of water.
     * @param cfmActual The current actual calculated CFM
     * @param deltaPHPa The current differential pressure (P1) in hPa
     * @return The corrected current flow in CFM at 28" in H20
     */
    private double calculateCFMat28inH20(double cfmActual, double deltaPHPa) {
        //Unit conversions
        double targetPressurePa = 28.0 * DIFFERENTIAL_PRESSURE_IN_H20_TO_Pa;
        double measuredPressurePa = deltaPHPa * 100.0;

        //Avoid divide by 0
        if (measuredPressurePa <= 0) return 0.0;

        //Formula: Q_28 = Q_actual * sqrt(TargetP / MeasuredP)
        return cfmActual * Math.sqrt(targetPressurePa / measuredPressurePa);
    }
}
