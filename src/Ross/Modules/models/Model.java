package Ross.Modules.models;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

public class Model {

    private int vaoId;
    private int vertexCount;
    private int[] vboIds;

    Model(int vaoId, int vertexCount, int[] vboIds) {
        this.vaoId      = vaoId;
        this.vertexCount = vertexCount;
        this.vboIds     = vboIds;
    }

    public int getVaoId()      { return vaoId; }
    public int getVertexCount() { return vertexCount; }

    public void bind()   { GL30.glBindVertexArray(vaoId); }
    public void unbind() { GL30.glBindVertexArray(0); }

    /** Frees the VAO and all associated VBOs/EBOs. Must be called on the GL thread. */
    public void dispose() {
        GL30.glDeleteVertexArrays(vaoId);
        if (vboIds != null) {
            for (int vbo : vboIds) {
                GL15.glDeleteBuffers(vbo);
            }
        }
    }
}
