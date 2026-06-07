package com.cityguardian.engine;

public class OptimizationResult {
    public final int firetrucks;
    public final int ambulances;
    public final int helicopters;
    public final String explanation;

    public OptimizationResult(int firetrucks, int ambulances, int helicopters, String explanation) {
        this.firetrucks = firetrucks;
        this.ambulances = ambulances;
        this.helicopters = helicopters;
        this.explanation = explanation;
    }
}
