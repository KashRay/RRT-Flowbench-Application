/**
 * A small class for holding calculated physics values (prevents recalculation).
 *
 * @author Rayyan Kashif
 * @version 1.0
 */
public record FlowResult(double flowrateAtOrifice, double massFlowrate, double flowrateIn28OfH2O) {
    //Constants
    public static double DISCHARGE_COEFFICIENT = 0.61;
    public static double PIPE_INNER_DIAMETER = 4.0;
    public static double EXPANSIBILITY_FACTOR = 1;
    public static double SPECIFIC_GAS_CONSTANT = 287.1;
    public static double INCHES_TO_METERS = 0.0254;
    public static double CELSIUS_TO_KELVIN = 273.15;
    public static double KgPerS_TO_CFM = 2118.88;
    public static double DIFFERENTIAL_PRESSURE_IN_H20_TO_Pa = 249.09;

    /**
     * Master calculation method and packager.
     * @param currentP1 The current differential pressure (P1) in hPa
     * @param currentP2 The current differential pressure (P2) in hPa
     * @param currentT1 The current temperature (T1) in C
     * @param currentOrificeDiameter The current orifice diameter in inches
     * @return A packaged FlowResult containing all calculated values
     */
    public static FlowResult calculate(double currentP1, double currentP2, double currentT1, double currentOrificeDiameter) {
        //Safety check
        if (currentP1 <= 0 || currentP2 <= 0 || currentOrificeDiameter <= 0) return new FlowResult(0.0, 0.0, 0.0);

        //Calculate fluid density (rho)
        double rho = calculateRho(currentP2, currentT1);

        //Calculate mass flow rate
        double massFlowrate = calculateMassFlowRate(currentP1, rho, currentOrificeDiameter);

        //Calculate actual volumetric flow (CFM)
        double flowrateAtOrifice = calculateCFMatOrifice(massFlowrate, rho);

        //Calculate corrected flow (CFM @ 28" in H20)
        double flowrateIn28OfH2O = calculateCFMat28inH20(flowrateAtOrifice, currentP1);

        //Return package of calculated values
        return new FlowResult(flowrateAtOrifice, massFlowrate, flowrateIn28OfH2O);
    }

    /**
     * Helper method for calculating the beta ratio for the orifice plate.
     * @param dInches The current orifice diameter in inches
     * @return The dimensionless beta ratio
     */
    private static double calculateBeta(double dInches) {
        return dInches / PIPE_INNER_DIAMETER;
    }

    /**
     * Helper method for calculating the air density based on the current absolute pressure and temperature.
     * @param pAbsHPa The current absolute pressure (P2) in hPa
     * @param tempC The current temperature (T1) in C
     * @return The current calculated air density in kg/m^3
     */
    private static double calculateRho(double pAbsHPa, double tempC) {
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
    private static double calculateMassFlowRate(double deltaPHPa, double rho, double dInches) {
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
    private static double calculateCFMatOrifice(double massFlowKgPerS, double rho) {
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
    private static double calculateCFMat28inH20(double cfmActual, double deltaPHPa) {
        //Unit conversions
        double targetPressurePa = 28.0 * DIFFERENTIAL_PRESSURE_IN_H20_TO_Pa;
        double measuredPressurePa = deltaPHPa * 100.0;

        //Avoid divide by 0
        if (measuredPressurePa <= 0) return 0.0;

        //Formula: Q_28 = Q_actual * sqrt(TargetP / MeasuredP)
        return cfmActual * Math.sqrt(targetPressurePa / measuredPressurePa);
    }
}
