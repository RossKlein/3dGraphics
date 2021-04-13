package main.math;

public class Utils {

    public Utils() {

    }

    public static float normalize(float x, float xmin, float xmax) {

        return (x-xmin)/(xmax-xmin);
    }
}
