package main.model;

import org.lwjgl.opengl.GL30;

public class Model {

    private int vaoId;
    private int vertexCount;

    Model(int vaoId, int vertexCount){
        this.vaoId = vaoId;
        this.vertexCount = vertexCount;

    }

    public int getVaoId() {

        return vaoId;
    }
    public int getVertexCount() {
        return vertexCount;
    }
    public void bind() {
        GL30.glBindVertexArray( vaoId);
    }
    public void unbind() {
        GL30.glBindVertexArray( vaoId);
    }
    /////////////// 0:32 in the video part 2 https://www.youtube.com/watch?v=Y5PeIqUsztg&list=PLdG8a-MTV23I2Eej26Jh3y2oReczryXs7&index=2
}
