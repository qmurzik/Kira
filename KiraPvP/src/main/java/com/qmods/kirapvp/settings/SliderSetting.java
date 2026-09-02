package com.qmods.kirapvp.settings;

public final class SliderSetting extends Setting<Double> {

    private final double min;
    private final double max;
    private final double step;
    private final String suffix;

    public SliderSetting(String name, double defaultValue, double min, double max, double step, String suffix) {
        super(name, clamp(defaultValue, min, max));
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** Sets the value from a 0..1 fraction (as reported by a dragged slider), snapped to step. */
    public void setFromFraction(double fraction) {
        double raw = min + (max - min) * clamp(fraction, 0, 1);
        double stepped = step > 0 ? Math.round(raw / step) * step : raw;
        setValue(clamp(stepped, min, max));
    }

    public double getFraction() {
        return (getValue() - min) / (max - min);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    @Override
    public String displayValue() {
        double v = getValue();
        String text = (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100.0) / 100.0);
        return suffix == null ? text : text + suffix;
    }
}
