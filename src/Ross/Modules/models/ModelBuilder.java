package Ross.Modules.models;


import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import Ross.textures.Texture;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ModelBuilder {

    public ModelBuilder() { }

    public Model buildModel(float[] vertices, int[] indices, float[] colors, float[] normals) {
        int vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        int ebo  = bindIndices(indices);
        int vbo0 = storeDataInAttributeList(0, 3, vertices);
        int vbo1 = storeDataInAttributeList(1, 4, colors);
        int vbo2 = storeDataInAttributeList(2, 3, normals);

        GL30.glBindVertexArray(0);
        return new Model(vaoId, indices.length, new int[]{ebo, vbo0, vbo1, vbo2});
    }

    public TexturedModel buildModel(float[] vertices, int[] indices, float[] colors, float[] normals,
                                    float[] uvCoordinates, Texture texture) {
        int vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        int ebo  = bindIndices(indices);
        int vbo0 = storeDataInAttributeList(0, 3, vertices);
        int vbo1 = storeDataInAttributeList(1, 4, colors);
        int vbo2 = storeDataInAttributeList(2, 3, normals);
        int vbo3 = storeDataInAttributeList(3, 2, uvCoordinates);

        GL30.glBindVertexArray(0);
        return new TexturedModel(vaoId, indices.length, new int[]{ebo, vbo0, vbo1, vbo2, vbo3}, texture);
    }

    /** Uploads an index buffer and returns its VBO id. */
    private int bindIndices(int[] indices) {
        int vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboId);
        // MemoryUtil: explicit native alloc+free so the buffer is freed immediately
        // after glBufferData instead of waiting for GC finalization.
        // BufferUtils.createIntBuffer() leaves the direct buffer alive until the
        // next GC cycle — with -Xmx1g this can accumulate hundreds of MB.
        IntBuffer buffer = MemoryUtil.memAllocInt(indices.length);
        try {
            buffer.put(indices).flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        return vboId;
    }

    /** Uploads a float attribute buffer and returns its VBO id. */
    private int storeDataInAttributeList(int attributeNr, int size, float[] data) {
        int vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
        try {
            buffer.put(data).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
            GL20.glVertexAttribPointer(attributeNr, size, GL11.GL_FLOAT, false, 0, 0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        return vboId;
    }

    /** No-op — individual Model/TexturedModel instances handle their own disposal. */
    public void exit() { }
}
