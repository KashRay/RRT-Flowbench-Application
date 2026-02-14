import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

public class CalculationTest {
    @Test
    public void TestRunSnapshotAverageMethod() {
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
}
