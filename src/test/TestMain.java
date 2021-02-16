package test;
import main.core.Display;
import main.core.Engine;
import main.scenes.DefaultSceneIds;
import main.scenes.SceneHandler;

public class TestMain extends Engine {

    public static void main(String[] args) {
        Engine testMain = new TestMain();

        Display display = TestMain.getDisplay();
        display.setTitle("test");
        display.setSize( 1280, 720);
        display.setPos( -1, -1);

        SceneHandler sceneHandler = TestMain.getSceneHandler();
        sceneHandler.registerScene(new TestScene());
        sceneHandler.setScene(DefaultSceneIds.Main_menu);

        testMain.start();
    }

}
