package main.core;

public class Settings  {

    static double maxTPS = 100;
    private Settings() {

    }

    public static void setMaxTPS(int maxTPS) {
        Settings.maxTPS = maxTPS;
    }
}
