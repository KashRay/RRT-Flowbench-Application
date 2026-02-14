import java.util.ArrayList;
import java.util.List;

/**
 * A small class to represent a collection of runs for a specific component configuration.
 *
 * @author Rayyan Kashif
 * @version 1.0
 */
public record TestSeries(String name, List<RunSnapshot> runs) {
    public TestSeries(String name, List<RunSnapshot> runs) {
        this.name = name;
        this.runs = new ArrayList<>(runs); //Create copy for data integrity
    }
}
