package Ross.textures;

import org.lwjgl.BufferUtils;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class TextureLoader {

    private static ArrayList<Integer> textureIdList = new ArrayList<>();
    public TextureLoader() {
    }

    public Texture loadTexture(String filepath) {
        IntBuffer w = BufferUtils.createIntBuffer(1);
        IntBuffer h = BufferUtils.createIntBuffer(1);
        IntBuffer c = BufferUtils.createIntBuffer(1);

        ByteBuffer imageBuffer = null;
        try {
            imageBuffer = STBImage.stbi_load_from_memory(readImageFile(filepath),w,h,c,0);
            if(imageBuffer==null){
                throw new IllegalStateException("couldnt load image file" + filepath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        int width = w.get(0);
        int height = h.get(0);
        int comp = c.get(0);
        int textureId = GL11.glGenTextures();
        textureIdList.add( textureId);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        if (comp == 3){
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB,width,height, 0, GL11.GL_RGB,GL11.GL_UNSIGNED_BYTE, imageBuffer);
        } else {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,width,height, 0, GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE, imageBuffer);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        return new Texture(textureId, width, height);

    }

    public Texture loadTexture(AITexture texture){

        boolean isCompressed = texture.mHeight() == 0;
        int width = 0;
        int height = 0;
        int channels = 0;

        ByteBuffer image = null;
        if(isCompressed) {

            // Compressed data
            ByteBuffer imageBytes = texture.pcDataCompressed(); // Newer Assimp versions


// Decode image using STB
            IntBuffer w = BufferUtils.createIntBuffer(1);
            IntBuffer h = BufferUtils.createIntBuffer(1);
            IntBuffer comp = BufferUtils.createIntBuffer(1);
            image = STBImage.stbi_load_from_memory(imageBytes, w, h, comp, 0);
            if (image == null) throw new RuntimeException("Failed to decode compressed embedded image");

            width = w.get(0);
            height = h.get(0);
            channels = comp.get(0);

        } else {
            width = texture.mWidth();
            height = texture.mHeight();
            ByteBuffer pixelBuffer = BufferUtils.createByteBuffer(width * height * 4);
            AITexel.Buffer texel = texture.pcData();

            for (int i = 0; i < width * height; i++) {
                pixelBuffer.put((byte)(texel.r() * 255f));
                pixelBuffer.put((byte)(texel.g() * 255f));
                pixelBuffer.put((byte)(texel.b() * 255f));
                pixelBuffer.put((byte)(texel.a() * 255f)); // alpha
            }
            pixelBuffer.flip();
            channels = 4;
            image = pixelBuffer;

        }
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        textureIdList.add(textureId);


        int format = (channels == 3) ? GL11.GL_RGB : GL11.GL_RGBA;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, format, GL11.GL_UNSIGNED_BYTE, image);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);



        return new Texture(textureId, width, height);

    }

    private ByteBuffer readImageFile(String filepath) throws URISyntaxException, IOException {
        InputStream is = null;

        try {
            is = new FileInputStream(new File(filepath));
            System.out.println("Loading file " + filepath);
        } catch (FileNotFoundException e) {
            System.out.println("file " + filepath + " not found");
        }

        if (is == null) {
            throw new FileNotFoundException("Resource not found: " + filepath);
        }
        ByteBuffer buffer = BufferUtils.createByteBuffer(is.available() + 1);
        byte[] data = is.readAllBytes();
        buffer.put(data);
        buffer.flip();
        is.close();
        return buffer;

    }

    public void exit(){
        for( Integer integer : textureIdList) {
            GL11.glDeleteTextures(integer);
        }
    }
}
