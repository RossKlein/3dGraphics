package Ross.Modules.world;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Pre-allocated, shared GPU buffer pool for one LOD level.
 *
 * <p>All chunks at the same LOD share a single VAO, which lets
 * {@code glMultiDrawElementsIndirect} draw every visible chunk in one call
 * instead of thousands of individual {@code glDrawElements} calls.</p>
 *
 * <h3>Slot model</h3>
 * The VBOs are divided into fixed-size "slots", one slot per chunk.
 * Each slot is exactly {@code vertsPerSlot} vertices wide and
 * {@code indicesPerSlot} indices wide.  A stack-based free-list gives O(1)
 * {@link #alloc()} and {@link #free(int)}.
 *
 * <h3>Per-frame MDI</h3>
 * Call {@link #multiDraw} once per frame with the draw-command array and the
 * per-chunk world offsets.  The pool uploads both to the GPU and issues a
 * single {@code glMultiDrawElementsIndirect} call.
 * The vertex shader reads per-chunk offsets from an SSBO at binding 0,
 * indexed by {@code gl_DrawID}.
 *
 * <p>Must be created and destroyed on the GL thread.</p>
 */
public class TerrainVboPool {

    // ---- GL handles -------------------------------------------------------

    private final int vao;
    private final int vboPosition;   // attribute 0 – vec3 xyz
    private final int vboColor;      // attribute 1 – vec4 rgba
    private final int vboNormal;     // attribute 2 – vec3 normal
    private final int ebo;           // element (index) buffer

    /** GL_DRAW_INDIRECT_BUFFER — rebuilt each frame. */
    private final int drawCmdBuf;

    /** SSBO at binding 0 — per-draw chunk world offsets, rebuilt each frame. */
    private final int ssbo;

    // ---- Slot geometry ----------------------------------------------------

    public final int vertsPerSlot;
    public final int indicesPerSlot;
    public final int capacity;

    // ---- Free-list --------------------------------------------------------

    private final int[] freeStack;
    private int freeTop;

    // -----------------------------------------------------------------------

    /**
     * Allocates all GPU buffers upfront.  Must be called on the GL thread.
     *
     * @param capacity       maximum chunks this pool can hold simultaneously
     * @param vertsPerSlot   vertices in one chunk mesh at this LOD
     * @param indicesPerSlot indices in one chunk mesh at this LOD
     */
    public TerrainVboPool(int capacity, int vertsPerSlot, int indicesPerSlot) {
        this.capacity        = capacity;
        this.vertsPerSlot    = vertsPerSlot;
        this.indicesPerSlot  = indicesPerSlot;

        freeStack = new int[capacity];
        for (int i = 0; i < capacity; i++) freeStack[freeTop++] = i;

        long totalV = (long) capacity * vertsPerSlot;
        long totalI = (long) capacity * indicesPerSlot;

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        // Position (attribute 0, vec3)
        vboPosition = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboPosition);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, totalV * 3L * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);

        // Color (attribute 1, vec4)
        vboColor = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboColor);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, totalV * 4L * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(1);

        // Normal (attribute 2, vec3)
        vboNormal = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboNormal);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, totalV * 3L * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL20.glVertexAttribPointer(2, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(2);

        // Index buffer
        ebo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, totalI * Integer.BYTES, GL15.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(0);

        // Per-frame buffers (not part of VAO)
        drawCmdBuf = GL15.glGenBuffers();
        ssbo       = GL15.glGenBuffers();
    }

    /** Allocates a slot from the free-list. Returns -1 if the pool is full. */
    public int alloc() {
        if (freeTop == 0) return -1;
        return freeStack[--freeTop];
    }

    /** Returns a slot to the free-list. */
    public void free(int slot) {
        freeStack[freeTop++] = slot;
    }

    /**
     * Uploads one chunk's mesh data into the given slot.
     * Must be called on the GL thread.
     *
     * <p>Binds this pool's VAO so the EBO sub-data write is scoped correctly —
     * in OpenGL, {@code GL_ELEMENT_ARRAY_BUFFER} is part of VAO state, and
     * binding it while a different VAO is active would corrupt that VAO.</p>
     */
    public void upload(int slot, float[] positions, float[] colors, float[] normals, int[] indices) {
        long vOff = (long) slot * vertsPerSlot;
        long iOff = (long) slot * indicesPerSlot;

        FloatBuffer fBuf;
        IntBuffer   iBuf;

        // Attribute VBOs (GL_ARRAY_BUFFER is not VAO state — safe without binding)
        fBuf = MemoryUtil.memAllocFloat(positions.length);
        try {
            fBuf.put(positions).flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboPosition);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, vOff * 3L * Float.BYTES, fBuf);
        } finally { MemoryUtil.memFree(fBuf); }

        fBuf = MemoryUtil.memAllocFloat(colors.length);
        try {
            fBuf.put(colors).flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboColor);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, vOff * 4L * Float.BYTES, fBuf);
        } finally { MemoryUtil.memFree(fBuf); }

        fBuf = MemoryUtil.memAllocFloat(normals.length);
        try {
            fBuf.put(normals).flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboNormal);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, vOff * 3L * Float.BYTES, fBuf);
        } finally { MemoryUtil.memFree(fBuf); }

        // Index buffer — must bind this pool's VAO so the write goes to our EBO,
        // not to whatever VAO the caller happens to have bound.
        GL30.glBindVertexArray(vao);
        iBuf = MemoryUtil.memAllocInt(indices.length);
        try {
            iBuf.put(indices).flip();
            GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, iOff * (long) Integer.BYTES, iBuf);
        } finally { MemoryUtil.memFree(iBuf); }
        GL30.glBindVertexArray(0);
    }

    /**
     * Uploads draw commands + per-draw chunk offsets, then issues
     * {@code glMultiDrawElementsIndirect} for all {@code drawCount} draws.
     *
     * <p>Layout expected by the caller:</p>
     * <pre>
     *   commands[i*5 + 0] = indexCount
     *   commands[i*5 + 1] = 1            (instanceCount)
     *   commands[i*5 + 2] = firstIndex   (slot * indicesPerSlot)
     *   commands[i*5 + 3] = baseVertex   (slot * vertsPerSlot)
     *   commands[i*5 + 4] = 0            (baseInstance)
     *
     *   offsets[i*4 + 0..3] = {worldX + camX,  camY,  worldZ + camZ,  0}
     * </pre>
     *
     * The vertex shader reads {@code offsets[gl_DrawID]} from the SSBO at
     * binding 0.
     */
    public void multiDraw(int[] commands, float[] offsets, int drawCount) {
        if (drawCount == 0) return;

        // Upload draw-command structs
        IntBuffer cmdBuf = MemoryUtil.memAllocInt(commands.length);
        try {
            cmdBuf.put(commands, 0, drawCount * 5).flip();
            GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, drawCmdBuf);
            GL15.glBufferData(GL40.GL_DRAW_INDIRECT_BUFFER, cmdBuf, GL15.GL_STREAM_DRAW);
        } finally { MemoryUtil.memFree(cmdBuf); }

        // Upload per-draw chunk offsets to SSBO (binding 0)
        FloatBuffer offBuf = MemoryUtil.memAllocFloat(offsets.length);
        try {
            offBuf.put(offsets, 0, drawCount * 4).flip();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, offBuf, GL15.GL_STREAM_DRAW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
        } finally { MemoryUtil.memFree(offBuf); }

        GL30.glBindVertexArray(vao);
        GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0L, drawCount, 0);
        GL30.glBindVertexArray(0);
    }

    /** Frees all GL resources.  Must be called on the GL thread. */
    public void dispose() {
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vboPosition);
        GL15.glDeleteBuffers(vboColor);
        GL15.glDeleteBuffers(vboNormal);
        GL15.glDeleteBuffers(ebo);
        GL15.glDeleteBuffers(drawCmdBuf);
        GL15.glDeleteBuffers(ssbo);
    }
}
