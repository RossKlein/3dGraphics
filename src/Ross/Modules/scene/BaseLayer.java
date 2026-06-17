package Ross.Modules.scene;

import Ross.Modules.Job;
import Ross.Modules.JobModule;

import java.util.Collections;
import java.util.List;

/**
 * Convenience base class for {@link Layer} implementations.
 *
 * Provides default (no-op) implementations of every lifecycle method and
 * handles state storage, so concrete layers only need to override what they
 * actually use.
 *
 * Example:
 * <pre>{@code
 * public class HudLayer extends BaseLayer {
 *     public HudLayer(GameState state) { this.state = state; }
 *
 *     @Override public Group  getGroup() { return Group.SCREEN; }
 *     @Override public int    getOrder() { return 0; }
 *     @Override public String getName()  { return "hud"; }
 *
 *     @Override
 *     public List<Job> render(JobModule jobs, RenderTarget target, double dt) {
 *         // draw HUD using target.getWidth() / target.getHeight()
 *         return Collections.emptyList();
 *     }
 * }
 * }</pre>
 */
public abstract class BaseLayer implements Layer {

    private State layerState = State.ACTIVE;

    @Override public State getState()          { return layerState; }
    @Override public void  setState(State s)   { layerState = s; }

    @Override public List<Job> onPush(JobModule jobs) { return Collections.emptyList(); }
    @Override public List<Job> onPop(JobModule jobs)  { return Collections.emptyList(); }
    @Override public List<Job> update(JobModule jobs)  { return Collections.emptyList(); }

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double deltaTime) {
        return Collections.emptyList();
    }
}
