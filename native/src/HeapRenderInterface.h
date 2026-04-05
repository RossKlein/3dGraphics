#pragma once
#include "rmlui_java.h"

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
    HeapRenderInterface(
        RmlCommand* commandBuf,  int32_t* commandCount,
        uint8_t*    geomBuf,     int32_t* geomBufOffset, int32_t* geomPending,
        int32_t*    releaseBuf,  int32_t* releaseCount
    );

    // Call before context.Render() each frame to reset command cursor.
    void BeginFrame();

    // ---- Rml::RenderInterface ----------------------------------------

    // Required: compiled geometry
    Rml::CompiledGeometryHandle CompileGeometry(
        Rml::Span<const Rml::Vertex> vertices,
        Rml::Span<const int>         indices) override;

    void RenderGeometry(
        Rml::CompiledGeometryHandle geometry,
        Rml::Vector2f               translation,
        Rml::TextureHandle          texture) override;

    void ReleaseGeometry(Rml::CompiledGeometryHandle geometry) override;

    // Scissor
    void EnableScissorRegion(bool enable) override;
    void SetScissorRegion(Rml::Rectanglei region) override;

    // Textures — stubs; Java owns all texture loading
    Rml::TextureHandle LoadTexture(Rml::Vector2i&     texture_dimensions,
                                   const Rml::String& source) override;
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

    void pushCommand(CommandType type, float d0, float d1, float d2, float d3);
};
