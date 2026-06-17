# v4 Job System

## What v3 Has

- `Job`: abstract unit of work with subtask dependency support (`addSubtask`, `postCode`)
- `JobQueue`: `ConcurrentLinkedQueue`, sorts jobs by previous frame duration (longest-first heuristic)
- `JobModule`: orchestrator with update/render queues, built-in `controls` and `matrixWork` jobs
- Work-stealing thread pool (4 threads default)
- `CountDownLatch` per frame — waits for all jobs to complete before next frame

This is solid. The sorting heuristic is smart. The subtask system is good.

## What's Missing for v4

### 1. Priority Tiers
Chunk generation jobs are not equal. A chunk 1 tile away matters more than one 12 tiles away. There's no way to express this — all jobs go into the same queue.

### 2. Cancellation
If the player flies fast, they can outrun chunk generation. Jobs for chunks that are now out of range should be cancelled rather than completing uselessly and uploading a mesh nobody will see.

### 3. Explicit Dependency Graph
Currently dependencies work within a single job's subtask tree, but there is no way to say "job B cannot start until job A completes" across unrelated jobs. For world streaming, the upload job for a chunk cannot run until the generation job has finished.

### 4. GL-Thread Pinning
`ModelBuilder.buildModel()` must run on the GL thread (OpenGL context is not shared). There is currently no way to designate a job as "must run on GL thread." Upload jobs run wherever the thread pool sends them, which will crash if it's not the GL thread.

---

## Proposed Extensions

### Priority

Add a `priority` field to `Job`:

```java
enum Priority {
    CRITICAL,   // flight physics, camera — must complete before render
    HIGH,       // visible near-field chunks, LOD updates
    NORMAL,     // load-ahead chunks
    LOW,        // prefetch chunks
    IDLE        // can be deferred to next frame if time budget exceeded
}
```

`JobQueue.frameCall()` already sorts jobs before submitting. Extend the sort key:

```
sort key = (priority tier first, then duration descending within tier)
```

CRITICAL jobs always go first regardless of duration. IDLE jobs go last and can be skipped if the frame is already over budget.

### Cancellation

Add a `CancelToken` to `Job`:

```java
class CancelToken {
    volatile boolean cancelled = false;
    void cancel() { cancelled = true; }
    boolean isCancelled() { return cancelled; }
}
```

Jobs check `cancel.isCancelled()` at safe points and return early. Callers keep a reference to the token and call `cancel()` when the job is no longer needed.

```java
// In World.update(), when a chunk exits load range:
chunk.cancelToken.cancel();
chunk.state = CANCELLED;
```

The job still runs to completion (the thread pool has already started it), but returns early doing minimal work.

### GL-Thread Pinned Jobs

Some work must happen on the GL thread (VAO/VBO creation, shader uniform binding). Add a `glThread` flag:

```java
abstract class Job {
    boolean glThread = false;   // if true, only the render thread may execute this
    // ...
}
```

`JobQueue` maintains a separate `glQueue` for GL-pinned jobs. The render thread drains this queue at the start of each render frame before calling `renderJobs()`. Worker threads never receive GL-pinned jobs.

```java
class JobQueue {
    ConcurrentLinkedQueue<Job> pendingJobs;
    ConcurrentLinkedQueue<Job> glJobs;      // render thread only
    // ...
}
```

### Cross-Job Dependencies

Currently, a `Job` can have subtasks (children). For v4 we also need to express that Job B depends on Job A completing, where they are in different trees.

Extend with a `dependsOn` list:

```java
abstract class Job {
    List<Job> dependsOn = new ArrayList<>();  // wait for all these before running

    boolean isReady() {
        return dependsOn.stream().allMatch(j -> j.state == COMPLETE);
    }
}
```

`JobQueue.frameCall()` separates jobs into ready and waiting. Waiting jobs are checked each time a job completes:

```java
void onJobComplete(Job completed) {
    waitingJobs.stream()
        .filter(Job::isReady)
        .forEach(j -> {
            waitingJobs.remove(j);
            submit(j);
        });
}
```

---

## Chunk Job Chain

With these extensions, the chunk lifecycle jobs compose cleanly:

```java
// When chunk enters load range:
CancelToken token = new CancelToken();
chunk.cancelToken = token;

ChunkGenerateJob gen = new ChunkGenerateJob(chunk, token);
gen.priority = Priority.HIGH;  // or NORMAL/LOW by distance

ChunkUploadJob upload = new ChunkUploadJob(chunk, token);
upload.glThread = true;
upload.dependsOn.add(gen);      // upload can't run until gen is done
upload.priority = Priority.HIGH;

jobModule.updateQueue.assign(gen);
jobModule.renderQueue.assign(upload);
```

The generation runs on a worker thread. When it completes and marks `chunk.state = PENDING_UPLOAD`, the dependency check fires and the upload job becomes ready. The render thread picks it up next frame and calls `ModelBuilder.buildModel()` safely on the GL context.

---

## Priority Assignment by Distance

`World.update()` runs each frame and re-evaluates priority for queued chunks:

```java
for (Chunk chunk : queuedChunks) {
    float dist = distanceToCamera(chunk);
    if (dist < 2)       chunk.generateJob.priority = Priority.CRITICAL;
    else if (dist < 5)  chunk.generateJob.priority = Priority.HIGH;
    else if (dist < 10) chunk.generateJob.priority = Priority.NORMAL;
    else                chunk.generateJob.priority = Priority.LOW;
}
```

Because `JobQueue.frameCall()` re-sorts every frame, priority changes take effect next frame without any additional machinery.

---

## Frame Budget for IDLE Jobs

IDLE-priority jobs represent prefetch work that is nice-to-have but should not cost frame time. The queue checks elapsed frame time before submitting IDLE jobs:

```java
// In frameCall():
long budget = targetFrameTime - (System.nanoTime() - frameStart);
if (budget > IDLE_THRESHOLD) {
    // submit idle jobs
}
```

This prevents background chunk generation from causing frame drops.

---

## Keeping What Works

Everything above is additive. The existing `Job`, `JobQueue`, `JobModule` code changes minimally:
- `Job` gains `priority`, `CancelToken`, `dependsOn`, `glThread` fields with sensible defaults
- `JobQueue` gains `glJobs` queue and sorting by priority tier
- The `CountDownLatch`-per-frame pattern is unchanged
- Existing `controls` and `matrixWork` jobs are unchanged (default priority = CRITICAL)

---

## Open Questions

- [ ] Should IDLE jobs run on a separate low-priority thread pool, or share the main pool with lower priority?
- [ ] How many GL-pinned jobs per frame is acceptable? (uploading many chunks at once causes frame spikes)
- [ ] Should priority be dynamic (updated each frame) or set once at creation?
- [ ] `dependsOn` across queues (update queue → render queue): is this clean or should chunks manage their own state machine outside the job system?
