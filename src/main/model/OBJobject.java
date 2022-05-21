package main.model;

public class OBJobject {

    public float[] vertices;
    public int[] indices;
    public float[] normals;
    public float[] uvCoordinates;

    public OBJobject() {

    }
    public OBJobject (float[] vertices, int[] indices, float[] normals, float[] uvCoordinates) {
        this.vertices = vertices;
        this.indices = indices;
        this.normals = normals;
        this.uvCoordinates = uvCoordinates;
    }

    public float[] getVertices() {
        return vertices;
    }

    public void setVertices(float[] vertices) {
        this.vertices = vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public void setIndices(int[] indices) {
        this.indices = indices;
    }

    public float[] getNormals() {
        return normals;
    }

    public void setNormals(float[] normals) {
        this.normals = normals;
    }

    public float[] getUvCoordinates() {
        return uvCoordinates;
    }

    public void setUvCoordinates(float[] uvCoordinates) {
        this.uvCoordinates = uvCoordinates;
    }
}
