package test;

import main.core.MasterRenderer;
import main.core.ResourceLoader;
import main.input.InputHandler;
import main.model.Model;
import main.model.ModelBuilder;
import main.scenes.DefaultSceneIds;
import main.scenes.Scene;


public class TestScene extends Scene {

    private Model model;
    public TestScene( ) {
        super(DefaultSceneIds.Main_menu);
    }

    @Override
    public void initialize(ResourceLoader loader) {
        ModelBuilder modelBuilder = new ModelBuilder();

        float[] vertices = new float[]{
                -0.5f, 0.5f, 0f,
                -0.5f, -0.5f, 0f,
                0.5f, -0.5f, 0f,
                0.5f, 0.5f, 0f
        };
        int[] indices = new int[] {
                0, 1, 3,
                3, 1, 2
        };

        float[] colors = new float[] {
                1, 0, 0, 1,
                1, 0, 0, 0.1f,
                0, 1, 0, 0.1f,
                0, 1, 0, 1

        };
        model = modelBuilder.buildModel(vertices, indices, colors);
    }

    @Override
    public void update(double deltaTime, InputHandler inputHandler) {

    }

    @Override
    public void render(MasterRenderer renderer) {

        renderer.renderModel(model);

    }
}
