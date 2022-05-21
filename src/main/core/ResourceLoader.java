package main.core;

import textures.Texture;
import textures.TextureLoader;

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
