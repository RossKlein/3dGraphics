package main.math;

public class Vec3f {
    private float x, y, z;

    public Vec3f() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }
    public Vec3f(Vec4f v) {
        this.x = v.x();
        this.y = v.y();
        this.z = v.z();
    }
    public Vec3f(Vec3f v) {
        this.x = v.x();
        this.y = v.y();
        this.z = v.z();
    }
    public Vec3f ( float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public Vec3f (float[] xyz) {
        this.x = xyz[0];
        this.y = xyz[1];
        this.z = xyz[2];
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

    public Vec3f sub(Vec3f l, Vec3f r){
        this.x = l.x() - r.x();
        this.y = l.y() - r.y();
        this.z = l.z() - r.z();

        return this;
    }
    public Vec3f add(Vec3f l, Vec3f r){
        this.x = l.x() + r.x();
        this.y = l.y() + r.y();
        this.z = l.z() + r.z();

        return this;
    }
    public float dot(Vec3f l, Vec3f r) {
        return l.x()*r.x() + l.y()*r.y() + l.z()*r.z();
    }

    public Vec3f mult(float r) {
        this.x = this.x*r;
        this.y = this.y*r;
        this.z = this.z*r;

        return this;
    }
    public Vec3f normalize() {
        this.x = (float) (this.x/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z));
        this.y = (float) (this.y/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z));
        this.z = (float) (this.z/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z));

        return this;
    }
    public Vec3f cross(Vec3f l, Vec3f r) {
        this.x = l.y()*r.z() - l.z()*r.y();
        this.y = l.z()*r.x() - l.x()*r.z();
        this.z = l.x()*r.y() - l.y()*r.x();

        return this;

    }
    public void print(){
        System.out.println("(" + this.x() + ", " + this.y() + ", " + this.z() + ")");
    }

}
