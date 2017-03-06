package com.talanlabs.sonar.plugins.gitlab;

public enum StatusNotificationsMode {

    COMMIT_STATUS("commit-status"), EXIT_CODE("exit-code");

    private final String meaning;

    StatusNotificationsMode(String meaning) {
        this.meaning = meaning;
    }

    public String getMeaning() {
        return meaning;
    }

    public static StatusNotificationsMode of(String meaning) {
        for (StatusNotificationsMode m : values()) {
            if (m.meaning.equals(meaning)) {
                return m;
            }
        }
        return null;
    }
}
