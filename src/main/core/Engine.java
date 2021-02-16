package main.core;

import main.input.InputHandler;
import main.scenes.SceneHandler;

public class Engine {

    private static Display display;
    private static SceneHandler sceneHandler;
    private static InputHandler inputHandler;
    private static ResourceLoader resourceLoader;
    private static boolean run;
    public Engine() {
        display = new Display();
        sceneHandler = new SceneHandler();
        inputHandler = new InputHandler();
    }

    public static Display getDisplay() {
        return display;
    }

    public static SceneHandler getSceneHandler() {
        return sceneHandler;
    }

    public static InputHandler getInputHandler() {
        return inputHandler;
    }

    public void start() {
        display.create(0);
        inputHandler.registerInputHandler( display.getWindowId());
        resourceLoader = new ResourceLoader();
        sceneHandler.initializeRegisteredScenes(resourceLoader);
        Thread updateThread = new Thread(updateLoop());
        updateThread.setDaemon( true);
        run = true;
        updateThread.start();
        display.setVisible(true);
        renderLoop();
    }
    public void exit() {
        sceneHandler.exit();
        resourceLoader.exit();
        display.exit();
    }

    public void stop() {
        run = false;

    }

    ///////////// MAIN LOOPS ///////////////

    private Runnable updateLoop() {
        return () -> {

            final long TIME_PER_TICK = (long) (1e9 / Settings.maxTPS);
            long lastTime, thisTime, elapsedTime, sleepTime;
            double deltaTime;
            lastTime = System.nanoTime();

            while(run) {
                thisTime = System.nanoTime();
                elapsedTime = thisTime-lastTime;
                deltaTime = elapsedTime / 1e9;
                sleepTime = TIME_PER_TICK - elapsedTime;
                lastTime = thisTime;

                sceneHandler.updateActiveScene( deltaTime, inputHandler);
                sleep( sleepTime);
            }

        };
    }
    private void renderLoop() {
        MasterRenderer masterRenderer = new MasterRenderer();

        while(run) {
            sceneHandler.renderActiveScene(masterRenderer);
            display.update();

            run = !display.isClosed();
        }
        stop();
        exit();
    }
    private void sleep(long time) {
        if(time<=0) {
            return;
        }
        long millis = (long) (time/1e6);
        int nanos = (int) (time-millis*1e6);
        try {
            Thread.sleep(millis, nanos);
        } catch( InterruptedException e){
            System.err.println("Error in game loop . . .");
            e.printStackTrace();
        }
    }
}
