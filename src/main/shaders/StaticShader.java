package main.shaders;

import main.math.Mat4f;
import main.math.Vec3f;

public class StaticShader extends Shader{

    private static final String VERTEX_SHADER_FILE = "/shaders/static/vertexShader.glsl";
    private static final String FRAGMENT_SHADER_FILE = "/shaders/static/fragmentShader.glsl";
    public StaticShader() {
        super(VERTEX_SHADER_FILE, FRAGMENT_SHADER_FILE);
    }


    @Override
    protected void bindAttributes() {
        super.bindAttributeLocation(0 ,"position");
        super.bindAttributeLocation(1 ,"color");
        super.bindAttributeLocation(2 ,"normals");
        super.bindAttributeLocation(3 ,"uvCoordinates");

    }

    @Override
    public void loadMatrix(Mat4f matrix, String name, boolean transpose) {
        super.bindUniformMatrix4fvLocation(matrix, name, transpose);



    }

    @Override
    public void loadLightSource(Vec3f vector) {
        super.bindUniform3vLocation(vector, "light");
    }


}
