package Ross.Modules.models;

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

        GL30.glBindVertexArray(0);
    }
    public void dispose() {
        GL30.glDeleteVertexArrays(vaoId);
//        for (int vbo : vboIds) {
//            GL15.glDeleteBuffers(vbo);
//        }
    }


}
