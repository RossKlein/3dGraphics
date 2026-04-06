package Ross.Modules.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    // ---- Event callbacks (UI → Game) ---------------------------------------

    /**
     * Set the single callback that receives all UI events.
     * Must be called before any {@link #addEventListener} calls.
     *
     * <pre>{@code
     * rmlUi.setEventCallback((elemId, eventType, value) -> {
     *     if (elemId.equals("start-btn") && eventType.equals("click"))
     *         startGame();
     * });
     * }</pre>
     *
     * Pass {@code null} to clear a previously set callback.
     */
    public void setEventCallback(RmlEventListener listener) {
        nSetEventCallback(listener);
    }

    /**
     * Attach an event listener to a specific element inside a document.
     * The callback set via {@link #setEventCallback} will be invoked when
     * the event fires.
     *
     * @param documentHandle  handle returned by {@link #loadDocument}
     * @param elementId       value of the id="" attribute in the .rml file
     * @param eventType       RmlUi event string: "click", "change", "submit", …
     */
    public void addEventListener(long documentHandle, String elementId, String eventType) {
        nAddEventListener(documentHandle, elementId, eventType);
    }

    // ---- Data models (Game → UI) -------------------------------------------

    /**
     * Declare a named data model and register its variables with RmlUi.
     * Call once after the context is initialised but before loading documents.
     *
     * In your .rml template use {@code data-model="modelName"} on the root
     * element, then reference variables with double-braces:
     * <pre>{@code
     * <div data-model="hud">
     *   <span>{{speed}} m/s  alt {{altitude}} m</span>
     *   <span>{{biome}}</span>
     * </div>
     * }</pre>
     *
     * Then each update frame push new values:
     * <pre>{@code
     * rmlUi.setModelFloat("hud", "speed",    bird.getSpeed());
     * rmlUi.setModelFloat("hud", "altitude", bird.getAltitude());
     * rmlUi.setModelString("hud", "biome",   world.getCurrentBiome());
     * }</pre>
     *
     * @param modelName   name matching data-model="…" in the .rml file
     * @param floatVars   variable names bound as numeric (float/double) values
     * @param stringVars  variable names bound as text values
     */
    public void createDataModel(String modelName, String[] floatVars, String[] stringVars) {
        nCreateDataModel(modelName, floatVars, stringVars);
    }

    /**
     * Update a numeric variable in a data model and mark it dirty.
     * RmlUi will re-render any template expressions referencing this variable
     * on the next {@link #update()} call.
     */
    public void setModelFloat(String modelName, String varName, float value) {
        nSetModelFloat(modelName, varName, value);
    }

    /**
     * Update a string variable in a data model and mark it dirty.
     * RmlUi will re-render any template expressions referencing this variable
     * on the next {@link #update()} call.
     */
    public void setModelString(String modelName, String varName, String value) {
        nSetModelString(modelName, varName, value);
    }

    // ---- Textures ----------------------------------------------------------

    /**
     * Pre-register a GL texture so RmlUi can reference it by path in .rml files.
     * Call before loading any document that contains image elements.
     *
     *   rmlUi.registerTexture("res/ui/logo.png", glTexId);
     *   rmlUi.loadDocument("res/ui/menu.rml");
     */
    public void registerTexture(String path, int glTextureId) {
        nRegisterTexture(path, glTextureId);
    }

    /**
     * Called from C++ (GenerateTexture) to create a GL texture for font atlases.
     * RGBA8 pixel data, width x height pixels.
     * Must run on the GL thread — guaranteed because nLoadFont triggers this
     * synchronously on whichever thread called it.
     */
    @SuppressWarnings("unused") // called via JNI
    private int nGenerateTextureCallback(byte[] pixels, int width, int height) {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8,
                     width, height, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE,
                     java.nio.ByteBuffer.wrap(pixels));
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
        return texId;
    }

    // ---- Input (pass-through to C++) ---------------------------------------

    public void mouseMove(int x, int y, int mods)      { nMouseMove(x, y, mods); }
    public void mouseDown(int button, int mods)         { nMouseButtonDown(button, mods); }
    public void mouseUp(int button, int mods)           { nMouseButtonUp(button, mods); }
    public void mouseScroll(float dx, float dy, int m)  { nMouseScroll(dx, dy, m); }
    public void textInput(int codepoint)                { nTextInput(codepoint); }

    /** Translate a GLFW key code to an RmlUi key ID and forward the event. */
    public void keyDown(int glfwKey, int glfwMods) { nKeyDown(glfwToRml(glfwKey), glfwModsToRml(glfwMods)); }
    public void keyUp(int glfwKey, int glfwMods)   { nKeyUp(glfwToRml(glfwKey),   glfwModsToRml(glfwMods)); }

    // ---- GLFW → RmlUi key translation -------------------------------------
    // RmlUi key IDs from Include/RmlUi/Core/Input.h
    // GLFW key codes from org.lwjgl.glfw.GLFW

    private static int glfwModsToRml(int glfwMods) {
        int rml = 0;
        if ((glfwMods & 0x0001) != 0) rml |= 2;  // SHIFT   → KM_SHIFT
        if ((glfwMods & 0x0002) != 0) rml |= 1;  // CONTROL → KM_CTRL
        if ((glfwMods & 0x0004) != 0) rml |= 4;  // ALT     → KM_ALT
        if ((glfwMods & 0x0008) != 0) rml |= 8;  // SUPER   → KM_META
        return rml;
    }

    private static int glfwToRml(int k) {
        return switch (k) {
            case 32  -> 1;   // SPACE
            // 0-9: GLFW 48-57 → RmlUi 2-11
            case 48  -> 2;  case 49  -> 3;  case 50  -> 4;  case 51  -> 5;
            case 52  -> 6;  case 53  -> 7;  case 54  -> 8;  case 55  -> 9;
            case 56  -> 10; case 57  -> 11;
            // A-Z: GLFW 65-90 → RmlUi 12-37
            case 65  -> 12; case 66  -> 13; case 67  -> 14; case 68  -> 15;
            case 69  -> 16; case 70  -> 17; case 71  -> 18; case 72  -> 19;
            case 73  -> 20; case 74  -> 21; case 75  -> 22; case 76  -> 23;
            case 77  -> 24; case 78  -> 25; case 79  -> 26; case 80  -> 27;
            case 81  -> 28; case 82  -> 29; case 83  -> 30; case 84  -> 31;
            case 85  -> 32; case 86  -> 33; case 87  -> 34; case 88  -> 35;
            case 89  -> 36; case 90  -> 37;
            // Navigation
            case 256 -> 81;  // ESCAPE     → KI_ESCAPE
            case 257 -> 72;  // ENTER      → KI_RETURN
            case 258 -> 70;  // TAB        → KI_TAB
            case 259 -> 69;  // BACKSPACE  → KI_BACK
            case 260 -> 98;  // INSERT     → KI_INSERT
            case 261 -> 99;  // DELETE     → KI_DELETE
            case 262 -> 92;  // RIGHT      → KI_RIGHT
            case 263 -> 90;  // LEFT       → KI_LEFT
            case 264 -> 93;  // DOWN       → KI_DOWN
            case 265 -> 91;  // UP         → KI_UP
            case 266 -> 86;  // PAGE_UP    → KI_PRIOR
            case 267 -> 87;  // PAGE_DOWN  → KI_NEXT
            case 268 -> 89;  // HOME       → KI_HOME
            case 269 -> 88;  // END        → KI_END
            // F1-F12: GLFW 290-301 → RmlUi 107-118
            case 290 -> 107; case 291 -> 108; case 292 -> 109; case 293 -> 110;
            case 294 -> 111; case 295 -> 112; case 296 -> 113; case 297 -> 114;
            case 298 -> 115; case 299 -> 116; case 300 -> 117; case 301 -> 118;
            // Modifiers
            case 340 -> 138; // LEFT_SHIFT    → KI_LSHIFT
            case 341 -> 140; // LEFT_CONTROL  → KI_LCONTROL
            case 342 -> 142; // LEFT_ALT      → KI_LMENU
            case 344 -> 139; // RIGHT_SHIFT   → KI_RSHIFT
            case 345 -> 141; // RIGHT_CONTROL → KI_RCONTROL
            case 346 -> 143; // RIGHT_ALT     → KI_RMENU
            default  -> 0;   // KI_UNKNOWN
        };
    }

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
        // RmlUi uses premultiplied alpha — correct blend equation is ONE / (1 - src_alpha)
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
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
    private native void    nRegisterTexture(String path, int glTextureId);

    // Event callbacks
    private native void    nSetEventCallback(RmlEventListener listener);
    private native void    nAddEventListener(long docHandle, String elementId, String eventType);

    // Data models
    private native void    nCreateDataModel(String modelName, String[] floatVars, String[] stringVars);
    private native void    nSetModelFloat(String modelName, String varName, float value);
    private native void    nSetModelString(String modelName, String varName, String value);
}
