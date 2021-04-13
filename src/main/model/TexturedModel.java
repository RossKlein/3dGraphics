package main.model;

import textures.Texture;

public class TexturedModel extends Model {

    private Texture texture;

    TexturedModel(int vaoId, int vertexCount, Texture texture) {
        super(vaoId, vertexCount);
        this.texture = texture;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

}

