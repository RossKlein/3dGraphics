package Ross.Modules.math;

import org.lwjgl.system.CallbackI;

public class Quaternion {

    public float x, y, z, w;
    public Quaternion() {
        this.x = this.y = this.z = 0;
        this.w = 1;
        //quaternion 0, 0, 0, 1 represents zero rotation
    }
    public Quaternion(Vec3f vec) {

        this.x = vec.x;
        this.y = vec.y;
        this.z = vec.z;
        this.w = 0;

    }
    public Quaternion(Vec3f vec, float w) {

        this.x = vec.x;
        this.y = vec.y;
        this.z = vec.z;
        this.w = w;
    }
    public Quaternion(Vec4f vec) {

        this.x = vec.x;
        this.y = vec.y;
        this.z = vec.z;
        this.w = vec.w;
    }
    public Quaternion(float x, float y, float z, float w) {

        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }



    public Quaternion rotation(Vec3f vec, float theta) {
        Vec3f newvec = vec.mult((float)Math.sin(Math.toRadians(theta/2)));
        return new Quaternion(newvec, (float)Math.cos(Math.toRadians(theta/2)));
    }
    public Quaternion rotation(float x, float y, float z, float theta) {
        Vec3f vec = new Vec3f(x, y, z);

        Vec3f newvec = vec.mult((float)Math.sin(Math.toRadians(theta/2)));

        return new Quaternion(newvec, ((float)Math.cos(Math.toRadians(theta/2))));
    }
    public Vec3f rotate(Quaternion rotation, Vec3f vec) {

        // v' = q x v x q-1
        // v' = q x v x q*
        //using conjugate form because it is less computation
        //might run into normalization problems if the rotation quaternion is not a unit quaternion

        Quaternion premult = rotation.mult(rotation, new Quaternion(vec));
        Quaternion postmult = rotation.mult(premult, rotation.conjugate());

        return new Vec3f(postmult);
    }

    public Quaternion mult(Quaternion left, Quaternion right) {
        Vec3f vec = new Vec3f();
        Vec3f pv = new Vec3f(left);
        Vec3f qv = new Vec3f(right);
        float ps = left.w;
        float qs = right.w;

        return new Quaternion(vec.add(vec.add(qv.mult(ps), pv.mult(qs)), vec.cross(pv, qv) ), ps*qs-vec.dot(pv,qv));

    }

    public Quaternion conjugate() {

        Vec3f xyz = new Vec3f(this);
        xyz = xyz.mult(-1);
        return new Quaternion(xyz, this.w);
    }

    public Quaternion inverse() {

        Quaternion conjugate = this.conjugate();
        double magnitudesquared = this.x*this.x + this.y*this.y + this.z*this.z + this.w*this.w;

        return conjugate.scalarMult((float)(1/magnitudesquared));
    }
    public Quaternion normalize() {
        double magnitude = Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z + this.w*this.w);

        return this.scalarMult((float)(1/magnitude));
    }

    public Quaternion scalarMult(float v) {
        return new Quaternion(this.x*v, this.y*v, this.z*v, this.w*v);

    }

    public Mat4f toMatrix() {
        Quaternion q = this;
        Mat4f mat = new Mat4f().identity();
        mat.m00(1-2*q.y*q.y-2*q.z*q.z);
        mat.m01(2*q.x*q.y + 2*q.z*q.w);
        mat.m02(2*q.x*q.z - 2*q.y*q.w);

        mat.m10(2*q.x*q.y - 2*q.z*q.w);
        mat.m11(1-2*q.x*q.x-2*q.z*q.z);
        mat.m12(2*q.y*q.z + 2*q.x*q.w);

        mat.m20(2*q.x*q.z + 2*q.y*q.w);
        mat.m21(2*q.y*q.z - 2*q.x*q.w);
        mat.m22(1 - 2*q.x*q.x - 2*q.y*q.y);

        return mat;
    }


    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    public float w() {
        return w;
    }

    public void x(float x) {
        this.x = x;
    }

    public void y(float y) {
        this.y = y;
    }

    public void z(float z) {
        this.z = z;
    }

    public void w(float w) {
        this.w = w;
    }


    public void print(){
//        System.out.println("(" + this.x() + ", " + this.y() + ", " + this.z() + ", " + this.w() + ")");
        System.out.print("( ");
        System.out.format("%.2f, ", this.x);
        System.out.format("%.2f, ", this.y);
        System.out.format("%.2f, ", this.z);
        System.out.format("%.2f, ", this.w);
        System.out.println(" )");



    }


}
