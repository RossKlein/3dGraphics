cmake_minimum_required(VERSION 3.16)
project(rmlui_java C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- JNI ----------------------------------------------------------------
find_package(JNI REQUIRED)
message(STATUS "JNI include: ${JNI_INCLUDE_DIRS}")

# ---- RmlUi (static, embedded into our shared lib) -----------------------
set(BUILD_SHARED_LIBS        OFF CACHE BOOL "" FORCE)
set(RMLUI_SAMPLES            OFF CACHE BOOL "" FORCE)
set(RMLUI_TESTS              OFF CACHE BOOL "" FORCE)
set(RMLUI_LOTTIE_PLUGIN      OFF CACHE BOOL "" FORCE)
set(RMLUI_SVG_PLUGIN         OFF CACHE BOOL "" FORCE)
set(RMLUI_THIRDPARTY_CONTAINERS OFF CACHE BOOL "" FORCE)

add_subdirectory(extern/RmlUi)

# ---- rmlui_java shared library ------------------------------------------
add_library(rmlui_java SHARED
    src/rmlui_jni.cpp
    src/HeapRenderInterface.cpp
    src/DefaultSystemInterface.cpp
    src/JavaEventListener.cpp
)

target_include_directories(rmlui_java PRIVATE
    ${JNI_INCLUDE_DIRS}
    include/
)

target_link_libraries(rmlui_java PRIVATE RmlCore)

# Windows needs explicit JNI libs; Linux/macOS resolve them at runtime
if(WIN32)
    target_link_libraries(rmlui_java PRIVATE ${JNI_LIBRARIES})
endif()

# Output directly into the engine's natives/ folder so Java picks it up
set(NATIVES_DIR "${CMAKE_SOURCE_DIR}/../natives")

set_target_properties(rmlui_java PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${NATIVES_DIR}"   # .so on Linux/macOS
    RUNTIME_OUTPUT_DIRECTORY "${NATIVES_DIR}"   # .dll on Windows
)

message(STATUS "Native lib will be written to: ${NATIVES_DIR}")
