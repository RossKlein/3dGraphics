package Ross.Modules.scenegraph;

import Ross.Modules.models.Model;
import Ross.Modules.models.utils.Transformation;

public class RenderableNode {


    Model model;
    Transformation transformation;
    public RenderableNode (Model model, Transformation transformation) {


        this.model = model;
        this.transformation = transformation;


    }

    public void doTransformation() {

        // apply transformation to model
    }




}
