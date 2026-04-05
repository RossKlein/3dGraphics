#pragma once

#include <RmlUi/Core.h>
#include <cstdint>

// ---------------------------------------------------------------------------
// Buffer protocol shared between C++ (HeapRenderInterface) and Java (RmlUi)
// ---------------------------------------------------------------------------
//
// COMMAND BUFFER  (written by C++ each frame, read by Java to issue draw calls)
//   Fixed-size 20-byte slots; Java iterates commandCount slots.
//
// GEOMETRY BUFFER (written by C++ on CompileGeometry, read ONCE by Java to
//   create a VAO, then Java zeros geomPendingCount)
//
// RELEASE BUFFER  (written by C++ on ReleaseGeometry, read by Java to free VAOs)
// ---------------------------------------------------------------------------

// --- Command buffer --------------------------------------------------------

enum CommandType : int32_t {
    CMD_DRAW        = 0,   // data: [geometryId, tx, ty, textureHandle]
    CMD_SCISSOR_ON  = 1,   // data: [x, y, width, height]  (int reinterpreted as float)
    CMD_SCISSOR_OFF = 2,   // data: unused
};

// All commands are the same size so Java can index directly.
#pragma pack(push, 1)
struct RmlCommand {
    int32_t type;     // CommandType
    float   d[4];     // Payload (see CommandType docs above)
};
#pragma pack(pop)

static_assert(sizeof(RmlCommand) == 20, "RmlCommand must be 20 bytes");

// --- Geometry buffer -------------------------------------------------------
// Layout per entry:
//   GeometryHeader  (12 bytes)
//   Rml::Vertex[]   (vertexCount * 20 bytes)
//   int32_t[]       (indexCount  *  4 bytes)
//
// Rml::Vertex memory layout (matches OpenGL VAO below):
//   float  position[2]   offset  0  size 8
//   uint8  colour[4]     offset  8  size 4
//   float  tex_coord[2]  offset 12  size 8
//   ---                  stride 20

#pragma pack(push, 1)
struct GeometryHeader {
    int32_t geometryId;
    int32_t vertexCount;
    int32_t indexCount;
    // Followed immediately by raw vertex bytes then raw index bytes.
};
#pragma pack(pop)

static_assert(sizeof(GeometryHeader) == 12, "GeometryHeader must be 12 bytes");

constexpr int32_t RMLUI_VERTEX_SIZE  = 20;  // sizeof(Rml::Vertex)
constexpr int32_t MAX_COMMANDS       = 4096;
constexpr int32_t GEOM_BUFFER_BYTES  = 4 * 1024 * 1024;  // 4 MB
constexpr int32_t MAX_RELEASES       = 256;
