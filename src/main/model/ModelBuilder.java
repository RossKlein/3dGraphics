package main.model;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import textures.Texture;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

public class ModelBuilder {

    private static ArrayList<Integer> vaoIdList = new ArrayList<>();
    private static ArrayList<Integer> vboIdList = new ArrayList<>();


    public ModelBuilder () {

    }

    public Model buildModel(float[] vertices, int[] indices, float[] colors, float[] normals) {
        int vaoId = createVao();
        GL30.glBindVertexArray( vaoId);


        bindIndices( indices);

        storeDataInAttributeList(0,3,vertices);
        storeDataInAttributeList(1,4,colors);
        storeDataInAttributeList(2,3,normals);

        GL30.glBindVertexArray(0);
        return new Model( vaoId, indices.length);

    }

    public TexturedModel buildModel(float[] vertices, int[] indices, float[] colors, float[] normals, float[] uvCoordinates, Texture texture) {
        int vaoId = createVao();
        GL30.glBindVertexArray( vaoId);


        bindIndices( indices);

        storeDataInAttributeList(0,3,vertices);
        storeDataInAttributeList(1,4,colors);
        storeDataInAttributeList(2,3,normals);
        storeDataInAttributeList(3, 2, uvCoordinates);

        GL30.glBindVertexArray(0);
        return new TexturedModel( vaoId, indices.length, texture);
    }

    private void bindIndices(int[] indices){
        int vboId = createVbo();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboId);

        IntBuffer buffer = BufferUtils.createIntBuffer(indices.length);

        buffer.put(indices);
        buffer.flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);



    }
    private void storeDataInAttributeList(int attributeNr, int size, float[] data){
        int vboId = createVbo();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer( attributeNr, size, GL11.GL_FLOAT, false, 0, 0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    private int createVao() {
        int vaoId = GL30.glGenVertexArrays();
        vaoIdList.add(vaoId);
        return vaoId;
    }
    private int createVbo() {
        int vboId = GL15.glGenBuffers();
        vboIdList.add(vboId);
        return vboId;
    }

    public void exit() {
        for (Integer id : vaoIdList){
            GL30.glDeleteVertexArrays(id);
        }
        for (Integer id : vboIdList){
            GL30.glDeleteBuffers(id);
        }
    }
}
