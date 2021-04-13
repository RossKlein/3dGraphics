package test;

import main.math.Vec3f;
import main.math.Vec4f;

import java.util.ArrayList;
import java.util.List;

public class DebugNormals {

    public float[] processedVertices;
    public int[] indices;
    public float[] processedColors;
    public List<Vec3f> norms;
    public List<Vec3f> verts;
    public List<Vec4f> colors;

    public List<Vec3f> newverts;
    public List<Integer> newinds;

    public DebugNormals() {
        norms = new ArrayList<>();
        verts = new ArrayList<>();
        colors = new ArrayList<>();
        newverts = new ArrayList<>();
        newinds = new ArrayList<>();
    }

    public void lineMaker(float[] normals, float[] vertices, int[] ind){

        for(int x=0;x<normals.length;x+=3){
            norms.add(new Vec3f(normals[x], normals[x+1], normals[x+2]));
        }
        for(int x=0;x<vertices.length;x+=3){
            verts.add(new Vec3f(vertices[x], vertices[x+1], vertices[x+2]));
        }

        processedVertices = new float[ind.length*9];
        processedColors = new float[ind.length*16];
        indices = new int[ind.length*3];
        int c = 0;
        for(int i = 0; i < ind.length; i++){
            Vec3f curvert = verts.get(ind[i]);
            Vec3f curnormvert = new Vec3f().add(curvert, norms.get(ind[i]).mult(1));
            newverts.add(curvert);
            newverts.add(curnormvert);
            newverts.add(new Vec3f().add(curvert, new Vec3f(0, 0, 0)));
            colors.add(new Vec4f(1f, 0, 0f, 1));
            colors.add(new Vec4f(1f, 0, 0f, 1));
            colors.add(new Vec4f(1f, 0, 0f, 1));
            colors.add(new Vec4f(1f, 0, 0f, 1));
            newinds.add(c);
            newinds.add(c+1);
            newinds.add(c+2);
            c += 3;

        }
        c = 0;
        for(Vec3f i : newverts){
            processedVertices[c] = i.x();
            processedVertices[c+1] = i.y();
            processedVertices[c+2] = i.z();
            c +=3;
        }
        c = 0;
        for(Vec4f i : colors){
            processedColors[c] = i.x();
            processedColors[c+1] = i.y();
            processedColors[c+2] = i.z();
            processedColors[c+3] = i.w();
            c +=4;
        }
        c = 0;
        for(Integer i : newinds){
            indices[c++] = i;
        }



    }
    public float[] getProcessedVertices() {
        return processedVertices;
    }
    public int[] getIndices() {
        return indices;
    }
    public float[] getProcessedColors() {
        return processedColors;
    }
}
