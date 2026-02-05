/**
 * A simple container for holding calculated physics values (prevents recalculation).
 */
public record FlowResult(double cfmOrifice, double massFlow, double cfm28) {}
