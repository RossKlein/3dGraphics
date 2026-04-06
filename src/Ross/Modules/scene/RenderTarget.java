package Ross.Modules.scene;

/**
 * Abstraction over a render destination — either an offscreen {@link Framebuffer}
 * or the {@link ScreenTarget} (default framebuffer 0).
 *
 * LayerManager binds the appropriate target before calling each group of layers.
 * Layers receive the target so they can query its dimensions for projection matrices.
 */
public interface RenderTarget {
    /** Bind this framebuffer as the current GL render target. */
    void bind();

    /** Unbind (restores default framebuffer 0). */
    void unbind();

    int getWidth();
    int getHeight();
}
