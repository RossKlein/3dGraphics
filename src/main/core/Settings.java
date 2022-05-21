package main.core;

public class Settings  {

    static double maxTPS;
    public static int width = 1720;
    public static int height = 1000;
    public static boolean wireframe = false;
    public static int fov = 60;
    private Settings() {

    }

    public static void setMaxTPS(int maxTPS) {
        Settings.maxTPS = maxTPS;
    }
}
