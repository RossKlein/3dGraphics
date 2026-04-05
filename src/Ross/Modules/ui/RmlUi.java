package Ross.Modules.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * Java wrapper for the RmlUi JNI bridge.
 *
 * Architecture:
 *   C++ writes geometry into direct ByteBuffers (no GL calls).
 *   Java reads those buffers, uploads VAOs once per geometry, and
 *   issues draw calls each frame — keeping all GL on the render thread.
 *
 * Buffer protocol (defined in rmlui_java.h):
 *   commandBuf     — RmlCommand[MAX_COMMANDS] (20 bytes each)
 *   geometryBuf    — packed pending uploads (GeometryHeader + vertices + indices)
 *   controlBufs    — small int[1] buffers for counts / cursors
 */
public class RmlUi {

    // Matches rmlui_java.h
    private static final int MAX_COMMANDS      = 4096;
    private static final int CMD_SIZE          = 20;      // sizeof(RmlCommand)
    private static final int GEOM_BUFFER_BYTES = 4 * 1024 * 1024;
    private static final int MAX_RELEASES      = 256;
    private static final int VERTEX_STRIDE     = 20;      // sizeof(Rml::Vertex)

    // CommandType values (must match enum in rmlui_java.h)
    private static final int CMD_DRAW        = 0;
    private static final int CMD_SCISSOR_ON  = 1;
    private static final int CMD_SCISSOR_OFF = 2;

    // ---- Shared ByteBuffers (direct = accessible from C++ JNI) ------------
    private final ByteBuffer commandBuf;     // RmlCommand[]
    private final ByteBuffer geometryBuf;    // pending geometry uploads
    private final ByteBuffer commandCount;   // int[1]
    private final ByteBuffer geomBufOffset;  // int[1]  write cursor
    private final ByteBuffer geomPending;    // int[1]  how many geometries pending
    private final ByteBuffer releaseBuf;     // int[MAX_RELEASES]
    private final ByteBuffer releaseCount;   // int[1]

    // ---- GPU resources -----------------------------------------------------
    // RmlUi geometry ID → [vaoId, vboId, eboId, indexCount]
    private final Map<Integer, int[]> geometryCache = new HashMap<>();

    private int shaderProgram;
    private int uProjection;
    private int uTranslation;
    private int uTexture;
    private int uHasTexture;

    private int screenWidth;
    private int screenHeight;

    // -----------------------------------------------------------------------

    static {
        System.loadLibrary("rmlui_java");
    }

    public RmlUi(int width, int height) {
        this.screenWidth  = width;
        this.screenHeight = height;

        commandBuf    = direct(MAX_COMMANDS * CMD_SIZE);
        geometryBuf   = direct(GEOM_BUFFER_BYTES);
        commandCount  = direct(4);
        geomBufOffset = direct(4);
        geomPending   = direct(4);
        releaseBuf    = direct(MAX_RELEASES * 4);
        releaseCount  = direct(4);

        nInit(width, height,
              commandBuf, geometryBuf,
              commandCount, geomBufOffset, geomPending,
              releaseBuf, releaseCount);

        buildShader();
    }

    // ---- Public API --------------------------------------------------------

    public long loadDocument(String path)            { return nLoadDocument(path); }
    public long loadDocumentFromMemory(String rml)   { return nLoadDocumentFromMemory(rml, "memory://ui"); }
    public void showDocument(long handle)            { nShowDocument(handle); }
    public void hideDocument(long handle)            { nHideDocument(handle); }
    public void closeDocument(long handle)           { nCloseDocument(handle); }
    public boolean loadFont(String path)             { return nLoadFont(path); }

    public void setDimensions(int w, int h) {
        screenWidth  = w;
        screenHeight = h;
        nSetDimensions(w, h);
        updateProjection();
    }

    /** Call once per update frame (runs layout). */
    public void update() { nUpdate(); }

    /**
     * Call once per render frame (on GL thread).
     * Calls C++ context.Render(), then uploads any new geometry and
     * processes the draw command list.
     */
    public void render() {
        nRender();                  // C++ fills commandBuf + geometryBuf
        uploadPendingGeometry();    // create VAOs for new geometry
        freeReleasedGeometry();     // delete VAOs C++ flagged for release
        processDrawCommands();      // issue GL draw calls
    }

    public void shutdown() { nShutdown(); }

    // ---- Input (pass-through to C++) ---------------------------------------

    public void mouseMove(int x, int y, int mods)      { nMouseMove(x, y, mods); }
    public void mouseDown(int button, int mods)         { nMouseButtonDown(button, mods); }
    public void mouseUp(int button, int mods)           { nMouseButtonUp(button, mods); }
    public void mouseScroll(float dx, float dy, int m)  { nMouseScroll(dx, dy, m); }
    public void keyDown(int rmlKey, int mods)           { nKeyDown(rmlKey, mods); }
    public void keyUp(int rmlKey, int mods)             { nKeyUp(rmlKey, mods); }
    public void textInput(int codepoint)                { nTextInput(codepoint); }

    // ---- Geometry upload ---------------------------------------------------

    private void uploadPendingGeometry() {
        int pending = geomPending.getInt(0);
        if (pending == 0) return;

        geometryBuf.rewind();

        for (int i = 0; i < pending; i++) {
            // Read GeometryHeader (12 bytes: geometryId, vertexCount, indexCount)
            int geomId      = geometryBuf.getInt();
            int vertexCount = geometryBuf.getInt();
            int indexCount  = geometryBuf.getInt();

            int vertexBytes = vertexCount * VERTEX_STRIDE;
            int indexBytes  = indexCount  * 4;

            // Slice views into the buffer for GL upload
            ByteBuffer vertexSlice = geometryBuf.slice().limit(vertexBytes);
            geometryBuf.position(geometryBuf.position() + vertexBytes);

            ByteBuffer indexSlice = geometryBuf.slice().limit(indexBytes);
            geometryBuf.position(geometryBuf.position() + indexBytes);

            // Create VAO
            int vao = glGenVertexArrays();
            int vbo = glGenBuffers();
            int ebo = glGenBuffers();

            glBindVertexArray(vao);

            // Upload vertices
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, vertexSlice, GL_STATIC_DRAW);

            // Upload indices
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexSlice, GL_STATIC_DRAW);

            // Attribute layout matches Rml::Vertex:
            //   offset  0: position  (vec2 float)
            //   offset  8: colour    (vec4 ubyte normalized)
            //   offset 12: tex_coord (vec2 float)
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 2, GL_FLOAT,         false, VERTEX_STRIDE, 0L);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true,  VERTEX_STRIDE, 8L);
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(2, 2, GL_FLOAT,         false, VERTEX_STRIDE, 12L);

            glBindVertexArray(0);

            geometryCache.put(geomId, new int[]{vao, vbo, ebo, indexCount});
        }

        // Tell C++ the buffer has been consumed — reset both count and offset
        geomPending.putInt(0, 0);
        geomBufOffset.putInt(0, 0);
    }

    // ---- Release geometry --------------------------------------------------

    private void freeReleasedGeometry() {
        int count = releaseCount.getInt(0);
        if (count == 0) return;

        for (int i = 0; i < count; i++) {
            int id = releaseBuf.getInt(i * 4);
            int[] ids = geometryCache.remove(id);
            if (ids != null) {
                glDeleteVertexArrays(ids[0]);
                glDeleteBuffers(ids[1]);
                glDeleteBuffers(ids[2]);
            }
        }
        releaseCount.putInt(0, 0);
    }

    // ---- Draw commands -----------------------------------------------------

    private void processDrawCommands() {
        int count = commandCount.getInt(0);
        if (count == 0) return;

        glUseProgram(shaderProgram);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        for (int i = 0; i < count; i++) {
            int base = i * CMD_SIZE;
            int type = commandBuf.getInt(base);

            switch (type) {
                case CMD_DRAW -> {
                    // d[0]=geometryId(float bits=int), d[1]=tx, d[2]=ty, d[3]=textureHandle
                    int   geomId  = Float.floatToRawIntBits(commandBuf.getFloat(base + 4));
                    float tx      = commandBuf.getFloat(base + 8);
                    float ty      = commandBuf.getFloat(base + 12);
                    int   texId   = Float.floatToRawIntBits(commandBuf.getFloat(base + 16));

                    int[] ids = geometryCache.get(geomId);
                    if (ids == null) continue;

                    glUniform2f(uTranslation, tx, ty);
                    if (texId != 0) {
                        glActiveTexture(GL_TEXTURE0);
                        glBindTexture(GL_TEXTURE_2D, texId);
                        glUniform1i(uHasTexture, 1);
                    } else {
                        glUniform1i(uHasTexture, 0);
                    }

                    glBindVertexArray(ids[0]);
                    glDrawElements(GL_TRIANGLES, ids[3], GL_UNSIGNED_INT, 0L);
                    glBindVertexArray(0);
                }
                case CMD_SCISSOR_ON -> {
                    int x = Float.floatToRawIntBits(commandBuf.getFloat(base + 4));
                    int y = Float.floatToRawIntBits(commandBuf.getFloat(base + 8));
                    int w = Float.floatToRawIntBits(commandBuf.getFloat(base + 12));
                    int h = Float.floatToRawIntBits(commandBuf.getFloat(base + 16));
                    glEnable(GL_SCISSOR_TEST);
                    glScissor(x, screenHeight - y - h, w, h); // flip Y for GL
                }
                case CMD_SCISSOR_OFF -> glDisable(GL_SCISSOR_TEST);
            }
        }

        glDisable(GL_SCISSOR_TEST);
        glEnable(GL_DEPTH_TEST);
        glUseProgram(0);
    }

    // ---- Shader ------------------------------------------------------------

    private void buildShader() {
        String vert = """
            #version 330 core
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec4 aColour;
            layout(location = 2) in vec2 aTexCoord;

            uniform mat4 uProjection;
            uniform vec2 uTranslation;

            out vec4 vColour;
            out vec2 vTexCoord;

            void main() {
                gl_Position = uProjection * vec4(aPosition + uTranslation, 0.0, 1.0);
                vColour   = aColour;
                vTexCoord = aTexCoord;
            }
            """;

        String frag = """
            #version 330 core
            in vec4 vColour;
            in vec2 vTexCoord;
            out vec4 fragColor;

            uniform sampler2D uTexture;
            uniform bool      uHasTexture;

            void main() {
                if (uHasTexture)
                    fragColor = vColour * texture(uTexture, vTexCoord);
                else
                    fragColor = vColour;
            }
            """;

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vert);
        glCompileShader(vs);

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, frag);
        glCompileShader(fs);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vs);
        glAttachShader(shaderProgram, fs);
        glLinkProgram(shaderProgram);
        glDeleteShader(vs);
        glDeleteShader(fs);

        uProjection  = glGetUniformLocation(shaderProgram, "uProjection");
        uTranslation = glGetUniformLocation(shaderProgram, "uTranslation");
        uTexture     = glGetUniformLocation(shaderProgram, "uTexture");
        uHasTexture  = glGetUniformLocation(shaderProgram, "uHasTexture");

        glUseProgram(shaderProgram);
        glUniform1i(uTexture, 0);
        glUseProgram(0);

        updateProjection();
    }

    private void updateProjection() {
        // Orthographic: (0,0) top-left, (w,h) bottom-right
        float w = screenWidth, h = screenHeight;
        float[] p = {
             2f/w,  0,     0, -1,
             0,    -2f/h,  0,  1,
             0,     0,    -1,  0,
             0,     0,     0,  1
        };
        glUseProgram(shaderProgram);
        glUniformMatrix4fv(uProjection, true, p);
        glUseProgram(0);
    }

    // ---- Utilities ---------------------------------------------------------

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    // ---- JNI declarations --------------------------------------------------

    private native void    nInit(int w, int h,
                                 ByteBuffer cmds,    ByteBuffer geom,
                                 ByteBuffer cmdCount, ByteBuffer geomOffset,
                                 ByteBuffer geomPending,
                                 ByteBuffer relBuf,  ByteBuffer relCount);
    private native void    nShutdown();
    private native void    nUpdate();
    private native void    nRender();
    private native long    nLoadDocument(String path);
    private native long    nLoadDocumentFromMemory(String rml, String sourceUrl);
    private native void    nShowDocument(long handle);
    private native void    nHideDocument(long handle);
    private native void    nCloseDocument(long handle);
    private native boolean nLoadFont(String path);
    private native void    nSetDimensions(int w, int h);
    private native void    nMouseMove(int x, int y, int mods);
    private native void    nMouseButtonDown(int button, int mods);
    private native void    nMouseButtonUp(int button, int mods);
    private native void    nMouseScroll(float dx, float dy, int mods);
    private native void    nKeyDown(int rmlKey, int mods);
    private native void    nKeyUp(int rmlKey, int mods);
    private native void    nTextInput(int codepoint);
}
