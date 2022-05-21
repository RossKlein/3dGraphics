package main.core;

import main.math.Mat4f;
import main.math.Vec3f;
import main.model.Model;
import main.model.ModelRenderer;
import main.model.TexturedModel;
import main.scenes.Scene;
import main.shaders.StaticShader;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MasterRenderer {

    private StaticShader staticShader;
    private ModelRenderer modelRenderer;
    private Mat4f[] matrix;
    private String[] matrixName;
    private int matPos;
    private boolean[] transpose;
    private Vec3f light;

    public MasterRenderer() {
        staticShader = new StaticShader();

        modelRenderer = new ModelRenderer();
        matrixName = new String[6];
        matrix = new Mat4f[6]; //arbitrarily making this object hold 6 arrays and their names
        transpose = new boolean[6]; //6 bools for 6 mats




    }
    public void loadMatrix(Mat4f matrix, String name, boolean transpose, int position){
        matPos = position;
        this.matrix[matPos] = matrix;
        this.matrixName[matPos] = name;
        this.transpose[matPos] = transpose;
    }
    public void loadLightSource(Vec3f vector){
        this.light = vector;
    }

    public void renderModel(Model model) {
        staticShader.startShader();
        for(int i=0; i<matPos+1;i++){
            staticShader.loadMatrix(this.matrix[i]/*Mat4f*/, this.matrixName[i]/*String*/, transpose[i]);

        }
        staticShader.loadLightSource(light);
        modelRenderer.render(model);
        staticShader.stopShader();

//        passShader.startShader();
//        for(int i=0; i<matPos;i+=2){
//            passShader.loadMatrix((Mat4f)this.matrix[i]/*Mat4f*/, (String)this.matrix[i+1]/*String*/, transpose[i/2]);
//        }
//        passShader.loadLightSource(light);
//        modelRenderer.render(model);
//
//        passShader.stopShader();
    }

    public void renderModel(TexturedModel texturedModel) {
        staticShader.startShader();
        for(int i=0; i<matPos+1;i++){
            staticShader.loadMatrix(this.matrix[i]/*Mat4f*/, this.matrixName[i]/*String*/, transpose[i]);

        }
        staticShader.loadLightSource(light);
        modelRenderer.render(texturedModel);
        staticShader.stopShader();
    }
}
