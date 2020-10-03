package main.shaders;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public abstract class Shader {

    private int programId;
    private int vertexShaderId, fragmentShaderId;

    public Shader(String vertexShaderFile, String fragmentShaderFile) {
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


    private static int loadShader( String file, int type) {
        InputStream in = Shader.class.getResourceAsStream(file);
        StringBuilder string = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader( new InputStreamReader(in));
            String line;
            while((line=reader.readLine())!=null) {
                string.append(line + "//\n");
                reader.close();
            }
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
}