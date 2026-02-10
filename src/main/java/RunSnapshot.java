import java.util.ArrayList;
import java.util.List;

/**
 * A small class to hold the data for a single test run.
 * @author Rayyan Kashif
 * @version 1.0
 */
public class RunSnapshot {
    Double valveLift;
    Double orificeDiameter;
    List<Double> time, p1, p2, p3, t1, t2, t3, cfmIn28OfH20, cfmAtOrifice, massFlowrate;
    String comment;

    public RunSnapshot(Double valveLift, Double orifice,
                       List<Double> t, List<Double> p1, List<Double> p2, List<Double> p3,
                       List<Double> t1, List<Double> t2, List<Double> t3,
                       List<Double> cfmIn28OfH20, List<Double> cfmAtOrifice, List<Double> massFlowrate, String comment)
    {
        this.valveLift = valveLift;
        this.orificeDiameter = orifice;
        //Create copies
        this.time = new ArrayList<>(t);
        this.p1 = new ArrayList<>(p1); this.p2 = new ArrayList<>(p2); this.p3 = new ArrayList<>(p3);
        this.t1 = new ArrayList<>(t1); this.t2 = new ArrayList<>(t2); this.t3 = new ArrayList<>(t3);
        this.cfmIn28OfH20 = new ArrayList<>(cfmIn28OfH20); this.cfmAtOrifice = new ArrayList<>(cfmAtOrifice); this.massFlowrate = new ArrayList<>(massFlowrate);
        this.comment = comment;
    }
}
