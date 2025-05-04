package Ross.Modules;


import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import Ross.Modules.models.Model;
import Ross.Modules.models.ModelRenderer;
import Ross.Modules.models.TexturedModel;
import Ross.Modules.shaders.StaticShader;

import java.util.ArrayList;

public class Renderer {
    public record MatrixBinding(Mat4f matrix, String name, boolean transpose) {}
    public record bindBool(String name, boolean value) {}

    private StaticShader staticShader;
    private ModelRenderer modelRenderer;
    private ArrayList<Mat4f>  matrix;
    private ArrayList<String> matrixName;
    private int matPos;
    private ArrayList<Boolean> transpose;
    private ArrayList<bindBool> boolstobind;
    private Vec3f light;
    ArrayList<MatrixBinding> matrixBindings;

    public Renderer() {
        staticShader = new StaticShader();

        modelRenderer = new ModelRenderer();
//        matrixName = new String[6];
//        matrix = new Mat4f[6]; //arbitrarily making this object hold 6 arrays and their names
        matrix = new ArrayList<>();
        matrixName = new ArrayList<>();
        boolstobind = new ArrayList<>();


        matrixBindings = new ArrayList<>();
        transpose = new ArrayList<>();//6 bools for 6 mats




    }

    public void clear() {
        matrixBindings.clear();
    }
    public void loadMatrix(Mat4f matrix, String name, boolean transpose, int position){
//        this.matrix.add(matrix);
//        this.matrixName.add(name);
//        this.transpose.add(transpose);
        matrixBindings.add(new MatrixBinding(matrix, name, transpose));
    }
    public void loadLightSource(Vec3f vector){
        this.light = vector;
    }
    public void addBindBool(String name, boolean value){
        this.boolstobind.add(new bindBool(name, value));
    }

    public void renderModel(Model model) {
        staticShader.startShader();
//        for(int i=0; i<matPos+1;i++){
//            staticShader.loadMatrix(this.matrix[i]/*Mat4f*/, this.matrixName[i]/*String*/, transpose[i]);
//
//        }
        for (MatrixBinding binding : matrixBindings) {
            staticShader.loadMatrix(binding.matrix(), binding.name(), binding.transpose());
        }
        matrixBindings.clear();
        staticShader.loadLightSource(light);
        for (bindBool binding : boolstobind) {
            staticShader.bindUniformBool(binding.name(), binding.value());
        }
        boolstobind.clear();
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
//        for(int i=0; i<matPos+1;i++){
//            staticShader.loadMatrix(this.matrix[i]/*Mat4f*/, this.matrixName[i]/*String*/, transpose[i]);
//
//        }
        for (MatrixBinding binding : matrixBindings) {
            staticShader.loadMatrix(binding.matrix(), binding.name(), binding.transpose());
        }
        staticShader.loadLightSource(light);
        modelRenderer.render(texturedModel);
        staticShader.stopShader();
    }


}
