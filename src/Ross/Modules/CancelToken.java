package Ross.Modules;

/**
 * Lightweight cancellation handle for long-running jobs (e.g. chunk generation).
 *
 * Usage:
 * <pre>{@code
 * CancelToken token = new CancelToken();
 * Job gen = new Job() {
 *     public void code() {
 *         for (int lod = 0; lod < 5; lod++) {
 *             if (token.isCancelled()) return;
 *             meshData[lod] = ChunkMesh.build(heightmap, lod);
 *         }
 *     }
 * };
 * gen.cancelToken = token;
 * // later, when the chunk leaves load range:
 * token.cancel();
 * }</pre>
 *
 * The job still runs to completion on the worker thread — it just returns early
 * at each safe point. The result is discarded without being uploaded.
 */
public final class CancelToken {

    private volatile boolean cancelled = false;

    /** Signal that the associated job(s) should abort at their next safe point. */
    public void cancel() {
        cancelled = true;
    }

    /** Returns true once {@link #cancel()} has been called. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** Reset to un-cancelled state (use with care — only before re-submitting). */
    public void reset() {
        cancelled = false;
    }
}
