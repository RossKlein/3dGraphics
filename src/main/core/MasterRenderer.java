package main.core;

import main.model.Model;
import main.model.ModelRenderer;
import main.shaders.StaticShader;

public class MasterRenderer {

    private StaticShader staticShader;
    private ModelRenderer modelRenderer;

    public MasterRenderer() {
        staticShader = new StaticShader();
        modelRenderer = new ModelRenderer();

    }

    public void renderModel(Model model) {
        staticShader.startShader();
        modelRenderer.render(model);
        staticShader.stopShader();
    }
}
