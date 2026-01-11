package Ross.Modules;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.Callback;

import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glViewport;


public class Window {

    private long windowId;
    private String title;
    private int x, y;
    private int w, h;
    private int resizable;
    private long fullscreen;
    private int vSync;

    public Window(JobModule taskModule) {
        windowId = 0;
        title = "Window";
        x = -1;
        y = -1;
        w = 1000;
        h = 1000;
        resizable = GLFW_TRUE;
        fullscreen = 0;
        vSync = Settings.vsync;
        taskModule.setWindow(this);
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
        Settings.height = h;
        Settings.width = w;
        if (windowId != 0) {
            GLFW.glfwSetWindowSize(windowId, w, h);
        }
    }

    public boolean getFullscreen() {
        return fullscreen == GLFW_TRUE;
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

    public boolean getvSync() {
        return vSync == GLFW_TRUE;
    }

    public void setvSync(boolean vSync) {
        if (vSync) {
            this.vSync = GLFW_TRUE;
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
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
        glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        glfwWindowHint(GLFW.GLFW_RESIZABLE, resizable);
        glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);


        if( x == -1&&y == -1) {
            GLFWVidMode vidMode = GLFW.glfwGetVideoMode( GLFW.glfwGetPrimaryMonitor());
            this.x = (vidMode.width() - w) / 2;
            this.y = (vidMode.height() - h) / 2;
        }



        windowId = GLFW.glfwCreateWindow(w, h, title, fullscreen, sharedWindow);

        if(windowId == 0){
            throw new IllegalStateException( "failed to create display");
        }

        GLFW.glfwSetWindowPos(windowId, x, y);
        GLFW.glfwMakeContextCurrent(windowId);
        GLFW.glfwSwapInterval(vSync);
        GL.createCapabilities();

        Callback debugProc = GLUtil.setupDebugMessageCallback();

        GL11.glClearColor(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LESS);
//        GL11.glEnable(GL11.GL_CULL_FACE);
//        GL11.glCullFace(GL11.GL_BACK);


        GLFWFramebufferSizeCallback resizeCallback = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                glViewport(0, 0, width, height);
                Settings.width = width;
                Settings.height = height;
            }
        };

        glfwSetFramebufferSizeCallback(windowId, resizeCallback);
        GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT);
        GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);

// Disable NOTIFICATION-level messages
        KHRDebug.glDebugMessageControl(
                GL11.GL_DONT_CARE, // source
                GL11.GL_DONT_CARE, // type
                KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION, // severity
                (int[]) null,
                false // disabled
        );

        System.out.println("GL_RENDERER: " + GL11.glGetString(GL11.GL_RENDERER));


    }

    public void update() {
        long thisTime = System.nanoTime();
        GLFW.glfwPollEvents();
        long thatTime = System.nanoTime();
        double delta = (thatTime-thisTime)/1e6;
        if(delta > 16){
            System.out.println("Polling time (ms): " + delta);
        }
        GLFW.glfwSwapBuffers(windowId);

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

    }


    public void exit() {
        setVisible(false);
        GLFW.glfwDestroyWindow(windowId);
        GLFW.glfwTerminate();
    }



}
