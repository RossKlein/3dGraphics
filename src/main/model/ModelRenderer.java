package main.model;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class ModelRenderer {

    public ModelRenderer() {

    }

    public void render( Model model) {
        bindModel(model);
        renderModel(model);
        unbindModel(model);
    }

    private void bindModel(Model model) {
        model.bind();
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
    }
    private void renderModel(Model model) {
        GL11.glDrawElements( GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);

    }

    private void unbindModel(Model model) {
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        model.unbind();
    }


}
