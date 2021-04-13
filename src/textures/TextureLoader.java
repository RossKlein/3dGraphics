package textures;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;

import java.io.FileInputStream;
import java.io.IOException;
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

    private ByteBuffer readImageFile(String filepath) throws URISyntaxException, IOException {
        FileInputStream fis = new FileInputStream( TextureLoader.class.getResource(filepath).toURI().getPath());
        FileChannel fc = fis.getChannel();
        ByteBuffer buffer = BufferUtils.createByteBuffer( (int)(fc.size()+1));

        while(fc.read( buffer) != -1){

        }
        fis.close();
        fc.close();
        buffer.flip();
        return buffer;
    }

    public void exit(){
        for( Integer integer : textureIdList) {
            GL11.glDeleteTextures(integer);
        }
    }
}
