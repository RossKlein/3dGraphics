package Ross;

import Ross.textures.Texture;
import Ross.textures.TextureLoader;

public class ResourceLoader {

    private TextureLoader textureLoader;

    public ResourceLoader() {

        textureLoader = new TextureLoader();

    }

    public Texture loadTexture(String filepath) {
        return textureLoader.loadTexture(filepath);
    }


    public void exit() {
        textureLoader.exit();

    }

}