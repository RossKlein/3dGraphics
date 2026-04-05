#include "HeapRenderInterface.h"
#include <cstring>
#include <cassert>

HeapRenderInterface::HeapRenderInterface(
    RmlCommand* commandBuf,  int32_t* commandCount,
    uint8_t*    geomBuf,     int32_t* geomBufOffset, int32_t* geomPending,
    int32_t*    releaseBuf,  int32_t* releaseCount)
    : cmdBuf(commandBuf),   cmdCount(commandCount)
    , geomBuf(geomBuf),     geomBufOffset(geomBufOffset), geomPending(geomPending)
    , relBuf(releaseBuf),   relCount(releaseCount)
{
    *cmdCount      = 0;
    *geomBufOffset = 0;
    *geomPending   = 0;
    *relCount      = 0;
}

void HeapRenderInterface::BeginFrame() {
    *cmdCount = 0;
    // Note: geomBuf is NOT reset here — Java resets it after uploading VAOs.
    // relBuf  is NOT reset here — Java resets it after freeing VAOs.
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

    // Check we have room
    if (*geomBufOffset + totalBytes > GEOM_BUFFER_BYTES) {
        // Buffer full — this is a configuration issue; log and skip.
        return 0;
    }

    uint8_t* dst = geomBuf + *geomBufOffset;

    // Write header
    GeometryHeader hdr;
    hdr.geometryId  = id;
    hdr.vertexCount = (int32_t)vertices.size();
    hdr.indexCount  = (int32_t)indices.size();
    memcpy(dst, &hdr, sizeof(GeometryHeader));
    dst += sizeof(GeometryHeader);

    // Write raw vertex data (Rml::Vertex is 20 bytes, matches Java VAO layout)
    memcpy(dst, vertices.data(), vertexBytes);
    dst += vertexBytes;

    // Write index data (RmlUi uses int, same as GL_UNSIGNED_INT)
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
    float texHandle;
    memcpy(&texHandle, &texture, sizeof(float));   // reinterpret handle as float
    pushCommand(CMD_DRAW,
        static_cast<float>(reinterpret_cast<intptr_t>(reinterpret_cast<void*>(geometry))),
        translation.x,
        translation.y,
        texHandle);
}

void HeapRenderInterface::ReleaseGeometry(Rml::CompiledGeometryHandle geometry) {
    if (*relCount < MAX_RELEASES) {
        relBuf[(*relCount)++] =
            static_cast<int32_t>(reinterpret_cast<intptr_t>(reinterpret_cast<void*>(geometry)));
    }
}

// ---------------------------------------------------------------------------
// Scissor
// ---------------------------------------------------------------------------

void HeapRenderInterface::EnableScissorRegion(bool enable) {
    if (enable) {
        // Region will follow immediately via SetScissorRegion
    } else {
        pushCommand(CMD_SCISSOR_OFF, 0, 0, 0, 0);
    }
}

void HeapRenderInterface::SetScissorRegion(Rml::Rectanglei region) {
    // Store ints as floats — Java casts them back with Float.floatToRawIntBits
    float x = *reinterpret_cast<const float*>(&region.Left());
    float y = *reinterpret_cast<const float*>(&region.Top());
    int32_t w = region.Width();
    int32_t h = region.Height();
    float wf = *reinterpret_cast<const float*>(&w);
    float hf = *reinterpret_cast<const float*>(&h);
    pushCommand(CMD_SCISSOR_ON, x, y, wf, hf);
}

// ---------------------------------------------------------------------------
// Textures — Java owns GL texture creation, so we just return stubs.
// Java registers textures by calling nRegisterTexture(path, glTextureId).
// ---------------------------------------------------------------------------

Rml::TextureHandle HeapRenderInterface::LoadTexture(
    Rml::Vector2i&     /*texture_dimensions*/,
    const Rml::String& /*source*/)
{
    // Java-side texture loading: Java pre-registers textures before loading
    // documents. Return 0 for now; full implementation routes through JNI callback.
    return 0;
}

Rml::TextureHandle HeapRenderInterface::GenerateTexture(
    Rml::Span<const Rml::byte> /*source_data*/,
    Rml::Vector2i              /*source_dimensions*/)
{
    return 0;
}

void HeapRenderInterface::ReleaseTexture(Rml::TextureHandle /*texture_handle*/) {}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

void HeapRenderInterface::pushCommand(
    CommandType type, float d0, float d1, float d2, float d3)
{
    if (*cmdCount >= MAX_COMMANDS) return;
    RmlCommand& cmd = cmdBuf[*cmdCount];
    cmd.type = static_cast<int32_t>(type);
    cmd.d[0] = d0;
    cmd.d[1] = d1;
    cmd.d[2] = d2;
    cmd.d[3] = d3;
    (*cmdCount)++;
}
