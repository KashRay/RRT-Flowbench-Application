import javax.swing.*;
import java.awt.FlowLayout;

public class GUI extends JFrame {
    JButton run, stop, export;
    
    public GUI() {
        super("Flow Test Bench GUI");

        this.setLayout(new FlowLayout());

        JPanel left, right, stats, inputs, data, info, graph, buttons;
        JTextArea cfmH2O, cfmOrifice, flowRate, instructions, testStatus, comments;
        JTextField valveLift, orificeDiameter, testDuration, pressureSensor1, pressureSensor2, pressureSensor3, tempSensor1, tempSensor2, tempSensor3;

        cfmH2O = new JTextArea("CFM at 28 in H20:", 2, 1);
        cfmOrifice = new JTextArea("CFM at Orifice:", 2, 1);
        flowRate = new JTextArea("Mass Flow Rate:", 2, 1);

        stats = new JPanel();
        stats.add(cfmH2O);
        stats.add(cfmOrifice);
        stats.add(flowRate);

        valveLift = new JTextField("Valve lift:");
        orificeDiameter = new JTextField("Orifice diameter:");
        testDuration = new JTextField("Testing duration:");

        inputs = new JPanel();
        inputs.add(valveLift);
        inputs.add(orificeDiameter);
        inputs.add(testDuration);

        instructions = new JTextArea("Instructions:\n(ex. Enter valve list value, hit RUN)", 2, 1);
        testStatus = new JTextArea("Test status\n(ex. test in progress/stopped)", 2, 1);

        info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(instructions);
        info.add(testStatus);

        comments = new JTextArea("Comments about test trial");

        data = new JPanel();
        data.add(info);
        data.add(comments);

        run = new JButton("RUN");
        stop = new JButton("STOP");
        export = new JButton("Export CSV");

        buttons = new JPanel();
        buttons.add(run);
        buttons.add(stop);
        buttons.add(export);

        left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(stats);
        left.add(inputs);
        left.add(data);
        left.add(buttons);

        pressureSensor1 = new JTextField("Pressure Diff Sensor 1 Status");
        pressureSensor2 = new JTextField("Pressure Diff Sensor 2 Status");
        pressureSensor3 = new JTextField("Pressure Diff Sensor 3 Status");
        tempSensor1 = new JTextField("Temperature Sensor 1 Status");
        tempSensor2 = new JTextField("Temperature Sensor 2 Status");
        tempSensor3 = new JTextField("Temperature Sensor 3 Status");

        right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(pressureSensor1);
        right.add(pressureSensor2);
        right.add(pressureSensor3);
        right.add(tempSensor1);
        right.add(tempSensor2);
        right.add(tempSensor3);

        this.add(left);
        this.add(right);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(1000, 500);
        this.setVisible(true);
    }

    public static void main(String[] args) {
        GUI gui = new GUI();
    }
}
