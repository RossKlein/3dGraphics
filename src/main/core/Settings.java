package main.core;

public class Settings  {

    static double maxTPS;
    public static int width = 1720;
    public static int height = 1000;
    public static boolean wireframe = true;
    private Settings() {

    }

    public static void setMaxTPS(int maxTPS) {
        Settings.maxTPS = maxTPS;
    }
}
