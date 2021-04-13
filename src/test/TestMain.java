package test;
import main.core.Display;
import main.core.Engine;
import main.core.Settings;
import main.scenes.DefaultSceneIds;
import main.scenes.SceneHandler;

public class TestMain extends Engine {

    public static void main(String[] args) {
        Engine testMain = new TestMain();
        TestScene testScene = new TestScene();
        Display display = TestMain.getDisplay();
        display.setTitle("Renderer");
        display.setSize( Settings.width, Settings.height );
        display.setPos( -1, -1);

        SceneHandler sceneHandler = TestMain.getSceneHandler();
        sceneHandler.registerScene(testScene);
        sceneHandler.setScene(DefaultSceneIds.Main_menu);

        Settings.setMaxTPS(100);

        testMain.start();
    }

}
