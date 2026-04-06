package Ross.Modules.scene;

import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33.*;

/**
 * Offscreen OpenGL framebuffer with one RGBA8 color texture and a depth renderbuffer.
 *
 * The color texture is sampled by {@link PostProcessChain} and by any SCREEN-layer
 * shader that needs to read the world pass (e.g. a refraction or depth-of-field effect).
 *
 * Must be created on the GL thread.
 */
public class Framebuffer implements RenderTarget {

    private final int fboId;
    private final int colorTexture;
    private final int depthRenderbuffer;
    private final int width;
    private final int height;

    public Framebuffer(int width, int height) {
        this.width  = width;
        this.height = height;

        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        // Color attachment — sampled by post-process passes
        colorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, colorTexture, 0);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Depth attachment
        depthRenderbuffer = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthRenderbuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                                  GL_RENDERBUFFER, depthRenderbuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE)
            throw new RuntimeException("Framebuffer incomplete (" + width + "x" + height
                                       + "): status=0x" + Integer.toHexString(status));

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    @Override public void bind()   { glBindFramebuffer(GL_FRAMEBUFFER, fboId); }
    @Override public void unbind() { glBindFramebuffer(GL_FRAMEBUFFER, 0); }
    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    /** GL texture ID of the color attachment — pass to post-process shaders. */
    public int getColorTexture() { return colorTexture; }

    public void dispose() {
        glDeleteFramebuffers(fboId);
        glDeleteTextures(colorTexture);
        glDeleteRenderbuffers(depthRenderbuffer);
    }
}
