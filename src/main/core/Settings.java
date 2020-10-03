package main.core;

public class Settings  {

    static double maxTPS;

    private Settings() {

    }

    public static void setMaxTPS(int maxTPS) {
        Settings.maxTPS = maxTPS;
    }
}
