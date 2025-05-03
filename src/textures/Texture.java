package textures;

import org.lwjgl.opengl.GL11;

public class Texture {

    private int textureId;
    private int width, height;

    Texture( int textureId, int width, int height) {
        this.textureId = textureId;
        this.width = width;
        this.height = height;

    }

    public int getTextureId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);


    }
    public void unbind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
