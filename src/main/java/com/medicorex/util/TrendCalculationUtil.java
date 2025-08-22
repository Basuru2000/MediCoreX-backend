package com.medicorex.util;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrendCalculationUtil {

    /**
     * Calculate linear regression trend
     */
    public double calculateLinearTrend(List<Double> values) {
        if (values.size() < 2) return 0;

        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }

    /**
     * Calculate moving average
     */
    public double calculateMovingAverage(List<Double> values, int period) {
        if (values.size() < period) return 0;

        return values.stream()
                .skip(Math.max(0, values.size() - period))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    /**
     * Calculate exponential smoothing
     */
    public double calculateExponentialSmoothing(List<Double> values, double alpha) {
        if (values.isEmpty()) return 0;

        double smoothed = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            smoothed = alpha * values.get(i) + (1 - alpha) * smoothed;
        }

        return smoothed;
    }
}