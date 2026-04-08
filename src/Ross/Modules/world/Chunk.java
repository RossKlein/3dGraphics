package Ross.Modules.world;

import Ross.Modules.CancelToken;

import java.util.Arrays;

/**
 * One terrain tile — the core unit of the chunk streaming system.
 *
 * <h3>State machine</h3>
 * <pre>
 *
 *  UNLOADED ──────────────────────────────────────────────────────────
 *     │  enters load radius
 *     ▼
 *  QUEUED ─── generate job dispatched
 *     │
 *     ▼
 *  GENERATING ─── worker thread building heightmap + mesh
 *     │  success                            │  error / out of range
 *     ▼                                     ▼
 *  PENDING_UPLOAD ─── GL upload queued   CANCELLED ── evicted next tick
 *     │  success
 *     ▼
 *  LOADED ──────────────────────────────────────────── rendering
 *     │  LOD mismatch detected                │  exits unload radius
 *     ▼                                        ▼
 *  LOD_SWITCHING ─── still renders old      dispose job ── UNLOADED
 *     │  LOD; background re-generate          │
 *     │  in progress (ACID — no preemption)   │
 *     │  new slot ready                        │
 *     ▼                                        │
 *  LOADED (new uploadedLod) ←──────────────────┘
 *
 * </pre>
 *
 * <h3>Key invariants</h3>
 * <ul>
 *   <li>LOADED and LOD_SWITCHING are both renderable ({@link #isRenderable()}).
 *   <li>At most ONE pool slot is allocated per chunk in steady state.
 *       During LOD_SWITCHING a second slot may be briefly held (new LOD being
 *       uploaded) before the old one is freed.
 *   <li>A LOD_SWITCHING chunk will not start another switch until the current
 *       one completes (ACID ordering).
 *   <li>All pool slot management happens on the GL thread.
 * </ul>
 *
 * <h3>Thread safety</h3>
 * {@link #state} is {@code volatile}.  All GL-object fields ({@link #lodSlots},
 * {@link #uploadedLod}) are written exclusively on the GL thread.
 */
public final class Chunk {

    // ---- State machine -------------------------------------------------------

    public enum State {
        /** Not yet in the chunk map or recently evicted. */
        UNLOADED,
        /** Scheduled; waiting for a worker thread to pick it up. */
        QUEUED,
        /** Worker thread is generating the heightmap and mesh data. */
        GENERATING,
        /** Mesh data ready; waiting for the GL thread to upload it. */
        PENDING_UPLOAD,
        /** GPU slot allocated; rendering at {@link #uploadedLod}. */
        LOADED,
        /**
         * Still rendering at {@link #uploadedLod} (old LOD), while a background
         * re-generate is underway for {@link #pendingLod} (new LOD).
         * Once the new slot is ready, slots are swapped atomically on the GL
         * thread and state returns to LOADED.  No further switch is started
         * until this one completes (ACID ordering).
         */
        LOD_SWITCHING,
        /** Generate or upload failed; will be evicted and re-queued next tick. */
        CANCELLED
    }

    public volatile State state = State.UNLOADED;

    // ---- Identity ------------------------------------------------------------

    public final ChunkCoord coord;

    public Chunk(ChunkCoord coord) {
        this.coord = coord;
    }

    // ---- Cancellation --------------------------------------------------------

    /**
     * Controls the initial generate + upload pipeline.
     * Replaced each time the chunk is re-queued.
     */
    public volatile CancelToken cancelToken = new CancelToken();

    /**
     * Controls the LOD-switch generate + upload pipeline.
     * Non-null only while {@link #state} == {@link State#LOD_SWITCHING}.
     * Cancelled when the chunk is evicted mid-switch.
     */
    public volatile CancelToken switchToken = null;

    // ---- CPU data (worker thread writes, GL thread consumes) -----------------

    /** Full-resolution heightmap; freed after GL upload to save RAM. */
    public float[][] heightmap;

    /**
     * CPU-side mesh arrays for each LOD level.  Only indices in
     * [{@code lodLo}, {@code lodHi}] are populated by a given generate pass.
     * Nulled immediately after the GL upload job consumes them.
     */
    public MeshData[] meshData;

    // ---- GPU data (GL thread writes and reads) --------------------------------

    /**
     * Pool slot index for each LOD level.  -1 = not allocated.
     * In steady state exactly one entry is non-(-1): {@code lodSlots[uploadedLod]}.
     * During LOD_SWITCHING a second entry may briefly be non-(-1) while the
     * atomic swap is in progress.
     */
    public final int[] lodSlots = new int[World.LOD_COUNT];

    /**
     * Which LOD level is currently in the GPU pool (-1 = none).
     * Written on the GL thread; read on the render path.
     */
    public volatile int uploadedLod = -1;

    /**
     * The LOD level being generated during {@link State#LOD_SWITCHING}.
     * -1 when not switching.
     */
    public volatile int pendingLod = -1;

    // ---- Misc ----------------------------------------------------------------

    public volatile boolean dirty  = false;
    public volatile int  activeLod = -1;

    // ---- Constructor setup ---------------------------------------------------

    { Arrays.fill(lodSlots, -1); }

    // ---- Helpers -------------------------------------------------------------

    /** True while this chunk has a GPU slot ready to draw. */
    public boolean isRenderable() {
        return state == State.LOADED || state == State.LOD_SWITCHING;
    }

    /**
     * Cancels the initial generate/upload pipeline and marks as CANCELLED.
     * Safe to call from any thread.
     */
    public void cancel() {
        cancelToken.cancel();
        state = State.CANCELLED;
    }

    /**
     * Cancels a pending LOD switch without affecting the currently rendered LOD.
     * No-op if no switch is in progress.  Safe to call from any thread.
     */
    public void cancelSwitch() {
        CancelToken t = switchToken;
        if (t != null) t.cancel();
    }

    /**
     * Resets all CPU-side data and state to UNLOADED.
     * GPU pool slots must be freed separately (on the GL thread) before this call.
     */
    public void dispose() {
        meshData    = null;
        heightmap   = null;
        activeLod   = -1;
        uploadedLod = -1;
        pendingLod  = -1;
        switchToken = null;
        Arrays.fill(lodSlots, -1);
        state = State.UNLOADED;
    }

    @Override
    public String toString() {
        return "Chunk[" + coord + " " + state + " lod=" + uploadedLod + "]";
    }
}
