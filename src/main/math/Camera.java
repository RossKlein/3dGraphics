package main.math;

public class Camera {

    private Vec3f position;
    private Vec3f rotation;

    public Camera () {
        position = new Vec3f(0,0,0);
        rotation = new Vec3f(0,0,0);

    }

    public Camera (Vec3f position, Vec3f rotation) {
        this.position = position;
        this.rotation = rotation;

    }

    public Vec3f getPosition() {
        return position;
    }

    public void setPosition(float x, float y, float z) {
        this.position.x(x);
        this.position.y(y);
        this.position.z(z);
    }

    public Vec3f getRotation() {
        return rotation;
    }

    public void setRotation(float x, float y, float z) {
        this.rotation.x(x);
        this.rotation.y(y);
        this.rotation.z(z);
    }


    public void rotate(float offsetX, float offsetY, float offsetZ) {
        rotation.x(rotation.x() + offsetX);
        rotation.y(rotation.y() + offsetY);
        rotation.z(rotation.z() + offsetZ);
    }
}
