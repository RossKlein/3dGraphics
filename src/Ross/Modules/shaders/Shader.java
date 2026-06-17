package Ross.Modules.shaders;

import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public abstract class Shader {

    private int programId;
    private int vertexShaderId, fragmentShaderId;
    // Cached uniform locations — avoids a glGetUniformLocation call every frame.
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public Shader(String vertexShaderFile, String fragmentShaderFile) {
        programId = GL20.glCreateProgram();
        vertexShaderId = loadShader(vertexShaderFile, GL20.GL_VERTEX_SHADER);
        fragmentShaderId = loadShader(fragmentShaderFile, GL20.GL_FRAGMENT_SHADER);

        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);

        bindAttributes();
        GL20.glLinkProgram(programId);
        GL20.glValidateProgram(programId);
    }

    protected abstract void bindAttributes();

    protected void bindAttributeLocation(int attributeNr, String variableName) {
        GL20.glBindAttribLocation(programId, attributeNr, variableName);
    }

    // ---- Uniform helpers ---------------------------------------------------

    private int uniformLocation(String name) {
        return uniformCache.computeIfAbsent(name,
                n -> GL20.glGetUniformLocation(programId, n));
    }

    protected void bindUniformMatrix4fvLocation(Mat4f matrix, String name, boolean transpose) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            matrix.get(fb);
            GL20.glUniformMatrix4fv(uniformLocation(name), transpose, fb);
        }
    }

    protected void bindUniform3vLocation(Vec3f vector, String name) {
        GL20.glUniform3fv(uniformLocation(name),
                new float[]{vector.x(), vector.y(), vector.z()});
    }

    public void bindUniformBool(String name, boolean value) {
        GL20.glUniform1i(uniformLocation(name), value ? 1 : 0);
    }

    protected void uniform1f(String name, float v) {
        GL20.glUniform1f(uniformLocation(name), v);
    }

    protected void uniform2f(String name, float x, float y) {
        GL20.glUniform2f(uniformLocation(name), x, y);
    }

    protected void uniform3f(String name, float x, float y, float z) {
        GL20.glUniform3f(uniformLocation(name), x, y, z);
    }

    protected void uniform1i(String name, int v) {
        GL20.glUniform1i(uniformLocation(name), v);
    }

    protected void uniformMatrix3fv(String name, boolean transpose, float[] values) {
        GL20.glUniformMatrix3fv(uniformLocation(name), transpose, values);
    }

    // ---- Geometry-shader helpers (optional to override) -------------------

    public void loadMatrix(Mat4f matrix, String name, boolean transpose) {}

    public void loadLightSource(Vec3f vector) {}

    // ---- Program lifecycle -------------------------------------------------

    public void startShader() {
        GL20.glUseProgram(programId);
    }

    public void stopShader() {
        GL20.glUseProgram(0);
    }

    private static int loadShader(String file, int type) {
        InputStream in = Shader.class.getResourceAsStream(file);
        StringBuilder source = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append('\n');
            }
            reader.close();
        } catch (IOException e) {
            System.err.println("Couldn't load vertex/fragment shader file " + file);
        }
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("Failed to compile vertex/fragment shader " + file);
            System.out.println(GL20.glGetShaderInfoLog(shaderId, 500));
            System.exit(-1);
        }
        return shaderId;
    }

    public void exit() {
        stopShader();
        GL20.glDetachShader(programId, vertexShaderId);
        GL20.glDetachShader(programId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
        GL20.glDeleteProgram(programId);
    }
}