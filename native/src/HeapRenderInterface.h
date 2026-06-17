#pragma once
#include "rmlui_java.h"
#include <jni.h>
#include <unordered_map>
#include <string>

class HeapRenderInterface final : public Rml::RenderInterface {
public:
    // All buffers are Java direct ByteBuffers; C++ writes, Java reads.
    //
    //  commandBuf     — RmlCommand[]  (MAX_COMMANDS slots)
    //  commandCount   — int32_t[1]    number of commands written this frame
    //  geomBuf        — raw bytes     for pending geometry uploads
    //  geomBufOffset  — int32_t[1]    write cursor into geomBuf
    //  geomPending    — int32_t[1]    number of new geometries pending upload
    //  releaseBuf     — int32_t[]     geometry IDs to free  (MAX_RELEASES slots)
    //  releaseCount   — int32_t[1]    number of pending releases
    //
    //  vm             — used for GenerateTexture JNI callback (font atlases)
    //  javaObj        — the Java RmlUi instance (global ref)
    HeapRenderInterface(
        RmlCommand* commandBuf,  int32_t* commandCount,
        uint8_t*    geomBuf,     int32_t* geomBufOffset, int32_t* geomPending,
        int32_t*    releaseBuf,  int32_t* releaseCount,
        JavaVM* vm, jobject javaObj
    );

    ~HeapRenderInterface();

    // Call before context.Render() each frame to reset command cursor.
    void BeginFrame();

    // Register a pre-loaded GL texture so LoadTexture() can find it by path.
    void registerTexture(const std::string& path, Rml::TextureHandle handle);

    // ---- Rml::RenderInterface ----------------------------------------

    Rml::CompiledGeometryHandle CompileGeometry(
        Rml::Span<const Rml::Vertex> vertices,
        Rml::Span<const int>         indices) override;

    void RenderGeometry(
        Rml::CompiledGeometryHandle geometry,
        Rml::Vector2f               translation,
        Rml::TextureHandle          texture) override;

    void ReleaseGeometry(Rml::CompiledGeometryHandle geometry) override;

    void EnableScissorRegion(bool enable) override;
    void SetScissorRegion(Rml::Rectanglei region) override;

    // LoadTexture: looks up pre-registered textures by path.
    Rml::TextureHandle LoadTexture(Rml::Vector2i&     texture_dimensions,
                                   const Rml::String& source) override;

    // GenerateTexture: calls back into Java to create the GL texture.
    // Used by RmlUi internally for font atlases.
    Rml::TextureHandle GenerateTexture(Rml::Span<const Rml::byte> source_data,
                                       Rml::Vector2i source_dimensions) override;

    void ReleaseTexture(Rml::TextureHandle texture_handle) override;

private:
    RmlCommand* cmdBuf;
    int32_t*    cmdCount;

    uint8_t*    geomBuf;
    int32_t*    geomBufOffset;
    int32_t*    geomPending;

    int32_t*    relBuf;
    int32_t*    relCount;

    int32_t nextGeomId = 1;

    // Texture registry: path → GL texture ID
    std::unordered_map<std::string, Rml::TextureHandle> textureRegistry;

    // JVM handle for GenerateTexture callback
    JavaVM*  vm;
    jobject  javaObjGlobalRef;   // global ref to Java RmlUi instance
    jmethodID generateTextureMethod = nullptr;

    void pushCommand(CommandType type, float d0, float d1, float d2, float d3);
};
