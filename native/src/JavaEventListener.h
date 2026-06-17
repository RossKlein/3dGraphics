#pragma once
#include <jni.h>
#include <RmlUi/Core.h>
#include <string>

/**
 * Bridges a single RmlUi event to the Java RmlEventListener callback.
 *
 * One instance is created per (element × eventType) registration via
 * nAddEventListener. RmlUi owns the lifetime: OnDetach() is called when
 * the element is removed from the DOM, at which point the listener deletes
 * itself. Do NOT delete manually.
 *
 * All listeners share the same Java callback global-ref (g_eventCallback),
 * which is managed by the JNI layer — not here.
 */
class JavaEventListener final : public Rml::EventListener {
public:
    JavaEventListener(JavaVM* vm, jobject callbackGlobalRef, jmethodID method,
                      std::string elementId);
    ~JavaEventListener() override = default;

    void ProcessEvent(Rml::Event& event) override;

    // Called by RmlUi when the element is destroyed. Listener must delete itself.
    void OnDetach(Rml::Element* element) override;

private:
    JavaVM*     vm;
    jobject     callbackRef;    // shared global ref — do not delete
    jmethodID   callbackMethod;
    std::string elementId;
};
