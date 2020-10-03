package main.core;
import main.input.InputHandler;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;


public class Display {

    private long windowId;
    private String title;
    private int x, y;
    private int w, h;
    private int resizable;
    private long fullscreen;
    private int vSync;

    public Display() {
        windowId = 0;
        title = "";
        x = -1;
        y = -1;
        w = 1;
        h = 0;
        resizable = GLFW.GLFW_FALSE;
        fullscreen = 0;
        vSync = GLFW.GLFW_TRUE;
    }

    public long getWindowId() {
        return windowId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        if (windowId != 0) {
            GLFW.glfwSetWindowTitle(windowId, title);
        }
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
        if (windowId != 0) {
            GLFW.glfwSetWindowPos(windowId, x, y);
        }
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
        if (windowId != 0) {
            GLFW.glfwSetWindowPos(windowId, x, y);
        }
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
        if (windowId != 0) {
            GLFW.glfwSetWindowPos(windowId, x, y);
        }
    }

    public int getW() {
        return w;
    }

    public void setW(int w) {
        this.w = w;
        if (windowId != 0) {
            GLFW.glfwSetWindowSize(windowId, w, h);
        }
    }

    public int getH() {
        return h;
    }

    public void setH(int h) {
        this.h = h;
        if (windowId != 0) {
            GLFW.glfwSetWindowSize(windowId, w, h);
        }
    }

    public void setSize(int w, int h) {
        this.w = w;
        this.h = h;
        if (windowId != 0) {
            GLFW.glfwSetWindowSize(windowId, w, h);
        }
    }

    public boolean getFullscreen() {
        return fullscreen == GLFW.GLFW_TRUE;
    }

    public void setFullscreen(boolean fullscreen) {
        if (fullscreen) {
            this.fullscreen = GLFW.glfwGetPrimaryMonitor();
        } else {
            this.fullscreen = GLFW.GLFW_FALSE;
        }
        if (windowId != 0) {
            create(0);
        }
    }

    public boolean getVSync() {
        return vSync == GLFW.GLFW_TRUE;
    }

    public void setvSync(boolean vSync) {
        if (vSync) {
            this.vSync = GLFW.GLFW_TRUE;
        } else {
            this.vSync = GLFW.GLFW_FALSE;
        }
        if (windowId != 0) {
            GLFW.glfwSwapInterval(this.vSync);
        }
    }


    public void setVisible(boolean visible) {
        if (windowId == 0) {
            return;
        }
        if (visible) {
            GLFW.glfwShowWindow(windowId);
        } else {
            GLFW.glfwHideWindow(windowId);
        }
    }

    public boolean isClosed() {
        if (windowId == 0) {
            return true;
        }
        return GLFW.glfwWindowShouldClose(windowId);
    }

    public void create(long sharedWindow) {
        boolean initSuccess = GLFW.glfwInit();
        if (!initSuccess) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, resizable);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        if( x == -1&&y == -1) {
            GLFWVidMode vidMode = GLFW.glfwGetVideoMode( GLFW.glfwGetPrimaryMonitor());
            this.x = (vidMode.width() - w) / 2;
            this.y = (vidMode.height() - h) / 2;
        }

        windowId = GLFW.glfwCreateWindow(w, h, title, 0, sharedWindow);
        GLFW.glfwSetWindowPos(windowId, x, y);
        GLFW.glfwMakeContextCurrent(windowId);
        GLFW.glfwSwapInterval(vSync);
        GL.createCapabilities();

        GL11.glClearColor(1f, 1f, 1f, 1f);
    }

    public void update() {
        GLFW.glfwPollEvents();
        GLFW.glfwSwapBuffers(windowId);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

    }

    public void exit() {
        setVisible(false);
        GLFW.glfwDestroyWindow(windowId);
        GLFW.glfwTerminate();
    }



}
