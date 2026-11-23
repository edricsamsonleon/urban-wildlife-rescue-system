package model;

public enum PriorityLevel {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    public static PriorityLevel fromScore(int score) {
        if (score >= 75) return CRITICAL;
        if (score >= 55) return HIGH;
        if (score >= 35) return MEDIUM;
        return LOW;
    }
}