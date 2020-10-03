package main.input;

import main.core.Display;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

public class InputHandler {
    private boolean[] keyboardKeys;
    private boolean[] mouseButtons;

    private double xcursor, ycursor;

    public InputHandler() {
        keyboardKeys = new boolean[386];
        mouseButtons = new boolean[8];
        xcursor = 0;
        ycursor = 0;
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

    public void registerInputHandler(long windowId) {
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
            xcursor = xpos;
            ycursor = ypos;
        });
    };
}
