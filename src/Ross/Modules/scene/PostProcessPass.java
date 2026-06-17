package Ross.Modules.scene;

/**
 * One stage in the {@link PostProcessChain} post-processing pipeline.
 *
 * Each pass reads from an input texture and renders a fullscreen quad into
 * the output target using its own shader. Passes can be stacked in any order
 * and toggled at runtime.
 *
 * Planned passes (implemented in later milestones):
 *   - AtmosphericHazePass   — distance fog / aerial perspective
 *   - GodRaysPass           — radial blur from sun position
 *   - BloomPass             — threshold + gaussian blur + additive blend
 *   - ToneMappingPass       — Reinhard / ACES filmic (always last)
 *
 * Contract:
 *   - {@link PostProcessChain} binds the output framebuffer before calling run().
 *   - The pass is responsible for issuing one fullscreen quad draw call.
 *   - The pass must not change the depth test or blend state permanently.
 */
public interface PostProcessPass {

    /**
     * Run this pass.
     *
     * @param inputTexture  GL texture ID of the previous pass result (or world FBO color).
     * @param output        The already-bound output target (for dimension queries).
     *                      Null when this is the final pass writing to the screen.
     */
    void run(int inputTexture, RenderTarget output);

    /** Called when the window is resized — rebuild any size-dependent FBOs or uniforms. */
    void resize(int width, int height);

    void dispose();
}
