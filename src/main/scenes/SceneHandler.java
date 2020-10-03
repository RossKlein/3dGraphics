package main.scenes;

import main.core.MasterRenderer;
import main.core.ResourceLoader;
import main.input.InputHandler;

import java.util.HashMap;

public class SceneHandler {

    private HashMap<Enum<?>,Scene> registeredScenes;
    private Scene activeScene;

    public SceneHandler() {
        registeredScenes = new HashMap<>();
        Scene splashScreen = new SplashScreen();
        registeredScenes.put(splashScreen.getSceneId(), splashScreen);
        splashScreen.enter();
        activeScene = splashScreen;
    }

    public void registerScene(Scene scene) {
        Enum<?> sceneId = scene.getSceneId();
        if(registeredScenes.containsKey(sceneId)){
            throw new IllegalArgumentException( "there's already a scene registered with that Id" + scene);
        }
        registeredScenes.put( sceneId,scene);
    }
    public void removeScene(Enum<?> sceneId){
        if(!registeredScenes.containsKey( sceneId)){
            throw new IllegalArgumentException( "there's no scene registered with that Id" + sceneId);
        }
        registeredScenes.remove(sceneId);
    }
    public void setScene(Enum<?> sceneId) {
        if(!registeredScenes.containsKey( sceneId)){
            throw new IllegalArgumentException( "there's no scene registered with that Id" + sceneId);
        }
        Scene oldScene = activeScene;
        Scene newScene = registeredScenes.get( sceneId);
        oldScene.leave();
        newScene.enter();
        activeScene = newScene;
    }




    public void initializeRegisteredScenes(ResourceLoader resourceLoader) {
        for( Scene scene : registeredScenes.values()) {
            scene.initialize(resourceLoader);
        }
    }

    public void updateActiveScene(double deltaTime, InputHandler inputHandler){
        if(activeScene != null){
            activeScene.update( deltaTime, inputHandler);
        }
    }

    public void renderActiveScene(MasterRenderer masterRenderer) {
        if(activeScene != null){
            activeScene.render( masterRenderer);
        }
    }

    public void exit() {
        activeScene.leave();
        activeScene = null;
        for( Scene scene : registeredScenes.values()) {
            scene.exit();
        }
        registeredScenes.clear();
    }
}
