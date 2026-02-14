import org.knowm.xchart.QuickChart;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
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
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private XYChart comparisonChart;
    private XChartPanel<XYChart> flowPanel;
    private XChartPanel<XYChart> pressurePanel;
    private XChartPanel<XYChart> temperaturePanel;
    private XChartPanel<XYChart> comparisonPanel;

    //View Switching Components
    private JComboBox<String> seriesSelector;
    private JComboBox<String> runSelector;
    private JComboBox<String> graphSelector;
    private JPanel cardPanel;
    private CardLayout cardLayout;

    //DATA STORAGE AND HISTORY
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
    //Hold the runs for the current series
    private final List<RunSnapshot> sessionRuns;
    //Hold previously completed series
    private final List<TestSeries> archivedSeries;

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
    private JButton nextTestButton;
    private JMenuItem importMenuItem;
    private JMenuItem exportMenuItem;
    private JMenuItem clearMenuItem;
    private JMenuItem runMenuItem;
    private JMenuItem stopMenuItem;
    private JMenuItem nextTestMenuItem;

    //Logic State
    private boolean isLogging = false;
    private boolean isUpdatingUI = false;
    private RunSnapshot currentlyViewedRun = null;
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

        //Create run and test history
        sessionRuns = new ArrayList<>(); archivedSeries = new ArrayList<>();

        //Create GUI with helper methods
        initializeData();
        initializeCharts();
        this.setJMenuBar(createMenuBar());
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
        configureChartStyle(flowChart);

        //Graph 2: Pressures
        pressureChart = QuickChart.getChart("Pressure Sensors", "Time (s)", "Pressure (hPa)", "P1", xData, p1Data);
        pressureChart.addSeries("P2", xData, p2Data);
        pressureChart.addSeries("P3", xData, p3Data);
        configureChartStyle(pressureChart);

        //Graph 3: Temperatures
        temperatureChart = QuickChart.getChart("Temperature Sensors", "Time (s)", "Temp (°C)", "T1", xData, t1Data);
        temperatureChart.addSeries("T2", xData, t2Data);
        temperatureChart.addSeries("T3", xData, t3Data);
        configureChartStyle(temperatureChart);

        //Graph 4: Flow Comparison Chart
        comparisonChart = new XYChart(800, 600);
        comparisonChart.setTitle("Flow Comparison");
        comparisonChart.setXAxisTitle("Valve Lift (Inches)");
        comparisonChart.setYAxisTitle("CFM");
        configureChartStyle(comparisonChart);
        comparisonChart.addSeries("Pending...", new double[]{0}, new double[]{0});

        //Wrap charts in panels
        flowPanel = new XChartPanel<>(flowChart);
        pressurePanel = new XChartPanel<>(pressureChart);
        temperaturePanel = new XChartPanel<>(temperatureChart);
        comparisonPanel = new XChartPanel<>(comparisonChart);
    }

    /**
     * Helper method to apply consistent, high-visibility styling to all charts.
     * @param chart The chart to be styled
     */
    private void configureChartStyle(XYChart chart) {
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setToolTipsEnabled(true);
        chart.getStyler().setZoomEnabled(true);
        chart.getStyler().setMarkerSize(10);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
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
        cardPanel.add(comparisonPanel, "Flow Comparison");
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

        //Create series selector dropdown menu
        seriesSelector = new JComboBox<>();
        seriesSelector.addItem("Current Unsaved Session");
        seriesSelector.setFont(new Font("Arial", Font.BOLD, 14));
        seriesSelector.addActionListener(_ -> onSeriesSelected());

        //Create run selector dropdown menu
        runSelector = new JComboBox<>();
        runSelector.addItem("No Runs Recorded");
        runSelector.setFont(new Font("Arial", Font.BOLD, 14));
        runSelector.addActionListener(_ -> onRunSelected());

        //Create graph selector dropdown menu
        graphSelector = new JComboBox<>(new String[]{"Airflow Calculations", "Pressure Sensors", "Temperature Sensors", "Flow Comparison"});
        graphSelector.setFont(new Font("Arial", Font.BOLD, 14));
        graphSelector.addActionListener(_ -> cardLayout.show(cardPanel, (String) graphSelector.getSelectedItem()));

        //Finalize selection header creation
        header.add(new JLabel("Select Test Series:"));
        header.add(seriesSelector);
        header.add(Box.createHorizontalStrut(20));
        header.add(new JLabel("Select Run:"));
        header.add(runSelector);
        header.add(Box.createHorizontalStrut(20));
        header.add(new JLabel("Select Graph View:"));
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

        //ROW 2: Comments
        // Comment area
        commentsArea = new JTextArea(5, 20);
        commentsArea.setBorder(BorderFactory.createTitledBorder("Comments About Displayed Run:"));
        c.gridx = 1; c.gridy = 2; c.gridwidth = 2; c.gridheight = 2; panel.add(new JScrollPane(commentsArea), c); //Spans 2 columns and rows

        //ROW 3: Test Status
        testStatusLabel = new JLabel("Status: STOPPED");
        testStatusLabel.setOpaque(true);
        testStatusLabel.setBackground(new Color(220, 220, 240));
        testStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        testStatusLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        testStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.gridheight = 1; panel.add(testStatusLabel, c);

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
        //Next test/commit series button
        nextTestButton = new JButton("Next Test (Commit New Series)");
        nextTestButton.setBackground(new Color(150, 200, 255)); //Light blue
        nextTestButton.addActionListener(_ -> commitSeries());
        c.gridx = 0; c.gridy = 3; c.gridwidth = 1; panel.add(nextTestButton, c);

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

    /**
     * Helper method for creating the entire menu bar and all submenus.
     * @return The complete menu layout and dropdown menus
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        //FILE MENU
        //Setup menu header
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        //Add "export CSV" button
        exportMenuItem = new JMenuItem("Export All Data (CSV)");
        exportMenuItem.addActionListener(_ -> exportToCSV());
        fileMenu.add(exportMenuItem);
        //Add "import CSV" button
        importMenuItem = new JMenuItem("Import Data (CSV)");
        importMenuItem.addActionListener(_ -> importFromCSV());
        fileMenu.add(importMenuItem);
        //Add "clear all data" button
        clearMenuItem = new JMenuItem("Clear All Data");
        clearMenuItem.addActionListener(_ -> clearAllData());
        fileMenu.add(clearMenuItem);

        //RUN MENU
        //Setup menu header
        JMenu runMenu = new JMenu("Controls");
        menuBar.add(runMenu);
        //Add "start run" button
        runMenuItem = new JMenuItem("StartRun");
        runMenuItem.addActionListener(_ -> startLogging());
        runMenu.add(runMenuItem);
        //Add "stop run" button
        stopMenuItem = new JMenuItem("StopRun");
        stopMenuItem.addActionListener(_ -> stopLogging(true));
        runMenu.add(stopMenuItem);
        //Add "next test" button
        nextTestMenuItem = new JMenuItem("Next Test Series");
        nextTestMenuItem.addActionListener(_ -> commitSeries());
        runMenu.add(nextTestMenuItem);

        //HELP MENU
        //Setup menu header
        JMenu helpMenu = new JMenu("Help");
        menuBar.add(helpMenu);
        //Add "setup instructions" button
        JMenuItem setupItem = new JMenuItem("Setup Instructions");
        setupItem.addActionListener(_ -> showSetupInstructions());
        helpMenu.add(setupItem);
        //Add "logging instructions" button
        JMenuItem loggingHelpItem = new JMenuItem("Logging Instructions");
        loggingHelpItem.addActionListener(_ -> showLoggingInstructions());
        helpMenu.add(loggingHelpItem);
        //Add "export instructions" menu
        JMenuItem exportHelpItem = new JMenuItem("Exporting Instructions");
        exportHelpItem.addActionListener(_ -> showExportingInstructions());
        helpMenu.add(exportHelpItem);
        //Add "next test and flow comparison instructions" menu
        JMenuItem nextTestHelpItem  = new JMenuItem("Next Test and Flow Comparison Instructions");
        nextTestHelpItem.addActionListener(_ -> showNextTextInstructions());
        helpMenu.add(nextTestHelpItem);

        return menuBar;
    }

    // === INSTRUCTIONS ===

    /**
     * Helper method for displaying an information box detailing how to start using the application.
     */
    private void showSetupInstructions() {
        String msg = """
                HOW TO SETUP FSAE FLOWBENCH TESTER:
                To setup the software, make sure that the Arduino is connected to the device via a
                serial cable. You can observe the status of the connection with the status label, or
                the activity log. All control buttons will be blocked until a USB connection is
                established and data values are being read. At any point, if the USB is disconnected,
                the system will automatically stop logging results, and require the user to reconnect
                the Arduino before continuing. If any issues with setup persist, you can contact
                "rayyankashif@cmail.carleton.ca".
                """;

        JOptionPane.showMessageDialog(this, msg, "Setup Instructions", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Helper method for displaying an information box detailing how logging controls and labels work.
     */
    private void showLoggingInstructions() {
        String msg = """
                LOGGING:
        """;
        JOptionPane.showMessageDialog(this, msg, "Logging Instructions", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Helper method for displaying an information box detailing how exporting logged data and saving graphs works.
     */
    private void showExportingInstructions() {
        String msg = """
                EXPORTING INSTRUCTOR:
        """;
        JOptionPane.showMessageDialog(this, msg, "Exporting Instructions", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Helper method for displaying an information box detailing how commiting and iterating to the next test works.
     */
    private void showNextTextInstructions() {
        String msg = """
                NEXT TEXT:
        """;
        JOptionPane.showMessageDialog(this, msg, "Next Test and Flow Comparison Instructions", JOptionPane.INFORMATION_MESSAGE);
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
        nextTestButton.setEnabled(connected);

        //Toggle inputs
        valveLiftInput.setEnabled(connected);
        orificeInput.setEnabled(connected);
        durationInput.setEnabled(connected);
        seriesSelector.setEnabled(connected);
        runSelector.setEnabled(connected);

        //Toggle menus
        runMenuItem.setEnabled(connected);
        stopMenuItem.setEnabled(false);
        nextTestMenuItem.setEnabled(connected);

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
     * Helper method to clear all graph points.
     */
    private void resetDataLists() {
        xData.clear();
        cfm28Data.clear(); cfmOrificeData.clear(); massFlowData.clear();
        p1Data.clear(); p2Data.clear(); p3Data.clear();
        t1Data.clear(); t2Data.clear(); t3Data.clear();
    }

    /**
     * Method to pull the comment from the text box and save it to the run currently displayed.
     */
    private void saveCurrentComment() {
        if (currentlyViewedRun != null) {
            String text = commentsArea.getText().replace("\n", " ").replace(",", ";").trim();
            currentlyViewedRun.setComment(text);
        }
    }

    /**
     * Method for selecting a series with the dropdown menu.
     * Updates the UI and run selector based on the currently selected run.
     */
    private void onSeriesSelected() {
        if (isUpdatingUI) return;
        saveCurrentComment(); //Save before switching

        isUpdatingUI = true;
        int seriesIndex = seriesSelector.getSelectedIndex();
        List<RunSnapshot> runsToDisplay;

        //Current series is 0, anything else is an archived series
        if (seriesIndex <= 0) runsToDisplay = sessionRuns;
        else runsToDisplay = archivedSeries.get(seriesIndex - 1).runs();

        //Update UI based on selected series
        runSelector.removeAllItems();
        if (runsToDisplay.isEmpty()) {
            runSelector.addItem("No Runs Recorded");
            currentlyViewedRun = null;
            commentsArea.setText("");
            showEmptyCharts();
        }
        else {
            for (int i = 0; i < runsToDisplay.size(); i++) runSelector.addItem("Run #" + (i + 1));

            //By default, select the last run in that series
            int lastIndex = runsToDisplay.size() - 1;
            runSelector.setSelectedIndex(lastIndex);
            currentlyViewedRun = runsToDisplay.get(lastIndex);

            //Populate the comment box with the saved comment
            commentsArea.setText(currentlyViewedRun.getComment() != null ? currentlyViewedRun.getComment() : "");
            refreshChartsForRun(currentlyViewedRun);
        }
        isUpdatingUI = false;
    }

    /**
     * Method for selecting a run with the dropdown menu.
     * Updates the UI based on the currently selected run.
     */
    private void onRunSelected() {
        if (isUpdatingUI) return;
        saveCurrentComment(); //Save before switching

        int seriesIndex = seriesSelector.getSelectedIndex();
        int runIndex = runSelector.getSelectedIndex();

        //If there are no recorded runs, do nothing
        if (runIndex == -1 || runSelector.getItemAt(0).equals("No Runs Recorded")) {
            currentlyViewedRun = null;
            return;
        }

        List<RunSnapshot> runsToDisplay = (seriesIndex <= 0) ? sessionRuns : archivedSeries.get(seriesIndex - 1).runs();

        if (runIndex < runsToDisplay.size()) {
            currentlyViewedRun = runsToDisplay.get(runIndex);

            //Update the text box without triggering any potential loops
            isUpdatingUI = true;
            commentsArea.setText(currentlyViewedRun.getComment() != null ? currentlyViewedRun.getComment() : "");
            isUpdatingUI = false;

            refreshChartsForRun(currentlyViewedRun);
        }
    }

    /**
     * A helper method to deconstruct a run into components for charting.
     * @param run The run to be deconstructed and displayed
     */
    private void refreshChartsForRun(RunSnapshot run) {
        refreshChartsWithData(run.getTime(), run.getP1(), run.getP2(), run.getP3(),
                run.getT1(), run.getT2(), run.getT3(),
                run.getCFMIn28OfH2O(), run.getCFMAtOrifice());
    }

    /**
     * Helper method to push dummy 0 values to safely clear the charts.
     */
    private void showEmptyCharts() {
        List<Double> zero = java.util.List.of(0.0);
        refreshChartsWithData(zero, zero, zero, zero, zero, zero, zero, zero, zero);
    }

    /**
     * Method to push specific data lists to the charts
     * @param x The list of timestamps
     * @param p1 The list of recorded P1 sensor readings
     * @param p2 The list of recorded P2 sensor readings
     * @param p3 The list of recorded P3 sensor readings
     * @param t1 The list of recorded T1 sensor readings
     * @param t2 The list of recorded T2 sensor readings
     * @param t3 The list of recorded T3 sensor readings
     * @param flowrateIn28OfH20 The list of recorded CFM at 28 in H20 calculations
     * @param flowrateAtOrifice The list of recorded CFM at orifice calculations
     */
    public void refreshChartsWithData(List<Double> x,
                                      List<Double> p1, List<Double> p2, List<Double> p3,
                                      List<Double> t1, List<Double> t2, List<Double> t3,
                                      List<Double> flowrateIn28OfH20, List<Double> flowrateAtOrifice) {
        temperatureChart.updateXYSeries("T1", x, t1, null);
        temperatureChart.updateXYSeries("T2", x, t2, null);
        temperatureChart.updateXYSeries("T3", x, t3, null);

        pressureChart.updateXYSeries("P1", x, p1, null);
        pressureChart.updateXYSeries("P2", x, p2, null);
        pressureChart.updateXYSeries("P3", x, p3, null);

        flowChart.updateXYSeries("Flowrate at 28\" in H20", x, flowrateIn28OfH20, null);
        flowChart.updateXYSeries("Flowrate at Orifice", x, flowrateAtOrifice, null);

        flowPanel.repaint();
        pressurePanel.repaint();
        temperaturePanel.repaint();
    }

    /**
     * Helper method for storing currently read data for logging.
     * @param sensorID The ID of the sensor currently being read
     * @param value The value the sensor is currently reading
     */
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
     * Method to iterate to the next test.
     * Takes all current runs and bundles them into a collection.
     * Clears the screen for the next component.
     * Updates the flow comparison graph with the newly created test.
     */
    private void commitSeries () {
        if (sessionRuns.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No runs to save! Perform tests first.", "ERROR!", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //Save comment before archiving run
        saveCurrentComment();

        String name = JOptionPane.showInputDialog(this, "Enter a name for this test (e.g., 'Engine 1'): ");
        if (name == null || name.trim().isEmpty()) return;

        //Save runs in new test series
        TestSeries series = new TestSeries(name, sessionRuns);
        archivedSeries.add(series);

        //Update the flow comparison graph
        updateComparisonGraph();

        //Clear data and UI for the next series
        sessionRuns.clear();
        isUpdatingUI = true;
        seriesSelector.addItem(name);
        seriesSelector.setSelectedIndex(0);
        runSelector.removeAllItems();
        runSelector.addItem("No Runs Recorded");
        isUpdatingUI = false;
        currentlyViewedRun = null;
        commentsArea.setText("");
        showEmptyCharts();

        //Inform user
        logMessage("Series '" + name + "' saved. Ready for next test.");
        JOptionPane.showMessageDialog(this, "Series '" + name + "' saved. You can now start testing the next component.", "SERIES SAVED!", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Method to update the final flow comparison chart based on all finished tests.
     */
    private void updateComparisonGraph () {
        //Clear old series
        comparisonChart.getSeriesMap().clear();

        for (TestSeries series : archivedSeries) {
            List<Double> xLifts = new ArrayList<>();
            List<Double> yFlows = new ArrayList<>();

            for (RunSnapshot run : series.runs()) {
                xLifts.add(run.getValveLift());
                yFlows.add(run.getAverageFlowrateIn28OfH2O());
            }

            //Sort by lifts (x-axis) to ensure line connects correctly
            if (!xLifts.isEmpty()) {
                XYSeries xySeries = comparisonChart.addSeries(series.name(), xLifts, yFlows);
                xySeries.setMarker(SeriesMarkers.CIRCLE);
            }
        }
        comparisonPanel.repaint();
    }

    /**
     * Method to wipe all data in the system currently.
     */
    private void clearAllData() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure? This will wipe ALL runs and series.", "CONFIRM CLEAR", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            sessionRuns.clear();
            archivedSeries.clear();

            isUpdatingUI = true;
            seriesSelector.removeAllItems();
            seriesSelector.addItem("Current Unsaved Session");
            runSelector.removeAllItems();
            runSelector.addItem("No Runs Recorded");
            isUpdatingUI = false;


            comparisonChart.getSeriesMap().clear();
            comparisonChart.addSeries("Pending...", new double[]{0}, new double[]{0});
            comparisonPanel.repaint();

            currentlyViewedRun = null;
            commentsArea.setText("");
            showEmptyCharts();
            logMessage("All data cleared!");
        }
    }

    /**
     * Helper method used to quickly toggle on/off all button functionality for trial running.
     * @param enabled True if the buttons should be on, and false if the buttons should be off
     */
    private void toggleInputs(boolean enabled) {
        //Toggle buttons
        runButton.setEnabled(enabled);
        stopButton.setEnabled(!enabled);
        exportButton.setEnabled(enabled);
        nextTestMenuItem.setEnabled(enabled);

        //Toggle inputs
        valveLiftInput.setEnabled(enabled);
        orificeInput.setEnabled(enabled);
        durationInput.setEnabled(enabled);
        seriesSelector.setEnabled(enabled);
        runSelector.setEnabled(enabled);

        //Toggle menu buttons
        runMenuItem.setEnabled(enabled);
        stopMenuItem.setEnabled(!enabled);
        nextTestMenuItem.setEnabled(enabled);
        exportMenuItem.setEnabled(enabled);
        importMenuItem.setEnabled(enabled);
        clearMenuItem.setEnabled(enabled);
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
        //Save the comment from whatever run is being looked at currently
        saveCurrentComment();

        //Force dropdown back to the current session if they were looking at old data
        if (seriesSelector.getSelectedIndex() != 0) {
            isUpdatingUI = true;
            seriesSelector.setSelectedIndex(0);
            runSelector.removeAllItems();
            if (sessionRuns.isEmpty()) runSelector.addItem("No Runs Recorded");
            else {
                for (int i = 0; i < sessionRuns.size(); i++) runSelector.addItem("Run #" + i + 1);
                runSelector.setSelectedIndex(sessionRuns.size() - 1);
            }
            isUpdatingUI = false;
        }

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
            currentlyViewedRun = null; //Detach the comment box
            commentsArea.setText("");
            testStatusLabel.setText("Status: RUNNING");
            testStatusLabel.setBackground(Color.GREEN);

            //Log action
            logMessage("Started Run #" + (sessionRuns.size() + 1) + " (Lift: " + lift + ", Orifice: " + orifice + ", Duration: " + seconds + "s)");
        }
        catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please ensure your input values are valid numbers!", "ERROR!", JOptionPane.ERROR_MESSAGE);
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

        //Update UI
        isUpdatingUI = true;
        if (runSelector.getItemAt(0).equals("No Runs Recorded")) runSelector.removeAllItems();
        runSelector.addItem("Run #" + sessionRuns.size());
        runSelector.setSelectedIndex(runSelector.getItemCount() - 1);
        currentlyViewedRun = run;
        isUpdatingUI = false;

        //Log action depending on if the run was interrupted or not
        if (manualStop) logMessage("Run #" + sessionRuns.size() + " STOPPED by user after " + String.format("%.1f", actualDuration) + "s.");
        else logMessage("Run #" + sessionRuns.size() + " COMPLETED (" + targetDuration + "s).");

        //Update status to show how many runs are pending export
        JOptionPane.showMessageDialog(this, "Run #" + sessionRuns.size() + " recorded!\nAdjust values and press RUN for next trial,\nor press EXPORT to save all.");
    }

    /**
     * Method for exporting all recorded tests and runs to a CSV file.
     */
    private void exportToCSV() {
        if (sessionRuns.isEmpty()  && archivedSeries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No session recorded! Run a test first.", "ERROR!", JOptionPane.ERROR_MESSAGE);
            return;
        }

        //Save the comment from the currently displayed run
        saveCurrentComment();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Flow Bench Data");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            //Ensure .csv extension
            if (!file.getName().toLowerCase().endsWith(".csv")) file = new File(file.getParentFile(), file.getName() + ".csv");

            try (PrintWriter writer = new PrintWriter(file)) {
                //CSV header
                writer.println("Series Name,Run ID,Valve Lift,Orifice Diameter,Time,P1 (hPa),P2 (hPa),P3 (hPa),T1 (C),T2 (C),T3 (C),Flowrate at 28\" in H20 (CFM),Flowrate at Orifice (CFM),Mass Flowrate (kg/s),Comments");

                //Export archived series (saved)
                for (TestSeries series : archivedSeries) {
                    try {
                        writeRunsToCSV(writer, series.name(), series.runs());
                    }
                    catch (Exception e) {
                        logMessage("Error while exporting " + series.name() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                //Export current session (unsaved)
                if (!sessionRuns.isEmpty()) {
                    try {
                        writeRunsToCSV(writer, "Current_Unsaved_Session", sessionRuns);
                    }
                    catch (Exception e) {
                        logMessage("Error while exporting " + sessionRuns.size() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                //Ensure data is written
                writer.flush();

                //Inform user
                JOptionPane.showMessageDialog(this, "Export Successful!\nSession cleared.");
                logMessage("Exported " + sessionRuns.size() + " runs to: " + file.getName());
            }
            catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
            }
        }
    }

    /**
     * Method to document runs from a specific series.
     * @param writer The initialized file writer object
     * @param seriesName The name of the test series the runs fall under
     * @param runs The list of runs under the specific test series
     */
    private void writeRunsToCSV(PrintWriter writer, String seriesName, List<RunSnapshot> runs) {
        //Loop through all saved runs
        for (int i = 0; i < runs.size(); i++) {
            RunSnapshot run = runs.get(i);
            int runID = i + 1;

            //Check if values are null
            if (run.getTime() == null) {
                System.err.println("Skipping corrupt run in " + seriesName);
                continue;
            }

            //Loop through data points in this run
            for (int j = 0; j < run.getTime().size(); j++) {
                writer.printf("%s,%d,%s,%s,%.3f,%.2f,%.2f,%.2f,%.1f,%.1f,%.1f,%.2f,%.2f,%.5f,%s%n",
                        seriesName,
                        runID,
                        run.getValveLift(),
                        run.getOrificeDiameter(),
                        run.getTime().get(j),
                        run.getP1().get(j), run.getP2().get(j), run.getP3().get(j),
                        run.getT1().get(j), run.getT2().get(j), run.getT3().get(j),
                        run.getCFMIn28OfH2O().get(j), run.getCFMAtOrifice().get(j), run.getMassFlowrate().get(j),
                        (j == 0) ? run.getComment() : ""
                );
            }
            //Flush after every run to prevent buffer loss
            writer.flush();
        }
    }

    /**
     * Method for importing a previously saved CSV file.
     * Rebuilds RunSnapshots and TestSeries, and refreshes UI.
     */
    private void importFromCSV() {
        //Warn user about overwriting data
        if (!sessionRuns.isEmpty() || !archivedSeries.isEmpty()) {
            int confirm = JOptionPane.showConfirmDialog(this, "Importing data will overwrite your current session!\n Are you sure you want to proceed?", "Confirm Import", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Flow Bench Data");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            //Read the selected file
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line = br.readLine();
                //Check if the header line follows the correct format
                if (line == null || !line.contains("Series Name")) {
                    JOptionPane.showMessageDialog(this, "Invalid CSV format! Header mismatch.", "ERROR!", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                //Create a data structure (Series Name -> (Run ID -> RunBuilder)
                Map<String, Map<Integer, RunBuilder>> parsedData = new LinkedHashMap<>();

                while ((line = br.readLine()) != null) {
                    //Split with -1 to keep trailing empty columns (like empty comments)
                    String[] parts = line.split(",", -1);
                    if (parts.length < 14) continue; //Skip corrupted rows

                    try {
                        //Parse all data
                        String seriesName = parts[0];
                        int runID = Integer.parseInt(parts[1]);
                        double valveLift = Double.parseDouble(parts[2]), orificeDiameter = Double.parseDouble(parts[3]);
                        double time = Double.parseDouble(parts[4]);
                        double p1 = Double.parseDouble(parts[5]), p2 = Double.parseDouble(parts[6]), p3 = Double.parseDouble(parts[7]);
                        double t1 = Double.parseDouble(parts[8]), t2 = Double.parseDouble(parts[9]), t3 = Double.parseDouble(parts[10]);
                        double flowrateIn28OfH2O = Double.parseDouble(parts[11]), flowrateAtOrifice = Double.parseDouble(parts[12]), massFlowrate = Double.parseDouble(parts[13]);
                        String comment = parts.length > 14 ? parts[14] : "";

                        //Find or create the series mapping
                        parsedData.putIfAbsent(seriesName, new LinkedHashMap<>());
                        Map<Integer, RunBuilder> seriesRuns = parsedData.get(seriesName);

                        //Find or create the run mapping
                        seriesRuns.putIfAbsent(runID, new RunBuilder(valveLift, orificeDiameter));
                        RunBuilder runBuilder = seriesRuns.get(runID);

                        //Feed the data point into the builder
                        runBuilder.addDataPoint(time, p1, p2, p3, t1, t2, t3, flowrateIn28OfH2O, flowrateAtOrifice, massFlowrate);
                        if (!comment.isEmpty() && runBuilder.comment.isEmpty()) runBuilder.comment = comment; //Usually only the first one
                    } catch (NumberFormatException e) {
                        System.err.println("Skipping malformed row: " + line);
                    }
                }

                //Clear all unsaved data
                sessionRuns.clear();
                archivedSeries.clear();

                //Integrate imported data
                for (Map.Entry<String, Map<Integer, RunBuilder>> seriesEntry : parsedData.entrySet()) {
                    String seriesName = seriesEntry.getKey();
                    List<RunSnapshot> rebuiltRuns = new ArrayList<>();

                    //Build all runs for this series
                    for (RunBuilder runBuilder : seriesEntry.getValue().values()) rebuiltRuns.add(runBuilder.build());

                    //Sort into the correct memory locations
                    if (seriesName.equals("Current_Unsaved_Session")) sessionRuns.addAll(rebuiltRuns);
                    else archivedSeries.add(new TestSeries(seriesName, rebuiltRuns));
                }

                //Refresh UI
                isUpdatingUI = true;
                seriesSelector.removeAllItems();
                seriesSelector.addItem("Current Unsaved Session");
                for (TestSeries series : archivedSeries) seriesSelector.addItem(series.name());

                //Switch view to imported data safely
                if (!sessionRuns.isEmpty()) seriesSelector.setSelectedIndex(0);
                else if (!archivedSeries.isEmpty()) seriesSelector.setSelectedIndex(archivedSeries.size());
                isUpdatingUI = false;

                //Trigger refresh
                updateComparisonGraph();
                onSeriesSelected();

                //Inform user
                JOptionPane.showMessageDialog(this, "Import Successful!");
                logMessage("Imported data from: " + file.getName());
            }
            catch(Exception e){
                JOptionPane.showMessageDialog(this, "Error reading file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                logMessage("Import failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper class used to rebuild RunSnapshots line-by-line during CSV import.
     */
    private static class RunBuilder {
        double valveLift, orifice;
        String comment = "";
        List<Double> time = new ArrayList<>(), p1 = new ArrayList<>(), p2 = new ArrayList<>(), p3 = new ArrayList<>();
        List<Double> t1 = new ArrayList<>(), t2 = new ArrayList<>(), t3 = new ArrayList<>();
        List<Double> cfm28 = new ArrayList<>(), cfmOrifice = new ArrayList<>(), massFlow = new ArrayList<>();

        RunBuilder(double valveLift, double orifice) {
            this.valveLift = valveLift;
            this.orifice = orifice;
        }

        void addDataPoint(double time,
                          double p1, double p2, double p3,
                          double t1, double t2, double t3,
                          double flowrateIn28ofH2O, double flowrateAtOrifice, double massFlowRate) {
            this.time.add(time);
            this.p1.add(p1); this.p2.add(p2); this.p3.add(p3);
            this.t1.add(t1); this.t2.add(t2); this.t3.add(t3);
            this.cfm28.add(flowrateIn28ofH2O); this.cfmOrifice.add(flowrateAtOrifice); this.massFlow.add(massFlowRate);
        }

        RunSnapshot build() {
            return new RunSnapshot(valveLift, orifice, time, p1, p2, p3, t1, t2, t3, cfm28, cfmOrifice, massFlow, comment);
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
