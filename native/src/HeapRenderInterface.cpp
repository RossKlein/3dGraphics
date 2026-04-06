#include "HeapRenderInterface.h"
#include <cstring>
#include <cassert>

// Verify our assumed Rml::Vertex layout at compile time.
// If this fails, adjust VERTEX_STRIDE and the VAO attribute offsets in Java.
static_assert(sizeof(Rml::Vertex) == 20,
    "Rml::Vertex is not 20 bytes — update VERTEX_STRIDE in rmlui_java.h and RmlUi.java");
static_assert(offsetof(Rml::Vertex, position)  == 0,  "Vertex.position offset changed");
static_assert(offsetof(Rml::Vertex, colour)    == 8,  "Vertex.colour offset changed");
static_assert(offsetof(Rml::Vertex, tex_coord) == 12, "Vertex.tex_coord offset changed");

// ---------------------------------------------------------------------------

HeapRenderInterface::HeapRenderInterface(
    RmlCommand* commandBuf,  int32_t* commandCount,
    uint8_t*    geomBuf,     int32_t* geomBufOffset, int32_t* geomPending,
    int32_t*    releaseBuf,  int32_t* releaseCount,
    JavaVM* vm, jobject javaObj)
    : cmdBuf(commandBuf),   cmdCount(commandCount)
    , geomBuf(geomBuf),     geomBufOffset(geomBufOffset), geomPending(geomPending)
    , relBuf(releaseBuf),   relCount(releaseCount)
    , vm(vm)
{
    *cmdCount      = 0;
    *geomBufOffset = 0;
    *geomPending   = 0;
    *relCount      = 0;

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) == JNI_OK) {
        javaObjGlobalRef = env->NewGlobalRef(javaObj);
        jclass cls = env->GetObjectClass(javaObj);
        generateTextureMethod = env->GetMethodID(cls, "nGenerateTextureCallback",
            "([BII)I");
    }
}

HeapRenderInterface::~HeapRenderInterface() {
    JNIEnv* env = nullptr;
    if (vm && vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8) == JNI_OK) {
        env->DeleteGlobalRef(javaObjGlobalRef);
    }
}

void HeapRenderInterface::BeginFrame() {
    *cmdCount = 0;
    // geomBuf and relBuf are reset by Java after it processes them.
}

// ---------------------------------------------------------------------------
// Texture registry
// ---------------------------------------------------------------------------

void HeapRenderInterface::registerTexture(const std::string& path, Rml::TextureHandle handle) {
    textureRegistry[path] = handle;
}

// ---------------------------------------------------------------------------
// Compiled geometry
// ---------------------------------------------------------------------------

Rml::CompiledGeometryHandle HeapRenderInterface::CompileGeometry(
    Rml::Span<const Rml::Vertex> vertices,
    Rml::Span<const int>         indices)
{
    const int32_t id          = nextGeomId++;
    const int32_t vertexBytes = (int32_t)vertices.size() * RMLUI_VERTEX_SIZE;
    const int32_t indexBytes  = (int32_t)indices.size()  * sizeof(int32_t);
    const int32_t totalBytes  = (int32_t)sizeof(GeometryHeader) + vertexBytes + indexBytes;

    if (*geomBufOffset + totalBytes > GEOM_BUFFER_BYTES) {
        fprintf(stderr, "[RmlUi] Geometry buffer full — increase GEOM_BUFFER_BYTES\n");
        return 0;
    }

    uint8_t* dst = geomBuf + *geomBufOffset;

    GeometryHeader hdr{ id, (int32_t)vertices.size(), (int32_t)indices.size() };
    memcpy(dst, &hdr, sizeof(GeometryHeader));
    dst += sizeof(GeometryHeader);

    memcpy(dst, vertices.data(), vertexBytes);
    dst += vertexBytes;

    memcpy(dst, indices.data(), indexBytes);

    *geomBufOffset += totalBytes;
    (*geomPending)++;

    return static_cast<Rml::CompiledGeometryHandle>(id);
}

void HeapRenderInterface::RenderGeometry(
    Rml::CompiledGeometryHandle geometry,
    Rml::Vector2f               translation,
    Rml::TextureHandle          texture)
{
    // Encode geometry ID as float bits so it fits in the command's float array.
    auto geomId = static_cast<int32_t>(reinterpret_cast<intptr_t>(
        reinterpret_cast<void*>(geometry)));
    auto texId  = static_cast<int32_t>(reinterpret_cast<intptr_t>(
        reinterpret_cast<void*>(texture)));

    float geomF, texF;
    memcpy(&geomF, &geomId, 4);
    memcpy(&texF,  &texId,  4);

    pushCommand(CMD_DRAW, geomF, translation.x, translation.y, texF);
}

void HeapRenderInterface::ReleaseGeometry(Rml::CompiledGeometryHandle geometry) {
    if (*relCount < MAX_RELEASES) {
        relBuf[(*relCount)++] = static_cast<int32_t>(
            reinterpret_cast<intptr_t>(reinterpret_cast<void*>(geometry)));
    }
}

// ---------------------------------------------------------------------------
// Scissor
// ---------------------------------------------------------------------------

void HeapRenderInterface::EnableScissorRegion(bool enable) {
    if (!enable)
        pushCommand(CMD_SCISSOR_OFF, 0, 0, 0, 0);
}

void HeapRenderInterface::SetScissorRegion(Rml::Rectanglei region) {
    // Store ints as float bits — Java reconstructs with Float.floatToRawIntBits
    int32_t x = region.Left(),  y = region.Top();
    int32_t w = region.Width(), h = region.Height();
    float xf, yf, wf, hf;
    memcpy(&xf, &x, 4); memcpy(&yf, &y, 4);
    memcpy(&wf, &w, 4); memcpy(&hf, &h, 4);
    pushCommand(CMD_SCISSOR_ON, xf, yf, wf, hf);
}

// ---------------------------------------------------------------------------
// Textures
// ---------------------------------------------------------------------------

Rml::TextureHandle HeapRenderInterface::LoadTexture(
    Rml::Vector2i&     /*texture_dimensions*/,
    const Rml::String& source)
{
    auto it = textureRegistry.find(std::string(source.c_str()));
    if (it != textureRegistry.end())
        return it->second;

    fprintf(stderr, "[RmlUi] LoadTexture: '%s' not pre-registered — call registerTexture() first\n",
        source.c_str());
    return 0;
}

Rml::TextureHandle HeapRenderInterface::GenerateTexture(
    Rml::Span<const Rml::byte> source_data,
    Rml::Vector2i              source_dimensions)
{
    // This is called for font atlases — infrequent, safe to use JNI callback.
    if (!vm || !generateTextureMethod) return 0;

    JNIEnv* env = nullptr;
    bool detach = false;
    int status = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_8);
    if (status == JNI_EDETACHED) {
        vm->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr);
        detach = true;
    }
    if (!env) return 0;

    int byteCount = (int)source_data.size();
    jbyteArray jPixels = env->NewByteArray(byteCount);
    env->SetByteArrayRegion(jPixels, 0, byteCount,
        reinterpret_cast<const jbyte*>(source_data.data()));

    jint glTexId = env->CallIntMethod(
        javaObjGlobalRef, generateTextureMethod,
        jPixels, source_dimensions.x, source_dimensions.y);

    env->DeleteLocalRef(jPixels);
    if (detach) vm->DetachCurrentThread();

    return static_cast<Rml::TextureHandle>(glTexId);
}

void HeapRenderInterface::ReleaseTexture(Rml::TextureHandle /*handle*/) {
    // TODO: notify Java to delete GL texture if it was generated (not pre-registered)
}

// ---------------------------------------------------------------------------

void HeapRenderInterface::pushCommand(
    CommandType type, float d0, float d1, float d2, float d3)
{
    if (*cmdCount >= MAX_COMMANDS) return;
    RmlCommand& cmd = cmdBuf[*cmdCount];
    cmd.type = static_cast<int32_t>(type);
    cmd.d[0] = d0; cmd.d[1] = d1; cmd.d[2] = d2; cmd.d[3] = d3;
    (*cmdCount)++;
}
