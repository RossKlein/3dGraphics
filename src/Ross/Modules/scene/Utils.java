package Ross.Modules.scene;

import Ross.Modules.input.InputHandler;
import Ross.Modules.math.Vec4f;
import Ross.Modules.models.Model;
import Ross.Modules.models.ModelBuilder;
import Ross.Modules.models.OBJloader;
import Ross.Modules.models.OBJobject;

public class Utils {

    private double yoff, oldoff, xvelold, yvelold;
    public double xvel, yvel;
    public double fov;
    private InputHandler inputHandler;

    public Utils() {


        yoff = oldoff = fov = xvelold = yvelold = 0;
        xvel = yvel = 0;
    }

    public void updateInput() {
        ////////////// fov
        yoff = inputHandler.getYoffset();

        if(yoff-oldoff == 0){
            oldoff = 0;
        }else{
            oldoff = 5*yoff;
            fov = fov - 0.1f* (float) oldoff;
        }

        oldoff = inputHandler.getYoffset();
        ///////////////////
        //////// mouse movement

        xvel = inputHandler.getXcursor();
        yvel = inputHandler.getYcursor();

        //clamp it to stop crazy initial movement
        //may be a problem if we want really fast mouse movement?
        //may be a problem on desktop?
        xvel = (xvel - xvelold) > 200 ? 200 : (xvel - xvelold);
        yvel = (yvel - yvelold) > 200 ? 200 : (yvel - yvelold);

        xvelold = inputHandler.getXcursor();
        yvelold = inputHandler.getYcursor();

    }

    public Model buildModel(String filename) {
        OBJloader obj = new OBJloader(filename);
        OBJobject objModel = obj.returnOBJobject();
        ModelBuilder modelBuilder = new ModelBuilder();

        float[] vertices = objModel.getVertices();

        int[] indices = objModel.getIndices();

        float[] colors = obj.genColor(new Vec4f(0, 1, 0, 1));
        float[] normals = objModel.getNormals();
        return modelBuilder.buildModel(vertices, indices, colors, normals);
    }





    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public void setInputHandler(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }
}
