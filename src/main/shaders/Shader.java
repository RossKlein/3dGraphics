package main.shaders;

import main.math.Mat4f;
import main.math.Vec3f;
import org.lwjgl.opengl.*;
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
    private Map<String, Integer> uniforms;

    public Shader(String vertexShaderFile, String fragmentShaderFile) {
        uniforms = new HashMap<>();

        programId = GL20.glCreateProgram();
        vertexShaderId = loadShader(vertexShaderFile, GL20.GL_VERTEX_SHADER);
        fragmentShaderId = loadShader(fragmentShaderFile, GL20.GL_FRAGMENT_SHADER);

        GL20.glAttachShader( programId, vertexShaderId);
        GL20.glAttachShader( programId, fragmentShaderId);

        bindAttributes();
        GL20.glLinkProgram(programId);
        GL20.glValidateProgram( programId);

    }

    protected abstract void bindAttributes();

    protected void bindAttributeLocation(int attributeNr, String variableName){
        GL20.glBindAttribLocation(programId, attributeNr, variableName);
    }

    protected void bindUniformMatrix4fvLocation(Mat4f matrix, String name, boolean transpose){
        int uniformLocation = GL20.glGetUniformLocation(programId, name);
        uniforms.put(name, uniformLocation);
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            matrix.get(fb);

            GL20.glUniformMatrix4fv(uniformLocation, transpose, fb);

        }


    }
    protected void bindUniform3vLocation(Vec3f vector, String name) {
        int uniformLocation = GL20.glGetUniformLocation(programId, name);

        GL20.glUniform3fv(uniformLocation, new float[]{vector.x(), vector.y(), vector.z()});
    }
    public abstract void loadMatrix(Mat4f matrix, String name, boolean transpose);
    public abstract void loadLightSource(Vec3f vector);


    public void startShader() {
        GL20.glUseProgram( programId);
    }

    public void stopShader() {
        GL20.glUseProgram(0);

    }

    private static int loadShader( String file, int type) {
        InputStream in = Shader.class.getResourceAsStream(file);
        StringBuilder string = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader( new InputStreamReader(in));
            String line;
            while( (line=reader.readLine()) != null) {
                string.append(line + "//\n");
            }
            reader.close();
        } catch (IOException e){
            System.err.println("Couldn't load vertex/fragment shader file " + file);
        }
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, string);
        GL20.glCompileShader(shaderId);
        if( GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("Failed to compile vertex/fragment shader " + file);
            System.out.println(GL20.glGetShaderInfoLog(shaderId, 250));
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