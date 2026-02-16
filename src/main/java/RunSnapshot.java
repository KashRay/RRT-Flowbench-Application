import java.util.ArrayList;
import java.util.List;

/**
 * A small class to represent the data for a single test run.
 *
 * @author Rayyan Kashif
 * @version 1.0
 */
public class RunSnapshot {
    private final Double valveLift, orificeDiameter;
    private final List<Double> time, p1, p2, p3, t1, t2, t3, flowrateIn28OfH2O, flowrateAtOrifice, massFlowrate;
    private String comment;

    public RunSnapshot(Double valveLift, Double orificeDiameter,
                       List<Double> time, List<Double> p1, List<Double> p2, List<Double> p3,
                       List<Double> t1, List<Double> t2, List<Double> t3,
                       List<Double> flowrateIn28OfH2O, List<Double> flowrateAtOrifice, List<Double> massFlowrate, String comment)
    {
        this.valveLift = valveLift;
        this.orificeDiameter = orificeDiameter;
        //Create copies
        this.time = new ArrayList<>(time);
        this.p1 = new ArrayList<>(p1); this.p2 = new ArrayList<>(p2); this.p3 = new ArrayList<>(p3);
        this.t1 = new ArrayList<>(t1); this.t2 = new ArrayList<>(t2); this.t3 = new ArrayList<>(t3);
        this.flowrateIn28OfH2O = new ArrayList<>(flowrateIn28OfH2O); this.flowrateAtOrifice = new ArrayList<>(flowrateAtOrifice); this.massFlowrate = new ArrayList<>(massFlowrate);
        this.comment = comment;
    }

    //Getter methods
    public Double getValveLift() { return valveLift; }
    public Double getOrificeDiameter() { return orificeDiameter; }
    public List<Double> getTime() { return time; }
    public List<Double> getP1() { return p1; }
    public List<Double> getP2() { return p2; }
    public List<Double> getP3() { return p3; }
    public List<Double> getT1() { return t1; }
    public List<Double> getT2() { return t2; }
    public List<Double> getT3() {  return t3; }
    public List<Double> getCFMIn28OfH2O() { return flowrateIn28OfH2O; }
    public List<Double> getCFMAtOrifice() {  return flowrateAtOrifice; }
    public List<Double> getMassFlowrate() {  return massFlowrate; }
    public String getComment() { return comment; }

    /**
     * Setter method used for commenting on the previous run.
     * @param comment The final comment associated with the run
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Helper method for getting a data point for the final flow comparison graph.
     * @return The average flowrate in 28" of H2O in CFM
     */
    public double getAverageFlowrateIn28OfH2O() {
        return flowrateIn28OfH2O.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
