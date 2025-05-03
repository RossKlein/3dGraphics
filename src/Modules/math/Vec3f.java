package Modules.math;

import org.lwjgl.system.CallbackI;

public class Vec3f {
    public float x, y, z;

    public Vec3f() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }
    public Vec3f(Vec4f v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }
    public Vec3f(Vec3f v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }
    public Vec3f(Quaternion v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
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

        return new Vec3f(l.x - r.x, l.y - r.y, l.z - r.z);
    }
    public Vec3f add(Vec3f l, Vec3f r){

        return new Vec3f(l.x + r.x, l.y + r.y, l.z + r.z);
    }
    public Vec3f add(Vec3f l, float r){

        return new Vec3f(l.x + r, l.y + r, l.z + r);
    }
    public float dot(Vec3f l, Vec3f r) {
        return l.x*r.x + l.y*r.y + l.z*r.z;
    }

    public Vec3f mult(float r) {

        return new Vec3f(this.x*r, this.y*r, this.z*r);
    }
    public Vec3f normalize() {

        return new Vec3f((float) (this.x/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z)),
                (float) (this.y/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z)),
                (float) (this.z/Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z)));
    }
    public Vec3f cross(Vec3f l, Vec3f r) {

        return new Vec3f(l.y*r.z - l.z*r.y,
                l.z*r.x - l.x*r.z,
                l.x*r.y - l.y*r.x);

    }
    public void print(){
        System.out.println("(" + this.x() + ", " + this.y() + ", " + this.z() + ")");
    }

}
