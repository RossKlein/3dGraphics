#include "JavaEventListener.h"

JavaEventListener::JavaEventListener(
    JavaVM* vm, jobject callbackGlobalRef, jmethodID method, std::string elementId)
    : vm(vm), callbackRef(callbackGlobalRef), callbackMethod(method)
    , elementId(std::move(elementId)) {}

void JavaEventListener::ProcessEvent(Rml::Event& event) {
    if (!vm || !callbackRef || !callbackMethod) return;

    JNIEnv* env = nullptr;
    bool detach = false;
    int status = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8);
    if (status == JNI_EDETACHED) {
        vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr);
        detach = true;
    }
    if (!env) return;

    // Pull "value" parameter from the event if present (useful for <input> change events).
    std::string valueStr;
    const Rml::Dictionary& params = event.GetParameters();
    auto it = params.find("value");
    if (it != params.end())
        valueStr = it->second.Get<Rml::String>();

    jstring jElemId    = env->NewStringUTF(elementId.c_str());
    jstring jEventType = env->NewStringUTF(event.GetType().c_str());
    jstring jValue     = env->NewStringUTF(valueStr.c_str());

    env->CallVoidMethod(callbackRef, callbackMethod, jElemId, jEventType, jValue);

    env->DeleteLocalRef(jElemId);
    env->DeleteLocalRef(jEventType);
    env->DeleteLocalRef(jValue);

    if (detach) vm->DetachCurrentThread();
}

void JavaEventListener::OnDetach(Rml::Element* /*element*/) {
    delete this;
}
