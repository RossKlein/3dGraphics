package main.input;

import main.core.Display;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

public class InputHandler {
    private boolean[] keyboardKeys;
    private boolean[] mouseButtons;
    private static long windowId;
    private double xcursor, ycursor, xoffset, yoffset;
    private boolean focus;

    public InputHandler() {
        keyboardKeys = new boolean[386];
        mouseButtons = new boolean[8];
        xcursor = 0;
        ycursor = 0;
        xoffset = 0;
        yoffset = 0;
        focus = true;

    }

    public boolean isKeyDown(int key){
        return keyboardKeys[key];
    }
    public boolean isButtonDown(int button) {
        return mouseButtons[button];
    }
    public double getXcursor() {
        return xcursor;
    }

    public double getYcursor() {
        return ycursor;
    }
    public boolean isFocus() { return focus;}

    public double getXoffset() {
        double offset = this.xoffset;
        this.xoffset = 0;
        return offset;
    }

    public double getYoffset() {
        double offset = this.yoffset;
        this.yoffset = 0;
        return offset;
    }
    public static void  setCursorPos(double x, double y){
        GLFW.glfwSetCursorPos(windowId, x, y);
    }
    public static void setCursorPos(float x, float y){
        GLFW.glfwSetCursorPos(windowId, x, y);
    }
    public void registerInputHandler(long windowId) {
        this.windowId = windowId;
        GLFW.glfwSetInputMode(windowId, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetWindowFocusCallback(windowId, (window, focused) -> {
            if(focused){
                focus = true;
            }else {
                focus = false;
            }
        });
        GLFW.glfwSetKeyCallback(windowId, (window, key, scancode, action, mods) -> {
            if(key>=keyboardKeys.length){
                System.err.println("unidentified keyboard key pressed: " + key);
                return;
            }
            keyboardKeys[key] = action != GLFW.GLFW_RELEASE;
        });
        GLFW.glfwSetMouseButtonCallback(windowId, (window, button, action, mods) -> {
            if(button >= mouseButtons.length) {
                System.err.println("unidentified mouse button pressed: " + button);
                return;
            }
            mouseButtons[button] = action != GLFW.GLFW_RELEASE;
        });
        GLFW.glfwSetCursorPosCallback(windowId, (window, xpos, ypos) -> {
            this.xcursor = xpos;
            this.ycursor = ypos;
        });
        GLFW.glfwSetScrollCallback(windowId, (window, xoffset, yoffset) -> {

            this.xoffset = xoffset;
            this.yoffset = yoffset;

        });
    }
}
