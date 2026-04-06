#include <jni.h>
#include <RmlUi/Core.h>
#include "rmlui_java.h"
#include "HeapRenderInterface.h"
#include "DefaultSystemInterface.h"
#include <cstring>

// JNI function naming: Java class is Ross.Modules.ui.RmlUi
// → Java_Ross_Modules_ui_RmlUi_<methodName>

// ---------------------------------------------------------------------------
// Global state (one RmlUi context per engine)
// ---------------------------------------------------------------------------

static JavaVM*                 g_vm              = nullptr;
static DefaultSystemInterface* g_systemInterface = nullptr;
static HeapRenderInterface*    g_renderInterface = nullptr;
static Rml::Context*           g_context         = nullptr;

// Capture the JavaVM on library load — needed for GenerateTexture callback.
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_8;
}

// ---------------------------------------------------------------------------
// Init / Shutdown
// ---------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_Ross_Modules_ui_RmlUi_nInit(
    JNIEnv* env, jobject /*self*/,
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
Java_Ross_Modules_ui_RmlUi_nShutdown(JNIEnv* /*env*/, jobject /*self*/)
{
    if (g_context) {
        Rml::RemoveContext("main");
        g_context = nullptr;
    }
    Rml::Shutdown();
    delete g_renderInterface; g_renderInterface = nullptr;
    delete g_systemInterface; g_systemInterface = nullptr;
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
