package Ross.Modules.models;

import Ross.ResourceLoader;
import Ross.textures.Texture;
import Ross.textures.TextureLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE;

public class ModelLoader {



    ResourceLoader loader;

    public ModelLoader () {

        loader = new ResourceLoader();

    }


    public TexturedModel buildASSIMPmodel(String modelPath, String texturePath){
        ModelBuilder modelBuilder = new ModelBuilder();

        AIScene scene = aiImportFile(modelPath, aiProcess_JoinIdenticalVertices | aiProcess_Triangulate | aiProcess_GenSmoothNormals | aiProcess_FlipUVs);


        if (scene == null || (scene.mFlags() & AI_SCENE_FLAGS_INCOMPLETE) != 0 || scene.mRootNode() == null) {
            throw new RuntimeException("Failed to load model: " + aiGetErrorString());
        }

//        AINode rootNode = scene.mRootNode();
        PointerBuffer meshIndices = scene.mMeshes();

        if (meshIndices == null || meshIndices.capacity() == 0) {
            throw new RuntimeException("No meshes found in model.");
        }

        AIMesh mesh = AIMesh.create(scene.mMeshes().get(0)); // Assuming one mesh
        int vertexCount = mesh.mNumVertices();

        float[] vertices = extractVec3Array(mesh.mVertices(), vertexCount);
        float[] normals = extractVec3Array(mesh.mNormals(), vertexCount);
        float[] uvs = mesh.mTextureCoords(0) != null
                ? extractVec2Array(mesh.mTextureCoords(0), vertexCount)
                : new float[vertexCount * 2];
        int[] indices = extractIndices(mesh.mFaces());

        float[] colors = new float[vertexCount * 4];
        for (int i = 0; i < vertexCount; i++) {
            colors[i * 4] = 0;
            colors[i * 4 + 1] = 1;
            colors[i * 4 + 2] = 0;
            colors[i * 4 + 3] = 1;
        }


        Texture texture = loader.loadTexture(texturePath);
        return modelBuilder.buildModel(vertices, indices, colors, normals, uvs, texture);
    }

    public ArrayList<TexturedModel> buildASSIMPmodelMultiple(String modelPath, String textureDirectory){
        Assimp.aiEnableVerboseLogging(true);

        ModelBuilder modelBuilder = new ModelBuilder();
        ArrayList<TexturedModel> allModels = new ArrayList<>();
        AIScene scene = aiImportFile(modelPath, aiProcess_JoinIdenticalVertices | aiProcess_Triangulate | aiProcess_GenSmoothNormals | aiProcess_FlipUVs);


        PointerBuffer meshBuffer = scene.mMeshes();
        PointerBuffer materialBuffer = scene.mMaterials();
        for (int i = 0; i < scene.mNumMeshes(); i++) {
            AIMesh mesh = AIMesh.create(meshBuffer.get(i));

            // Load geometry
            int vertexCount = mesh.mNumVertices();

            float[] vertices = extractVec3Array(mesh.mVertices(), vertexCount);
            float[] normals = extractVec3Array(mesh.mNormals(), vertexCount);
            float[] uvs = mesh.mTextureCoords(0) != null
                    ? extractVec2Array(mesh.mTextureCoords(0), vertexCount)
                    : new float[vertexCount * 2];
            int[] indices = extractIndices(mesh.mFaces());

            // Get material and texture
            int materialIndex = mesh.mMaterialIndex();
            AIMaterial material = AIMaterial.create(materialBuffer.get(materialIndex));

            AIString path = AIString.calloc();
            aiGetMaterialTexture(material, aiTextureType_DIFFUSE, 0, path, (IntBuffer) null, null, null, null, null, null);
            String texturePath = path.dataString();
            Texture texture = null;
            if(texturePath == ""){

                System.out.println("no texture");
            } else if (texturePath.startsWith("*")) {
                texture = loadExternalTexture(scene, texturePath);
            } else {
                texture = loader.loadTexture(textureDirectory + texturePath);
            }


            float[] colors = new float[vertexCount * 4];
            for (int j = 0; j < vertexCount; j++) {
                colors[j * 4] = 0;
                colors[j * 4 + 1] = 1;
                colors[j * 4 + 2] = 0;
                colors[j * 4 + 3] = 1;
            }
            // Build submodel
            TexturedModel subModel = modelBuilder.buildModel(vertices, indices, colors, normals, uvs, texture);
            allModels.add(subModel);
        }
        return allModels;
    }

    public Texture loadExternalTexture(AIScene scene, String texturePath){
        int texIndex = Integer.parseInt(texturePath.substring(1));
        AITexture texture = AITexture.create(scene.mTextures().get(texIndex));
        TextureLoader loader = new TextureLoader();

        Texture mytexture = loader.loadTexture(texture);
        return mytexture;
    }


    public static float[] extractVec3Array(AIVector3D.Buffer buffer, int count) {
        float[] result = new float[count * 3];
        for (int i = 0; i < count; i++) {
            AIVector3D vec = buffer.get(i);
            int base = i * 3;
            result[base] = vec.x();
            result[base + 1] = vec.y();
            result[base + 2] = vec.z();
        }
        return result;
    }

    public static float[] extractVec2Array(AIVector3D.Buffer buffer, int count) {
        float[] result = new float[count * 2];
        for (int i = 0; i < count; i++) {
            AIVector3D vec = buffer.get(i);
            int base = i * 2;
            result[base] = vec.x();
            result[base + 1] = vec.y();
        }
        return result;
    }

    public static int[] extractIndices(AIFace.Buffer faces) {
        List<Integer> indexList = new ArrayList<>();
        for (int i = 0; i < faces.remaining(); i++) {
            AIFace face = faces.get(i);
            for (int j = 0; j < face.mNumIndices(); j++) {
                indexList.add(face.mIndices().get(j));
            }
        }
        return indexList.stream().mapToInt(Integer::intValue).toArray();
    }

}
