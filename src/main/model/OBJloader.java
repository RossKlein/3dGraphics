package main.model;

import main.math.Vec3f;
import main.math.Vec4f;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class OBJloader {

    public float[] vertices;
    public float[] uvCoordinates;
    public float[] normals;
    public float[] color;
    public int[] indices;
    private InputStream is;


    public OBJloader(String file) {
        is = null;
        try {
            is = new FileInputStream(new File(file));
        } catch (FileNotFoundException e) {
            System.out.println("file " + file + " not found");
        }


    }
    public OBJobject returnOBJobject() {
        OBJobject object = new OBJobject();
        List<Vec3f> verts = new ArrayList<>();
        List<Integer> inds = new ArrayList<>();
        List<Vec3f> norms = new ArrayList<>();
        List<Vec3f> uvs = new ArrayList<>();
//////////////////////////
        readFromFile(verts, inds, norms, uvs);

        vertices = new float[verts.size()*3];
        indices = new int[inds.size()];
        int c = 0;
        for(Vec3f i : verts){
            vertices[c] = i.x();
            vertices[c+1] = i.y();
            vertices[c+2] = i.z();
            c += 3;
        }
        c = 0;
        for(Integer i : inds){
            indices[c++] = i;
        }

        object.setVertices(vertices);
        object.setIndices(indices);
        object.setUvCoordinates(uvCoordinates);
        object.setNormals(normals);




        return object;
    }



    private void readFromFile(List<Vec3f> verts, List<Integer> inds, List<Vec3f> norms, List<Vec3f> uvs) {
        try(BufferedReader obj = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = obj.readLine()) != null) {

                if (line.contains("  ")){
                    line = line.replaceAll("  ", " ");
                }
                String[] currentLine = line.split(" ");
                if (line.startsWith("v ")) {
                    Vec3f vert = new Vec3f(Float.parseFloat(currentLine[1]), Float.parseFloat(currentLine[2]), Float.parseFloat(currentLine[3]));
                    verts.add(vert);
                } else if(line.startsWith("vt ")){
                    Vec3f vt = new Vec3f(Float.parseFloat(currentLine[1]), Float.parseFloat(currentLine[2]), 0);
                    uvs.add(vt);
                } else if(line.startsWith("vn ")){
                    Vec3f vn = new Vec3f(Float.parseFloat(currentLine[1]), Float.parseFloat(currentLine[2]), Float.parseFloat(currentLine[3]));
                    norms.add(vn);
                } else if (line.startsWith("f ")) {
                    if(line.split(" ").length > 4){
                        uvCoordinates = new float[verts.size()*4];
                        normals = new float[verts.size()*6];
                    }else{

                        uvCoordinates = new float[verts.size()*2];
                        normals = new float[verts.size()*3];
                    }
                    break;
                }
            }

            while (line != null) {
                if(!line.startsWith("f ")){
                    line = obj.readLine();
                    continue;
                }

                String[] currentLine = line.split(" ");
                String[] vertex1 = currentLine[1].split("/");
                String[] vertex2 = currentLine[2].split("/");
                String[] vertex3 = currentLine[3].split("/");

                if(currentLine.length > 4){
                    String[] vertex4 = currentLine[4].split("/");
                    processIndices3v(vertex1, inds, norms, uvs);
                    processIndices3v(vertex2, inds, norms, uvs);
                    processIndices3v(vertex3, inds, norms, uvs);

                    processIndices3v(vertex1, inds, norms, uvs);
                    processIndices3v(vertex3, inds, norms, uvs);
                    processIndices3v(vertex4, inds, norms, uvs);
                } else{

                    processIndices3v(vertex1,inds, norms, uvs);
                    processIndices3v(vertex2,inds, norms, uvs);
                    processIndices3v(vertex3,inds, norms, uvs);
                }
                line = obj.readLine();
            }

        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processIndices3v(String[] vertexData, List<Integer> inds, List<Vec3f> norms, List<Vec3f> uvs) {

        int currentVertexPos = Integer.parseInt(vertexData[0])-1;
        inds.add(currentVertexPos);

        if(uvs.size() != 0){
            Vec3f currentUv = uvs.get(Integer.parseInt(vertexData[1])-1);
            uvCoordinates[currentVertexPos*2] = currentUv.x();
            uvCoordinates[currentVertexPos*2+1] = 1- currentUv.y();
        }

        if(norms.size() != 0){

            Vec3f currentNorm = norms.get(Integer.parseInt(vertexData[2])-1);
            normals[currentVertexPos*3] = currentNorm.x();
            normals[currentVertexPos*3+1] = currentNorm.y();
            normals[currentVertexPos*3+2] = currentNorm.z();
        }else {

        }
    }

    public float[] genColor(Vec4f colors){
        color = new float[(vertices.length*8/3)];
        for(int x = 0; x< vertices.length*2; x+=4){
            color[x] = colors.x();
            color[x+1] = colors.y();
            color[x+2] = colors.z();
            color[x+3] = colors.w();
        }

        return color;
    }


}