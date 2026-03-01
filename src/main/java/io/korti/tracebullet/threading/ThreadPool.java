package io.korti.tracebullet.threading;

public enum ThreadPool {
    METRIC("metric"),
    ;

    private final String name;

    ThreadPool(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
