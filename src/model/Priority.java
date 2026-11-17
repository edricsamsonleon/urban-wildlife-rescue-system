package model;

public enum Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    public static Priority fromScore(int score) {
        if (score >= 75) return CRITICAL;
        if (score >= 55) return HIGH;
        if (score >= 35) return MEDIUM;
        return LOW;
    }
}