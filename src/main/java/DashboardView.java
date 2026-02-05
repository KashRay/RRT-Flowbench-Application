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
    public static double C_d = 0.61;
    public static double D = 4.0;
    public static double EPSILON = 1;
    public static double R = 287.1;
    public static double INCHES_TO_METERS = 0.0254;

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
    }

    // === INITIALIZATION HELPER METHODS

    /**
     * Helper method to initialize and add dummy points for data buffers.
     */
    private void initializeData() {
        //Setup and initialize data lists
        xData = new ArrayList<>();
        cfm28Data = new ArrayList<>(); cfmOrificeData = new ArrayList<>();
        p1Data = new ArrayList<>(); p2Data = new ArrayList<>(); p3Data = new ArrayList<>();
        t1Data = new ArrayList<>(); t2Data = new ArrayList<>(); t3Data = new ArrayList<>();

        //Add dummy data only for startup
        xData.add(0.0);
        cfm28Data.add(0.0); cfmOrificeData.add(0.0);
        p1Data.add(0.0); p2Data.add(0.0); p3Data.add(0.0);
        t1Data.add(0.0); t2Data.add(0.0); t3Data.add(0.0);
        currentOrificeDiameter = 1.00;
    }

    /**
     * Helper method to initialize, stylize, and wrap all 3 graphs.
     */
    private void initializeCharts() {
        //Graph 1: Airflow
        flowChart = QuickChart.getChart("Airflow Calculations", "Time (s)", "CFM", "CFM @ 28", xData, cfm28Data);
        flowChart.addSeries("CFM @ Orifice", xData, cfmOrificeData);
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
        topContainer.add(createControlPanel(), BorderLayout.WEST);
        topContainer.add(createLogPanel(), BorderLayout.CENTER);

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
        c.gridx = 0; c.gridy = 1; c.gridwidth = 1; panel.add(createInputSubPanel("Valve Lift:", valveLiftInput), c);
        //Orifice diameter
        orificeInput = new JComboBox<>(new String[]{"1.00", "1.50", "2.125"});
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
        c.gridx = 3; c.gridy = 0; c.gridheight = 5; c.gridwidth = 1; panel.add(createSensorStatusPanel(), c);

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
                "Pressure Diff Sensor 1 Status:", "Pressure Diff Sensor 2 Status:", "Pressure Diff Sensor 3 Status:",
                "Temperature Sensor 1 Status:", "Temperature Sensor 2 Status:", "Temperature Sensor 3 Status:"
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
        scrollPane.setPreferredSize(new Dimension(300, 250));

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    // === LOGIC METHODS ===

    /**
     * Helper method used to record a message on the activity log at the current timestamp.
     * @param message The message to be recorded on the activity log
     */
    private void logMessage(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        activityLog.append("[" + timestamp + "] " + message + "\n");
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
            refreshChartsWithData(snap.time, snap.p1, snap.p2, snap.p3, snap.t1, snap.t2, snap.t3, snap.flow, snap.flowOrifice);
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

        flowChart.updateXYSeries("CFM @ 28", x, cfm28, null);
        flowChart.updateXYSeries("CFM @ Orifice", x, cfmO, null);

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
        label.setPreferredSize(new Dimension(150, 40));
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
        cfm28Data.add(flowData.cfm28());
        cfmOrificeData.add(flowData.cfmOrifice());
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

        //Thresholds (can be adjusted through testing)
        double tempWarning = 65.0; //Warning if over 50C

        switch (sensorID) {
            case "P1": sensor = "Pressure Diff Sensor 1 Status"; index = 0; unit = "hPa"; break;
            case "P2": sensor = "Pressure Diff Sensor 2 Status"; index = 1; unit = "hPa"; break;
            case "P3": sensor = "Pressure Diff Sensor 3 Status"; index = 2; unit = "hPa"; break;
            case "T1": sensor = "Temperature Sensor 1 Status"; index = 3; unit = "°C"; break;
            case "T2": sensor = "Temperature Sensor 2 Status"; index = 4; unit = "°C"; break;
            case "T3": sensor = "Temperature Sensor 3 Status"; index = 5; unit = "°C"; break;
        }

        if (index != -1) {
            sensorStatusLabels[index].setText(String.format("%s: %.1f %s", sensor, value, unit));

            //Safety check logic (can be adjusted to include pressures)
            if (sensorID.startsWith("T") && value > tempWarning) {
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
     * Helper method to update calculated labels.
     */
    private FlowResult performRealTimeCalculations() {
        double rtCFMOrifice = 0.0, rtMassFlow = 0.0, rtCFM28 = 0.0;

        //Only calculate if valid sensor data is present
        if (currentP1 > 0 && currentT1 > 0) {
            rtCFMOrifice = calculateCFMatOrifice(currentP1, currentP2, currentOrificeDiameter, currentT1);
            rtMassFlow = calculateMassFlowRate(rtCFMOrifice, calculateRho(currentP2, currentT1));
            rtCFM28 = calculateCFMat28inH20(rtCFMOrifice, currentP1);
        }

        //Update results labels immediately
        cfm28Label.setText(String.format("CFM @ 28: %.2f", rtCFM28));
        cfmOrificeLabel.setText(String.format("CFM @ Orifice: %.2f", rtCFMOrifice));
        massFlowRateLabel.setText(String.format("Mass Flow: %.3f", rtMassFlow));

        return new FlowResult(rtCFMOrifice, rtMassFlow, rtCFM28);
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
        RunSnapshot run = new RunSnapshot(currentValveLift, currentOrificeDiameter, xData, p1Data, p2Data, p3Data, t1Data, t2Data, t3Data, cfm28Data, cfmOrificeData, safeComment);
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
                writer.println("Run_ID,Valve_Lift,Orifice_Dia,Time,P1(hPa),P2(hPa),P3(hPa),T1(C),T2(C),T3(C),CFM@28,CFM@Orifice,Comment");

                //Loop through all saved runs
                for (int i = 0; i < sessionRuns.size(); i++) {
                    RunSnapshot run = sessionRuns.get(i);
                    int runID = i + 1;

                    //Loop through data points in this run
                    for (int j = 0; j < run.time.size(); j++) {
                        writer.printf("%d,%s,%s,%.3f,%.2f,%.2f,%.2f,%.1f,%.1f,%.1f,%.2f,%.2f,%s%n",
                                runID,
                                run.valveLift,
                                run.orificeDiameter,
                                run.time.get(j),
                                run.p1.get(j), run.p2.get(j), run.p3.get(j),
                                run.t1.get(j), run.t2.get(j), run.t3.get(j),
                                run.flow.get(j), run.flowOrifice.get(j),
                                (j == 0) ? run.comment : ""
                        );
                    }
                }
                JOptionPane.showMessageDialog(this, "Export Successful!\nSession cleared.");
                logMessage("Exported " + sessionRuns.size() + " runs to: " + file.getName());

                //Clear session memory and reset run dropdown
                sessionRuns.clear();
                runSelector.removeAllItems();
                runSelector.addItem("No Runs Recorded");
                logMessage("Memory cleared. Ready for new session.");
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
     * Helper method for calculating the constant value of beta.
     * @param currentOrificeDiameter The currently selected orifice value in inches
     * @return The calculated beta value
     */
    private double calculateBeta(double currentOrificeDiameter) {
        return currentOrificeDiameter/D;
    }

    /**
     * Helper method for calculating the value of rho.
     * @param currentP2 The second pressure sensor reading
     * @param currentT1 The first temperature sensor reading
     * @return The current calculated value of rho
     */
    private double calculateRho(double currentP2, double currentT1) {
        double pPascal = currentP2 * 100;
        double tKelvin = currentT1 + 273.15;

        // Avoid divide by 0
        if (tKelvin == 0) return 0;

        return pPascal / (R * tKelvin);
    }

    /**
     * Helper method for calculating the current CFM at orifice.
     * @param currentP1 The current reading of the first pressure sensor
     * @param currentP2 The current reading of the second pressure sensor
     * @param currentOrificeDiameter The currently selected orifice value in inches
     * @param currentT1 The current reading of the first temperature sensor
     * @return The current calculated value for the CFM at orifice
     */
    private double calculateCFMatOrifice(double currentP1, double currentP2, double currentOrificeDiameter, double currentT1) {
        double p1Pascal = currentP1 * 100;
        double rho = calculateRho(currentP2, currentT1);

        if (rho <= 0 || p1Pascal <= 0) return 0.0;

        double area = (Math.PI / 4.0) * Math.pow(currentOrificeDiameter * INCHES_TO_METERS, 2);
        double beta = calculateBeta(currentOrificeDiameter);
        double flowCoefficient = C_d / Math.sqrt(1 - Math.pow(beta, 4));
        double massFlow = flowCoefficient * EPSILON * area * Math.sqrt(2 * rho * p1Pascal);
        double volFlowCMS = massFlow / rho;

        return volFlowCMS * 2118.88;
    }

    /**
     * Helper method for calculating the current mass flow rate.
     * @param currentCFMatOrifice The current calculated CFM at orifice
     * @param currentRho The current calculated rho value
     * @return The current calculated value for the mass flow rate
     */
    private double calculateMassFlowRate(double currentCFMatOrifice, double currentRho) {
        double cms = currentCFMatOrifice / 2118.88;

        return cms * currentRho;
    }

    /**
     * Helper method for calculating the current CFM at 28 in H20.
     * @param currentCFMatOrifice The current calculated CFM at orifice
     * @param currentP1 The current reading of the first pressure sensor
     * @return The current calculated value for the CFM at 28 in H20
     */
    private double calculateCFMat28inH20(double currentCFMatOrifice, double currentP1) {
        if (currentP1 <= 0) return 0.0;

        return  currentCFMatOrifice * Math.sqrt(28 * INCHES_TO_METERS/currentP1);
    }
}
