package Ross.Modules.models;

import Ross.textures.Texture;

public class TexturedModel extends Model {

    private Texture texture;

    TexturedModel(int vaoId, int vertexCount, int[] vboIds, Texture texture) {
        super(vaoId, vertexCount, vboIds);
        this.texture = texture;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

}


