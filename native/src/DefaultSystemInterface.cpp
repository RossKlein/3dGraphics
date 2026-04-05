#include "DefaultSystemInterface.h"
#include <chrono>
#include <cstdio>

static auto startTime = std::chrono::steady_clock::now();

double DefaultSystemInterface::GetElapsedTime() {
    auto now = std::chrono::steady_clock::now();
    return std::chrono::duration<double>(now - startTime).count();
}

void DefaultSystemInterface::LogMessage(Rml::Log::Type type, const Rml::String& message) {
    const char* prefix = "RmlUi";
    switch (type) {
        case Rml::Log::LT_ERROR:   prefix = "RmlUi ERROR"; break;
        case Rml::Log::LT_WARNING: prefix = "RmlUi WARN";  break;
        default: break;
    }
    fprintf(stderr, "[%s] %s\n", prefix, message.c_str());
}

void DefaultSystemInterface::SetMouseCursor(const Rml::String& /*cursor_name*/) {
    // TODO: route to GLFW cursor API via JNI callback if needed
}

void DefaultSystemInterface::SetClipboardText(const Rml::String& /*text*/) {
    // TODO: glfwSetClipboardString via JNI callback
}

void DefaultSystemInterface::GetClipboardText(Rml::String& /*text*/) {
    // TODO: glfwGetClipboardString via JNI callback
}
