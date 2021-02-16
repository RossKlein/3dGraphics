package main.shaders;

public class StaticShader extends Shader{

    private static final String VERTEX_SHADER_FILE = "/shaders/static/vertexShader.glsl";
    private static final String FRAGMENT_SHADER_FILE = "/shaders/static/fragmentShader.glsl";

//    public StaticShader(String vertexShaderFile, String fragmentShaderFile) {
//        super(vertexShaderFile, fragmentShaderFile);
//    }

    public StaticShader() {
        super(VERTEX_SHADER_FILE, FRAGMENT_SHADER_FILE);
    }


    @Override
    protected void bindAttributes() {
        super.bindAttributeLocation(0 ,"position");
        super.bindAttributeLocation(1 ,"color");

    }
}
