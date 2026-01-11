package Ross.Modules.flamegraph;

import Ross.Modules.models.Model;
import Ross.Modules.models.ModelBuilder;

import java.util.ArrayList;
import java.util.List;

public class FlameRect {
    public final float x, y, width, height;
    public final String jobName;
    public final float[] color;

    public FlameRect(float x, float y, float width, float height, String jobName, float[] color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.jobName = jobName;
        this.color = color;
    }

    public float[] getVertices() {
        return new float[]{
                x, y, 0,
                x + width, y, 0,
                x + width, y + height, 0,
                x, y + height, 0
        };
    }

    public int[] getIndices(int baseIndex) {
        return new int[]{
                baseIndex, baseIndex + 1, baseIndex + 2,
                baseIndex, baseIndex + 2, baseIndex + 3
        };
    }

    public float[] getColors() {
        float r = color[0], g = color[1], b = color[2], a = color[3];
        return new float[] {
                r, g, b, a,
                r, g, b, a,
                r, g, b, a,
                r, g, b, a
        };
    }

    public float[] getNormals() {
        return new float[]{
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        };
    }

    public static Model buildFlamegraphModel(List<FlameRect> rects, ModelBuilder builder) {

        int numRects = rects.size();

        float[] vertices = new float[numRects * 4 * 3]; // 4 verts × 3 coords
        int[] indices = new int[numRects * 6];          // 2 triangles × 3 indices
        float[] colors = new float[numRects * 4 * 4];   // RGBA per vertex
        float[] normals = new float[numRects * 4 * 3];  // Z-normal per vertex

        int vi = 0;  // vertex float index
        int ii = 0;  // index int index
        int ci = 0;  // color float index
        int ni = 0;  // normal float index
        int baseVertex = 0;

        for (FlameRect rect : rects) {
            float[] v = rect.getVertices();
            int[] i = rect.getIndices(baseVertex);
            float[] c = rect.getColors();
            float[] n = rect.getNormals();

            System.arraycopy(v, 0, vertices, vi, v.length);
            System.arraycopy(i, 0, indices, ii, i.length);
            System.arraycopy(c, 0, colors, ci, c.length);
            System.arraycopy(n, 0, normals, ni, n.length);

            vi += v.length;
            ii += i.length;
            ci += c.length;
            ni += n.length;
            baseVertex += 4;
        }

        return builder.buildModel(vertices, indices, colors, normals);
    }


}
