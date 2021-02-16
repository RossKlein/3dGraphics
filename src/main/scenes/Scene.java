package main.scenes;

import main.core.MasterRenderer;
import main.core.ResourceLoader;
import main.input.InputHandler;

public abstract class Scene {

    private Enum<?> sceneId;

    public Scene ( Enum<?> sceneId) {
        this.sceneId = sceneId;
    }

    public abstract void initialize(ResourceLoader loader);

    public abstract void update (double deltaTime, InputHandler inputHandler);

    public abstract void render (MasterRenderer renderer);

    public void enter() {
        System.out.println("entering " + sceneId);
    }
    public void leave() {
        System.out.println("leaving " + sceneId);


    }
    public void exit() {
        System.out.println("exiting " + sceneId);


    }

    public Enum<?> getSceneId() {
        return sceneId;

    }
}
