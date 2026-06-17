package Ross.Modules.ui;

/**
 * Callback interface for RmlUi → Game events.
 *
 * Register with {@link RmlUi#setEventCallback} and attach to specific elements
 * via {@link RmlUi#addEventListener}.
 *
 * All three parameters are non-null strings:
 *   elementId  — the HTML id="" attribute of the element that fired the event
 *   eventType  — RmlUi event type string: "click", "change", "submit", etc.
 *   value      — the element's "value" parameter if present, otherwise ""
 *                (populated for <input> change events, empty for clicks)
 *
 * Example:
 * <pre>{@code
 * rmlUi.setEventCallback((elemId, eventType, value) -> {
 *     if (elemId.equals("start-button") && eventType.equals("click"))
 *         sceneManager.push(new GameScene());
 *     if (elemId.equals("volume-slider") && eventType.equals("change"))
 *         audio.setVolume(Float.parseFloat(value));
 * });
 * }</pre>
 */
@FunctionalInterface
public interface RmlEventListener {
    void onEvent(String elementId, String eventType, String value);
}
