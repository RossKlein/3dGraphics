package main.math;

public class Vec4f {

    private float x, y, z, w;

    public Vec4f ( float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
    public Vec4f (float[] xyzw) {
        this.x = xyzw[0];
        this.y = xyzw[1];
        this.z = xyzw[2];
        this.w = xyzw[3];
    }
    public Vec4f (Vec3f vec3) {
        this.x = vec3.x();
        this.y = vec3.y();
        this.z = vec3.z();
        this.w = 1;
    }
    public Vec4f (Vec3f vec3, float w) {
        this.x = vec3.x();
        this.y = vec3.y();
        this.z = vec3.z();
        this.w = w;
    }

    public float x() {
        return x;
    }

    public void x(float x) {
        this.x = x;
    }

    public float y() {
        return y;
    }

    public void y(float y) {
        this.y = y;
    }

    public float z() {
        return z;
    }

    public void z(float z) {
        this.z = z;
    }

    public float w() {
        return w;
    }

    public void w(float w) {
        this.w = w;
    }

    public Vec4f sub(Vec4f l, Vec4f r){
        this.x = l.x() - r.x();
        this.y = l.y() - r.y();
        this.z = l.z() - r.z();
        this.w = l.w() - r.w();

        return this;
    }
    public Vec4f normalize() {
        this.x = (float) (this.x/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z + this.w*this.w));
        this.y = (float) (this.y/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z + this.w*this.w));
        this.z = (float) (this.z/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z + this.w*this.w));
        this.w = (float) (this.w/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z + this.w*this.w));

        return this;
    }
}
