package Ross.Modules.scene;

import static org.lwjgl.opengl.GL33.*;

/**
 * A unit fullscreen quad covering NDC [-1,1]x[-1,1].
 *
 * Shared by any pass that needs to rasterise a fullscreen rectangle
 * (sky background, post-process effects, etc.) without each one
 * duplicating VAO/VBO/EBO setup.
 *
 * Usage:
 * <pre>{@code
 * FullscreenQuad quad = new FullscreenQuad(); // call on GL thread
 * quad.draw();                                // inside render loop
 * quad.dispose();                             // on cleanup
 * }</pre>
 *
 * Attribute layout: location 0 — vec2 aPos (NDC position).
 */
public class FullscreenQuad {

    private final int vao;
    private final int vbo;
    private final int ebo;

    public FullscreenQuad() {
        float[] verts = { -1f, -1f,   1f, -1f,   1f,  1f,   -1f,  1f };
        int[]   idx   = { 0, 1, 2,   2, 3, 0 };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0L);
        glBindVertexArray(0);
    }

    /** Issues the draw call. The caller is responsible for binding a shader beforehand. */
    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
    }

    public void dispose() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
