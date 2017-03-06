package com.talanlabs.sonar.plugins.gitlab;

public enum BuildInitState {

    PENDING("pending"), RUNNING("running");

    private final String meaning;

    BuildInitState(String meaning) {
        this.meaning = meaning;
    }

    public static BuildInitState of(String meaning) {
        for (BuildInitState m : values()) {
            if (m.meaning.equals(meaning)) {
                return m;
            }
        }
        return null;
    }

    public String getMeaning() {
        return meaning;
    }
}
