package test;

import main.core.*;
import main.input.InputHandler;
import main.math.*;
import main.model.*;
import main.scenes.DefaultSceneIds;
import main.scenes.Scene;
import main.shaders.Shader;
import org.lwjgl.glfw.GLFW;
import org.w3c.dom.ls.LSOutput;

import java.io.FileNotFoundException;
import java.util.Arrays;

public class TestScene extends Scene {

    private TexturedModel texturedModel;
    private Model model;
    private Model othermodel;
    private Model normalmodel;
    private Mat4f perspective;
    private Mat4f modelview;
    private Mat4f modelm;
    private Vec3f position;
    private Vec3f target;
    private Camera camera;
    private float aspectratio = (float)Settings.width/(float)Settings.height;
    private float fov = Settings.fov;
    private double oldoff;
    private double yoff;
    private double xvel;
    private double yvel;
    private float xrotate;
    private float yrotate;
    private double xvelold;
    private double yvelold;
    private float movementScale;
    float x, y, z;


    public TestScene( ) {
        super(DefaultSceneIds.Main_menu);
    }


    @Override
    public void initialize(ResourceLoader loader) {
        ModelBuilder modelBuilder = new ModelBuilder();
        DebugNormals debugger = new DebugNormals();
        OBJloader obj = new OBJloader("res/test/sphere.obj");
        OBJobject objModel = obj.returnOBJobject();
        OBJloader teapot1 = new OBJloader("res/test/utah-teapot.obj");
        OBJobject teapot = teapot1.returnOBJobject();


        float[] vertices = objModel.getVertices();

        int[] indices = objModel.getIndices();

        float[] colors = obj.genColor(new Vec4f(0, 1, 0, 1));
        float[] normals = objModel.getNormals();

        float[] uvCoordinates = objModel.getUvCoordinates();

        othermodel = modelBuilder.buildModel(vertices, indices, colors, normals);
        texturedModel = modelBuilder.buildModel(vertices, indices, colors, normals, uvCoordinates, loader.loadTexture("/test/brick.png"));





        vertices = teapot.getVertices();
        indices = teapot.getIndices();
        colors = teapot1.genColor(new Vec4f(0.5f, 0.5f, 1, 1));
        normals = teapot.getNormals();

        model = modelBuilder.buildModel(vertices, indices, colors, normals);

        debugger.lineMaker(normals, vertices, indices);
        vertices = debugger.getProcessedVertices();
        indices = debugger.getIndices();
        colors = debugger.getProcessedColors();
        uvCoordinates = teapot.getUvCoordinates();
        normalmodel = modelBuilder.buildModel(vertices, indices, colors, normals);

        perspective = sceneShading(fov, aspectratio, 0.1f, -10000);
        modelview = new Mat4f().identity();
        modelm = new Mat4f().identity();
        camera = new Camera();
        position = new Vec3f(0, 0, 0);
        target = new Vec3f();
        camera.rotate(0, 0, 0);



        xvel = 0;
        yvel = 0;
        xvelold = 0;
        yvelold = 0;
        movementScale = 1f;
        InputHandler.setCursorPos(0, 0);
    }

    @Override
    public void update(double deltaTime, InputHandler inputHandler) {

        ////////////// fov
        yoff = inputHandler.getYoffset();

        if(yoff-oldoff == 0){
            oldoff = 0;
        }else{
            oldoff = 5*yoff;
            fov = fov - 0.1f* (float) oldoff;
        }

        oldoff = inputHandler.getYoffset();
        ///////////////////
        //////// mouse movement

        xvel = inputHandler.getYcursor();
        yvel = inputHandler.getXcursor();

        xvel = (xvel - xvelold);
        yvel = (yvel - yvelold);

        xrotate = (float)xvel;
        yrotate = (float)yvel;

        xvelold = inputHandler.getYcursor();
        yvelold = inputHandler.getXcursor();


        camera.rotate(-0.2f*xrotate, -0.2f*yrotate, 0);

        /////

        target = new Vec3f(0, 0, -1);

        Mat4f cRotation = new Mat4f().rotate(camera.getRotation().y(), 0, 1, 0);
        cRotation = cRotation.dot(cRotation, new Mat4f().rotate(camera.getRotation().x(), 1, 0, 0));
        Vec4f lookDir = cRotation.matrixVecMult(cRotation, new Vec4f(target, 1));


        Vec3f forward = new Vec3f(lookDir);
        Vec3f upDir = new Vec3f(0, 1, 0);
        Vec3f xDir = new Vec3f().cross(upDir, forward);
        Vec3f yDir = new Vec3f().cross(forward, xDir);
        forward = forward.mult(movementScale);
        xDir = xDir.mult(movementScale);
        yDir = yDir.mult(2*movementScale);

        if (inputHandler.isKeyDown(87)) { //w
            position = position.sub(position, forward);
        }
        if (inputHandler.isKeyDown(83)) {//s
            position = position.add(position, forward);

        }
        if (inputHandler.isKeyDown(65)) {//a
            position = position.sub(position, xDir);
        }
        if (inputHandler.isKeyDown(68)) {//d
            position = position.add(position, xDir);

        }
        if (inputHandler.isKeyDown(81)) {//q
            position = position.sub(position, yDir);
        }
        if (inputHandler.isKeyDown(69)) {//e
            position = position.add(position, yDir);

        }
        target = new Vec3f(lookDir).normalize();



        /////

    }

    @Override
    public void render(MasterRenderer renderer) {

        perspective = sceneShading(fov, aspectratio, 0.01f, -1000);
        modelview = modelview.lookAt(new Vec3f(0, 0, 0), target, new Vec3f(0, 1, 0));
        //perspective.string();

        modelm = modelm.dot(modelm.translation(position.x(), position.y(), position.z()), modelm.scale(2));

        modelview = modelview.dot(modelview, modelm);
        perspective = perspective.dot(perspective, modelview);
        renderer.loadMatrix(modelm, "m", false, 0); //not being used
        renderer.loadMatrix(modelview, "v", false,1); //not being used
        renderer.loadMatrix(perspective, "p", false, 2);
        renderer.loadLightSource(new Vec3f(-200, 200, 300));
        renderer.renderModel(model);

      // renderer.renderModel(normalmodel);
///////////////
//        perspective = perspective.projection(fov, aspectratio, 0.0001f, -10000);
//        modelview = modelview.lookAt(new Vec3f(0, 0, 0), target, new Vec3f(0, 1, 0));
//        modelm = modelm.scale(1f);
//        modelm = modelm.dot(modelm.translation(position.x()-200, position.y()+200, position.z()+300), modelm);
//          modelview = modelview.dot(modelview, modelm);
//          perspective = perspective.dot(perspective, modelview);
//        renderer.loadMatrix(modelm, "m", false, 0);
//        renderer.loadMatrix(modelview, "v", false,1);
//        renderer.loadMatrix(perspective, "p", false, 2);
//        renderer.loadLightSource(new Vec3f(-200, 200, 300));
//
//        renderer.renderModel(othermodel);


    }

}

