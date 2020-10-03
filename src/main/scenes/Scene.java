package main.scenes;

import main.core.MasterRenderer;
import main.core.ResourceLoader;
import main.input.InputHandler;

public abstract class Scene {

    private Enum<?> sceneId;

    public Scene ( Enum<?> SceneId) {
        this.sceneId = sceneId;
    }

    public abstract void initialize(ResourceLoader loader);

    public abstract void update (double deltaTime, InputHandler inputHandler);

    public abstract void render (MasterRenderer renderer);

    public void enter() {

    }
    public void leave() {

    }
    public void exit() {

    }

    public Enum<?> getSceneId() {
        return sceneId;
    }
}
