package main.model;

import main.core.Settings;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.w3c.dom.Text;

public class ModelRenderer {

    public ModelRenderer() {

    }

    public void render( Model model) {

        bindModel(model);
        renderModel(model);
        unbindModel(model);
    }

    public void render(TexturedModel texturedModel) {
        bindTexturedModel(texturedModel);
        renderTexturedModel(texturedModel);
        unbindTexturedModel(texturedModel);
    }

    private void bindModel(Model model) {
        model.bind();
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
    }
    private void renderModel(Model model) {
        if(Settings.wireframe){
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }

        GL11.glDrawElements( GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
    }

    private void unbindModel(Model model) {
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);

        model.unbind();
    }
    private void bindTexturedModel(TexturedModel texturedModel) {
        texturedModel.bind();
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
        GL20.glEnableVertexAttribArray(3);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        texturedModel.getTexture().bind();
    }
    private void renderTexturedModel(TexturedModel texturedModel) {
        if(Settings.wireframe){
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }
        GL11.glDrawElements( GL11.GL_TRIANGLES, texturedModel.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);

    }

    private void unbindTexturedModel(TexturedModel texturedModel) {
        texturedModel.getTexture().unbind();
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL20.glDisableVertexAttribArray(2);
        GL20.glEnableVertexAttribArray(3);

        texturedModel.unbind();
    }


}
