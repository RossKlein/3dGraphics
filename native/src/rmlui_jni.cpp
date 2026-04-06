#include <jni.h>
#include <RmlUi/Core.h>
#include "rmlui_java.h"
#include "HeapRenderInterface.h"
#include "DefaultSystemInterface.h"
#include "JavaEventListener.h"
#include <cstring>
#include <deque>
#include <unordered_map>
#include <vector>
#include <string>

// JNI function naming: Java class is Ross.Modules.ui.RmlUi
// → Java_Ross_Modules_ui_RmlUi_<methodName>

// ---------------------------------------------------------------------------
// Global state (one RmlUi context per engine)
// ---------------------------------------------------------------------------

static JavaVM*                 g_vm              = nullptr;
static DefaultSystemInterface* g_systemInterface = nullptr;
static HeapRenderInterface*    g_renderInterface = nullptr;
static Rml::Context*           g_context         = nullptr;

// Capture the JavaVM on library load — needed for GenerateTexture and event callbacks.
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_8;
}

// ---------------------------------------------------------------------------
// Event callback (UI → Game)
// ---------------------------------------------------------------------------

static jobject    g_eventCallback       = nullptr; // global ref to Java RmlEventListener
static jmethodID  g_eventCallbackMethod = nullptr;

// ---------------------------------------------------------------------------
// Data models (Game → UI)
// ---------------------------------------------------------------------------

// ModelVar holds one bound variable. Uses double for all numeric types to
// avoid separate float/int storage; Java helpers cast as needed.
struct ModelVar {
    double      number = 0.0;
    std::string text;
    bool        isText = false;
};

struct DataModelState {
    Rml::DataModelHandle handle;
    // std::deque provides stable element addresses across push_back — safe for
    // BindFunc lambdas that capture ModelVar* pointers.
    std::deque<ModelVar>                       storage;
    std::unordered_map<std::string, ModelVar*> index;
};

static std::unordered_map<std::string, DataModelState> g_dataModels;

// ---------------------------------------------------------------------------
// JNI helpers
// ---------------------------------------------------------------------------

static std::vector<std::string> jStringArrayToVec(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> result;
    if (!arr) return result;
    jsize len = env->GetArrayLength(arr);
    result.reserve(len);
    for (jsize i = 0; i < len; i++) {
        auto js = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        const char* s = env->GetStringUTFChars(js, nullptr);
        result.emplace_back(s);
        env->ReleaseStringUTFChars(js, s);
        env->DeleteLocalRef(js);
    }
    return result;
}

// ---------------------------------------------------------------------------
// Init / Shutdown
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nInit(
    JNIEnv* env, jobject self,
    jint width, jint height,
    jobject jCommandBuf,
    jobject jGeomBuf,
    jobject jCommandCount,
    jobject jGeomBufOffset,
    jobject jGeomPending,
    jobject jReleaseBuf,
    jobject jReleaseCount)
{
    auto* cmdBuf      = reinterpret_cast<RmlCommand*>(env->GetDirectBufferAddress(jCommandBuf));
    auto* geomBuf     = reinterpret_cast<uint8_t*>  (env->GetDirectBufferAddress(jGeomBuf));
    auto* cmdCount    = reinterpret_cast<int32_t*>  (env->GetDirectBufferAddress(jCommandCount));
    auto* geomOffset  = reinterpret_cast<int32_t*>  (env->GetDirectBufferAddress(jGeomBufOffset));
    auto* geomPending = reinterpret_cast<int32_t*>  (env->GetDirectBufferAddress(jGeomPending));
    auto* relBuf      = reinterpret_cast<int32_t*>  (env->GetDirectBufferAddress(jReleaseBuf));
    auto* relCount    = reinterpret_cast<int32_t*>  (env->GetDirectBufferAddress(jReleaseCount));

    g_systemInterface = new DefaultSystemInterface();
    g_renderInterface = new HeapRenderInterface(
        cmdBuf, cmdCount,
        geomBuf, geomOffset, geomPending,
        relBuf, relCount,
        g_vm, self);

    Rml::SetSystemInterface(g_systemInterface);
    Rml::SetRenderInterface(g_renderInterface);
    Rml::Initialise();

    g_context = Rml::CreateContext("main",
        Rml::Vector2i(static_cast<int>(width), static_cast<int>(height)));
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nShutdown(JNIEnv* env, jobject /*self*/)
{
    // Data models must be destroyed before Rml::Shutdown().
    g_dataModels.clear();

    if (g_context) {
        Rml::RemoveContext("main");
        g_context = nullptr;
    }
    Rml::Shutdown();
    delete g_renderInterface; g_renderInterface = nullptr;
    delete g_systemInterface; g_systemInterface = nullptr;

    if (g_eventCallback) {
        env->DeleteGlobalRef(g_eventCallback);
        g_eventCallback       = nullptr;
        g_eventCallbackMethod = nullptr;
    }
}

// ---------------------------------------------------------------------------
// Per-frame
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nUpdate(JNIEnv* /*env*/, jobject /*self*/)
{
    if (g_context) g_context->Update();
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nRender(JNIEnv* /*env*/, jobject /*self*/)
{
    if (!g_context) return;
    g_renderInterface->BeginFrame();
    g_context->Render();
    // commandBuf and geomBuf are now filled; Java reads them next.
}

// ---------------------------------------------------------------------------
// Document lifecycle
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jlong JNICALL
Java_Ross_Modules_ui_RmlUi_nLoadDocument(
    JNIEnv* env, jobject /*self*/, jstring jPath)
{
    if (!g_context) return 0;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    Rml::ElementDocument* doc = g_context->LoadDocument(path);
    env->ReleaseStringUTFChars(jPath, path);
    return reinterpret_cast<jlong>(doc);
}

extern "C" JNIEXPORT jlong JNICALL
Java_Ross_Modules_ui_RmlUi_nLoadDocumentFromMemory(
    JNIEnv* env, jobject /*self*/, jstring jRml, jstring jSourceUrl)
{
    if (!g_context) return 0;
    const char* rml = env->GetStringUTFChars(jRml, nullptr);
    const char* url = env->GetStringUTFChars(jSourceUrl, nullptr);
    Rml::ElementDocument* doc = g_context->LoadDocumentFromMemory(rml, url);
    env->ReleaseStringUTFChars(jRml, rml);
    env->ReleaseStringUTFChars(jSourceUrl, url);
    return reinterpret_cast<jlong>(doc);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nShowDocument(
    JNIEnv* /*env*/, jobject /*self*/, jlong handle)
{
    auto* doc = reinterpret_cast<Rml::ElementDocument*>(handle);
    if (doc) doc->Show();
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nHideDocument(
    JNIEnv* /*env*/, jobject /*self*/, jlong handle)
{
    auto* doc = reinterpret_cast<Rml::ElementDocument*>(handle);
    if (doc) doc->Hide();
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nCloseDocument(
    JNIEnv* /*env*/, jobject /*self*/, jlong handle)
{
    auto* doc = reinterpret_cast<Rml::ElementDocument*>(handle);
    if (doc) doc->Close();
}

// ---------------------------------------------------------------------------
// Fonts
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_Ross_Modules_ui_RmlUi_nLoadFont(
    JNIEnv* env, jobject /*self*/, jstring jPath)
{
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    bool ok = Rml::LoadFontFace(path);
    env->ReleaseStringUTFChars(jPath, path);
    return static_cast<jboolean>(ok);
}

// ---------------------------------------------------------------------------
// Input — translate GLFW key/button codes to RmlUi
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nSetDimensions(
    JNIEnv* /*env*/, jobject /*self*/, jint width, jint height)
{
    if (g_context)
        g_context->SetDimensions(Rml::Vector2i(width, height));
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nMouseMove(
    JNIEnv* /*env*/, jobject /*self*/, jint x, jint y, jint modifiers)
{
    if (g_context) g_context->ProcessMouseMove(x, y, modifiers);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nMouseButtonDown(
    JNIEnv* /*env*/, jobject /*self*/, jint button, jint modifiers)
{
    if (g_context) g_context->ProcessMouseButtonDown(button, modifiers);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nMouseButtonUp(
    JNIEnv* /*env*/, jobject /*self*/, jint button, jint modifiers)
{
    if (g_context) g_context->ProcessMouseButtonUp(button, modifiers);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nMouseScroll(
    JNIEnv* /*env*/, jobject /*self*/, jfloat deltaX, jfloat deltaY, jint modifiers)
{
    if (g_context)
        g_context->ProcessMouseWheel(Rml::Vector2f(deltaX, deltaY), modifiers);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nKeyDown(
    JNIEnv* /*env*/, jobject /*self*/, jint rmlKeyId, jint modifiers)
{
    if (g_context)
        g_context->ProcessKeyDown(static_cast<Rml::Input::KeyIdentifier>(rmlKeyId), modifiers);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nKeyUp(
    JNIEnv* /*env*/, jobject /*self*/, jint rmlKeyId, jint modifiers)
{
    if (g_context)
        g_context->ProcessKeyUp(static_cast<Rml::Input::KeyIdentifier>(rmlKeyId), modifiers);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nTextInput(
    JNIEnv* /*env*/, jobject /*self*/, jint codepoint)
{
    if (g_context)
        g_context->ProcessTextInput(static_cast<Rml::Character>(codepoint));
}

// ---------------------------------------------------------------------------
// Texture registration — call before loading documents that reference images
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nRegisterTexture(
    JNIEnv* env, jobject /*self*/, jstring jPath, jint glTextureId)
{
    if (!g_renderInterface) return;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    g_renderInterface->registerTexture(
        std::string(path),
        static_cast<Rml::TextureHandle>(glTextureId));
    env->ReleaseStringUTFChars(jPath, path);
}

// ---------------------------------------------------------------------------
// Event callbacks (UI → Game)
//
// Usage pattern:
//   rmlUi.setEventCallback((elemId, eventType, value) -> { ... });
//   long doc = rmlUi.loadDocument("menu.rml");
//   rmlUi.addEventListener(doc, "start-button", "click");
//   rmlUi.addEventListener(doc, "volume-slider", "change");
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nSetEventCallback(
    JNIEnv* env, jobject /*self*/, jobject jListener)
{
    // Delete previous global ref if any.
    if (g_eventCallback) {
        env->DeleteGlobalRef(g_eventCallback);
        g_eventCallback       = nullptr;
        g_eventCallbackMethod = nullptr;
    }
    if (!jListener) return;

    g_eventCallback = env->NewGlobalRef(jListener);
    jclass cls = env->GetObjectClass(jListener);
    g_eventCallbackMethod = env->GetMethodID(cls, "onEvent",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nAddEventListener(
    JNIEnv* env, jobject /*self*/,
    jlong docHandle, jstring jElemId, jstring jEventType)
{
    if (!g_eventCallback || !g_eventCallbackMethod) return;
    auto* doc = reinterpret_cast<Rml::ElementDocument*>(docHandle);
    if (!doc) return;

    const char* elemId    = env->GetStringUTFChars(jElemId, nullptr);
    const char* eventType = env->GetStringUTFChars(jEventType, nullptr);

    Rml::Element* elem = doc->GetElementById(elemId);
    if (elem) {
        // JavaEventListener deletes itself in OnDetach — RmlUi manages lifetime.
        auto* listener = new JavaEventListener(
            g_vm, g_eventCallback, g_eventCallbackMethod, elemId);
        elem->AddEventListener(eventType, listener);
    }

    env->ReleaseStringUTFChars(jElemId, elemId);
    env->ReleaseStringUTFChars(jEventType, eventType);
}

// ---------------------------------------------------------------------------
// Data models (Game → UI)
//
// Usage pattern:
//   rmlUi.createDataModel("hud",
//       new String[]{"speed", "altitude", "timeOfDay"},   // float vars
//       new String[]{"biome", "statusText"});              // string vars
//
//   // Each update frame:
//   rmlUi.setModelFloat("hud", "speed",    bird.getSpeed());
//   rmlUi.setModelFloat("hud", "altitude", bird.getAltitude());
//   rmlUi.setModelString("hud", "biome",   world.getCurrentBiome());
//
//   // .rml template uses: <span>{{speed}} m/s</span>
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nCreateDataModel(
    JNIEnv* env, jobject /*self*/,
    jstring jModelName, jobjectArray jFloatVars, jobjectArray jStringVars)
{
    if (!g_context) return;

    const char* modelName = env->GetStringUTFChars(jModelName, nullptr);
    std::string name(modelName);
    env->ReleaseStringUTFChars(jModelName, modelName);

    auto floatVarNames  = jStringArrayToVec(env, jFloatVars);
    auto stringVarNames = jStringArrayToVec(env, jStringVars);

    // Erase any previous model with this name.
    g_dataModels.erase(name);
    DataModelState& state = g_dataModels[name];

    Rml::DataModelConstructor ctor = g_context->CreateDataModel(name);
    if (!ctor) return;

    for (const auto& varName : floatVarNames) {
        state.storage.push_back(ModelVar{});
        ModelVar* var = &state.storage.back();
        state.index[varName] = var;
        ctor.BindFunc(varName,
            [var](Rml::Variant& out)        { out = var->number; },
            [var](const Rml::Variant& in)   { var->number = in.Get<double>(); });
    }

    for (const auto& varName : stringVarNames) {
        ModelVar textVar;
        textVar.isText = true;
        state.storage.push_back(textVar);
        ModelVar* var = &state.storage.back();
        state.index[varName] = var;
        ctor.BindFunc(varName,
            [var](Rml::Variant& out)        { out = var->text; },
            [var](const Rml::Variant& in)   { var->text = in.Get<Rml::String>(); });
    }

    state.handle = ctor.GetModelHandle();
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nSetModelFloat(
    JNIEnv* env, jobject /*self*/, jstring jModel, jstring jVar, jfloat value)
{
    const char* model = env->GetStringUTFChars(jModel, nullptr);
    const char* var   = env->GetStringUTFChars(jVar,   nullptr);

    auto mit = g_dataModels.find(model);
    if (mit != g_dataModels.end()) {
        auto vit = mit->second.index.find(var);
        if (vit != mit->second.index.end()) {
            vit->second->number = static_cast<double>(value);
            mit->second.handle.DirtyVariable(var);
        }
    }

    env->ReleaseStringUTFChars(jModel, model);
    env->ReleaseStringUTFChars(jVar,   var);
}

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nSetModelString(
    JNIEnv* env, jobject /*self*/, jstring jModel, jstring jVar, jstring jValue)
{
    const char* model = env->GetStringUTFChars(jModel, nullptr);
    const char* var   = env->GetStringUTFChars(jVar,   nullptr);
    const char* value = env->GetStringUTFChars(jValue, nullptr);

    auto mit = g_dataModels.find(model);
    if (mit != g_dataModels.end()) {
        auto vit = mit->second.index.find(var);
        if (vit != mit->second.index.end()) {
            vit->second->text = value;
            mit->second.handle.DirtyVariable(var);
        }
    }

    env->ReleaseStringUTFChars(jModel, model);
    env->ReleaseStringUTFChars(jVar,   var);
    env->ReleaseStringUTFChars(jValue, value);
}
