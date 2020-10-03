package test;
import main.core.Display;
import main.core.Engine;
import main.scenes.SceneHandler;

public class TestMain extends Engine {

    public static void main(String[] args) {
        Engine testMain = new TestMain();

        Display display = TestMain.getDisplay();
        display.setTitle("test");
        display.setSize( 600, 600);
        display.setPos( -1, -1);

        SceneHandler sceneHandler = TestMain.getSceneHandler();

        testMain.start();
    }

}
