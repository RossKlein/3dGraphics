package Ross.Modules;


import org.lwjgl.glfw.GLFW;

public class Settings  {

    static double maxTPS = 100;
    static double maxFPS = 100;
    public static int width = 1720;
    public static int height = 1000;
    public static boolean wireframe = false;
    public static int fov = 60;
    public static int vsync = 1;
    private Settings() {

    }

    public static void setMaxTPS(int maxTPS) {
        Settings.maxTPS = maxTPS;
    }
}
