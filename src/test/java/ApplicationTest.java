import org.testng.annotations.Test;
import static org.testng.AssertJUnit.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApplicationTest {
    /**
     * Tests whether the averaging method within snapshot produces the correct results.
     */
    @Test
    public void testRunSnapshotAverageMethod() {
        List<Double> time = new ArrayList<>();
        List<Double> p1 = new ArrayList<>();
        List<Double> p2 = new ArrayList<>();
        List<Double> p3 = new ArrayList<>();
        List<Double> t1 = new ArrayList<>();
        List<Double> t2 = new ArrayList<>();
        List<Double> t3 = new ArrayList<>();
        List<Double> cfmIn28OfH2O = new ArrayList<>();
        List<Double> cfmAtOrifice = new ArrayList<>();
        List<Double> massFlowrate = new ArrayList<>();

        time.add(1.0);
        p1.add(1.0);
        p2.add(1.0);
        p3.add(1.0);
        t1.add(1.0);
        t2.add(1.0);
        t3.add(1.0);
        cfmAtOrifice.add(1.0);
        massFlowrate.add(1.0);

        cfmIn28OfH2O.add(1.0);
        cfmIn28OfH2O.add(2.0);

        RunSnapshot run = new RunSnapshot(1.0, 1.0, time, p1, p2, p3, t1, t2, t3, cfmIn28OfH2O, cfmAtOrifice, massFlowrate, "TestRunSnapshotAverageMethod");

        assertEquals(1.5, run.getAverageFlowrateIn28OfH2O());
    }

    /**
     * Tests whether importing and exporting creates an identical file.
     * Copy of the code since the normal code involves GUI interaction.
     */
    @Test
    public void testCSVImportExportConsistency() throws IOException {
        File inputFile = new File("testExport.csv");
        if (!inputFile.exists()) {
            System.err.println("testExport.csv not found in working directory. Skipping test.");
            return;
        }

        List<String> originalLines = new ArrayList<>();
        Map<String, Map<Integer, RunBuilder>> parsedData = new LinkedHashMap<>();

        // 1. IMPORT LOGIC (Simulated from DashboardView)
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String header = br.readLine();
            originalLines.add(header);

            String line;
            while ((line = br.readLine()) != null) {
                originalLines.add(line);
                String[] parts = line.split(",", -1);
                if (parts.length < 14) continue;

                String seriesName = parts[0];
                int runID = Integer.parseInt(parts[1]);
                double vLift = Double.parseDouble(parts[2]);
                double orificeDia = Double.parseDouble(parts[3]);
                double t = Double.parseDouble(parts[4]);
                double p1 = Double.parseDouble(parts[5]), p2 = Double.parseDouble(parts[6]), p3 = Double.parseDouble(parts[7]);
                double t1 = Double.parseDouble(parts[8]), t2 = Double.parseDouble(parts[9]), t3 = Double.parseDouble(parts[10]);
                double cfm28 = Double.parseDouble(parts[11]), cfmO = Double.parseDouble(parts[12]), mass = Double.parseDouble(parts[13]);
                String comment = parts.length > 14 ? parts[14] : "";

                parsedData.putIfAbsent(seriesName, new LinkedHashMap<>());
                Map<Integer, RunBuilder> seriesRuns = parsedData.get(seriesName);

                seriesRuns.putIfAbsent(runID, new RunBuilder(vLift, orificeDia));
                RunBuilder builder = seriesRuns.get(runID);

                builder.addDataPoint(t, p1, p2, p3, t1, t2, t3, cfm28, cfmO, mass);
                if (!comment.isEmpty() && builder.comment.isEmpty()) {
                    builder.comment = comment;
                }
            }
        }

        // 2. EXPORT LOGIC (Simulated from DashboardView)
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);

        // Exact header match
        writer.println("Series Name,Run ID,Valve Lift,Orifice Diameter,Time,P1 (hPa),P2 (hPa),P3 (hPa),T1 (C),T2 (C),T3 (C),Flowrate at 28\" in H20 (CFM),Flowrate at Orifice (CFM),Mass Flowrate (kg/s),Comments");

        for (Map.Entry<String, Map<Integer, RunBuilder>> seriesEntry : parsedData.entrySet()) {
            String seriesName = seriesEntry.getKey();
            List<RunSnapshot> runs = new ArrayList<>();
            for (RunBuilder builder : seriesEntry.getValue().values()) {
                runs.add(builder.build());
            }

            for (int i = 0; i < runs.size(); i++) {
                RunSnapshot run = runs.get(i);
                int runID = i + 1;

                for (int j = 0; j < run.getTime().size(); j++) {
                    writer.printf("%s,%d,%s,%s,%.3f,%.2f,%.2f,%.2f,%.1f,%.1f,%.1f,%.2f,%.2f,%.5f,%s%n",
                            seriesName,
                            runID,
                            run.getValveLift(),
                            run.getOrificeDiameter(),
                            run.getTime().get(j),
                            run.getP1().get(j), run.getP2().get(j), run.getP3().get(j),
                            run.getT1().get(j), run.getT2().get(j), run.getT3().get(j),
                            run.getCFMIn28OfH2O().get(j),
                            run.getCFMAtOrifice().get(j),
                            run.getMassFlowrate().get(j),
                            (j == 0) ? run.getComment() : ""
                    );
                }
            }
        }
        writer.flush();

        // 3. COMPARE
        String[] exportedLines = sw.toString().split("\\r?\\n");
        assertEquals("Total line count mismatch", originalLines.size(), exportedLines.length);

        for (int i = 0; i < originalLines.size(); i++) {
            assertEquals("Mismatch at line " + (i + 1), originalLines.get(i), exportedLines[i]);
        }
    }

    /**
     * Copy of the DashboardView helper class for building runs during the test.
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

        void addDataPoint(double t, double p1, double p2, double p3, double t1, double t2, double t3, double c28, double cO, double mf) {
            this.time.add(t); this.p1.add(p1); this.p2.add(p2); this.p3.add(p3);
            this.t1.add(t1); this.t2.add(t2); this.t3.add(t3);
            this.cfm28.add(c28); this.cfmOrifice.add(cO); this.massFlow.add(mf);
        }

        RunSnapshot build() {
            return new RunSnapshot(valveLift, orifice, time, p1, p2, p3, t1, t2, t3, cfm28, cfmOrifice, massFlow, comment);
        }
    }
}
