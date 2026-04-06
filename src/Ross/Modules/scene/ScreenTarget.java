package Ross.Modules.scene;

import static org.lwjgl.opengl.GL33.*;

/**
 * Represents the default OpenGL framebuffer (FBO 0 — the screen).
 *
 * Passed to SCREEN-group layers by {@link LayerManager} so they can read
 * the screen dimensions for orthographic projection without holding a
 * reference to the window directly.
 */
public class ScreenTarget implements RenderTarget {

    private int width;
    private int height;

    public ScreenTarget(int width, int height) {
        this.width  = width;
        this.height = height;
    }

    @Override public void bind()   { glBindFramebuffer(GL_FRAMEBUFFER, 0); }
    @Override public void unbind() { /* already on the default framebuffer */ }
    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    public void resize(int width, int height) {
        this.width  = width;
        this.height = height;
    }
}
