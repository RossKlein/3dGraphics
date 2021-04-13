package main.math;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Vector;

public class Mat4f {

    float m00, m01, m02, m03;
    float m10, m11, m12, m13;
    float m20, m21, m22, m23;
    float m30, m31, m32, m33;

    public Mat4f () { }
    public Mat4f (float[] matrix) {
        matrix[0] = this.m00;
        matrix[1] = this.m01;
        matrix[2] = this.m02;
        matrix[3] = this.m03;
        matrix[4] = this.m10;
        matrix[5] = this.m11;
        matrix[6] = this.m12;
        matrix[7] = this.m13;
        matrix[8] = this.m20;
        matrix[9] = this.m21;
        matrix[10] = this.m22;
        matrix[11] = this.m23;
        matrix[12] = this.m30;
        matrix[13] = this.m31;
        matrix[14] = this.m32;
        matrix[15] = this.m33;

    }
    public Mat4f (Vec4f col0, Vec4f col1, Vec4f col2, Vec4f col3) {
        this.m00 = col0.x();
        this.m10 = col0.y();
        this.m20 = col0.z();
        this.m30 = col0.w();
        this.m01 = col1.x();
        this.m11 = col1.y();
        this.m21 = col1.z();
        this.m31 = col1.w();
        this.m02 = col2.x();
        this.m12 = col2.y();
        this.m22 = col2.z();
        this.m32 = col2.w();
        this.m03 = col3.x();
        this.m13 = col3.y();
        this.m23 = col3.z();
        this.m33 = col3.w();

    }
    public Mat4f (Vec3f col0, Vec3f col1, Vec3f col2, Vec3f col3) {
        this.m00 = col0.x();
        this.m10 = col0.y();
        this.m20 = col0.z();
        this.m30 = 1;
        this.m01 = col1.x();
        this.m11 = col1.y();
        this.m21 = col1.z();
        this.m31 = 1;
        this.m02 = col2.x();
        this.m12 = col2.y();
        this.m22 = col2.z();
        this.m32 = 1;
        this.m03 = col3.x();
        this.m13 = col3.y();
        this.m23 = col3.z();
        this.m33 = 1;

    }
    public float m00() {
        return m00;
    }
    public float m01() {
        return m01;
    }
    public float m02() {
        return m02;
    }
    public float m03() {
        return m03;
    }
    public float m10() {
        return m10;
    }
    public float m11() {
        return m11;
    }
    public float m12() {
        return m12;
    }
    public float m13() {
        return m13;
    }
    public float m20() {
        return m20;
    }
    public float m21() {
        return m21;
    }
    public float m22() {
        return m22;
    }
    public float m23() {
        return m23;
    }
    public float m30() {
        return m30;
    }
    public float m31() {
        return m31;
    }
    public float m32() {
        return m32;
    }
    public float m33() {
        return m33;
    }

    public void m00(float m00) {
        this.m00 = m00;
    }

    public void m01(float m01) {
        this.m01 = m01;
    }

    public void m02(float m02) {
        this.m02 = m02;
    }

    public void m03(float m03) {
        this.m03 = m03;
    }

    public void m10(float m10) {
        this.m10 = m10;
    }

    public void m11(float m11) {
        this.m11 = m11;
    }

    public void m12(float m12) {
        this.m12 = m12;
    }

    public void m13(float m13) {
        this.m13 = m13;
    }

    public void m20(float m20) {
        this.m20 = m20;
    }

    public void m21(float m21) {
        this.m21 = m21;
    }

    public void m22(float m22) {
        this.m22 = m22;
    }

    public void m23(float m23) {
        this.m23 = m23;
    }

    public void m30(float m30) {
        this.m30 = m30;
    }

    public void m31(float m31) {
        this.m31 = m31;
    }

    public void m32(float m32) {
        this.m32 = m32;
    }

    public void m33(float m33) {
        this.m33 = m33;
    }
    public void set(float[] matrix){
        this.m00(matrix[0]);
        this.m01(matrix[1]);
        this.m02(matrix[2]);
        this.m03(matrix[3]);
        this.m10(matrix[4]);
        this.m11(matrix[5]);
        this.m12(matrix[6]);
        this.m13(matrix[7]);
        this.m20(matrix[8]);
        this.m21(matrix[9]);
        this.m22(matrix[10]);
        this.m23(matrix[11]);
        this.m30(matrix[12]);
        this.m31(matrix[13]);
        this.m32(matrix[14]);
        this.m33(matrix[15]);

    }
    public void set(Mat4f matrix) {
        this.m00(matrix.m00());
        this.m01(matrix.m01());
        this.m02(matrix.m02());
        this.m03(matrix.m03());
        this.m10(matrix.m10());
        this.m11(matrix.m11());
        this.m12(matrix.m12());
        this.m13(matrix.m13());
        this.m20(matrix.m20());
        this.m21(matrix.m21());
        this.m22(matrix.m22());
        this.m23(matrix.m23());
        this.m30(matrix.m30());
        this.m31(matrix.m31());
        this.m32(matrix.m32());
        this.m33(matrix.m33());
    }
    public Mat4f identity() {

        float[] identity = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };
        this.set(identity);
        return this;
    }
    public Mat4f empty() {
        float[] empty = {
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
        };
        this.set(empty);
        return this;
    }

    public Mat4f projection(float FOV, float aspectRatio, float zNear, float zFar) {
        float zm = zFar - zNear;
        float zp = zFar + zNear;
        double fovInRadians = Math.toRadians((double) FOV/2);
        Mat4f result = new Mat4f().empty();
        result.m00((1/((float) Math.tan(fovInRadians)))/aspectRatio);
        result.m11((1/((float) Math.tan(fovInRadians))));
        result.m22(-1*zp/zm);
        result.m32(-1);
        result.m23((-2*zFar*zNear)/zm);
        return result;
    }

    public Mat4f view(Camera camera) {

        Vec3f cameraPos = camera.getPosition();
        Vec3f rotation = camera.getRotation();

        Mat4f matrix = new Mat4f();
        Mat4f matrix2 = new Mat4f();
        this.rotate(rotation.x(), 1, 0, 0);
        matrix.rotate(rotation.y(), 0, 1, 0);
        this.dot(this, matrix);
        matrix2 = matrix2.translation(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
        this.dot(this, matrix2);
        return this;
    }

    public Mat4f lookAt(Vec3f eye, Vec3f target, Vec3f upDir) {
//        Vec3f forward = new Vec3f().sub(eye, target);
//        forward.normalize();
//
//        Mat4f matrix = new Mat4f().identity();
//        Vec3f a = new Vec3f(forward).mult(new Vec3f().dot(upDir, forward));
//        Vec3f up = new Vec3f().sub(upDir, a);
//
//        Vec3f left = new Vec3f().cross(upDir, forward);
//        left.normalize();
        Vec3f forward = new Vec3f().sub(eye, target);
        forward.normalize();

        Mat4f matrix = new Mat4f().identity();

        Vec3f left = new Vec3f().cross(upDir, forward);
        left.normalize();
        Vec3f up = new Vec3f().cross(forward, left);
        matrix.m00(left.x());
        matrix.m01(left.y());
        matrix.m02(left.z());
        matrix.m10(up.x());
        matrix.m11(up.y());
        matrix.m12(up.z());
        matrix.m20(forward.x());
        matrix.m21(forward.y());
        matrix.m22(forward.z());

        matrix.m03(-1 * new Vec3f().dot(left, eye));
        matrix.m13(-1 * new Vec3f().dot(up, eye));
        matrix.m23(-1 * new Vec3f().dot(forward, eye));

        return matrix;

    }
    public Mat4f translation(float x, float y, float z){
        Mat4f matrix = new Mat4f().identity();

        matrix.m03(x);
        matrix.m13(y);
        matrix.m23(z);

        return matrix;
    }
    public Mat4f scale(float r){
        Mat4f matrix = new Mat4f().identity();

        matrix.m00(r);
        matrix.m11(r);
        matrix.m22(r);


        return matrix;
    }
    public Mat4f rotate(float a, int x, int y, int z){
        Mat4f matrix = new Mat4f().identity();
        double aInRadians = Math.toRadians((double) a);

        if(x != 0) {
            matrix.m11((float) Math.cos(aInRadians));
            matrix.m12(-1*(float) Math.sin(aInRadians));
            matrix.m21((float) Math.sin(aInRadians));
            matrix.m22((float) Math.cos(aInRadians));

        }
        if(y != 0) {
            matrix.m00((float) Math.cos(aInRadians));
            matrix.m02((float) Math.sin(aInRadians));
            matrix.m20(-1*(float) Math.sin(aInRadians));
            matrix.m22((float) Math.cos(aInRadians));
        }
        if(z != 0){
            matrix.m00((float) Math.cos(aInRadians));
            matrix.m01(-1*(float) Math.sin(aInRadians));
            matrix.m10((float) Math.sin(aInRadians));
            matrix.m11((float) Math.cos(aInRadians));
        }
        return matrix;

    }
    public void string(){
        float[] matrix0 = new float[4];
        float[] matrix1 = new float[4];
        float[] matrix2 = new float[4];
        float[] matrix3 = new float[4];

        matrix0[0] = m00();
        matrix0[1] = m01();
        matrix0[2] = m02();
        matrix0[3] = m03();
        matrix1[0] = m10();
        matrix1[1] = m11();
        matrix1[2] = m12();
        matrix1[3] = m13();
        matrix2[0] = m20();
        matrix2[1] = m21();
        matrix2[2] = m22();
        matrix2[3] = m23();
        matrix3[0] = m30();
        matrix3[1] = m31();
        matrix3[2] = m32();
        matrix3[3] = m33();

        System.out.println(Arrays.toString(matrix0));
        System.out.println(Arrays.toString(matrix1));
        System.out.println(Arrays.toString(matrix2));
        System.out.println(Arrays.toString(matrix3));
    }



    public Mat4f dot(Mat4f f, Mat4f m) {
        Mat4f result = new Mat4f();
        result.m00((f.m00()*m.m00())+(f.m01()*m.m10())+(f.m02()*m.m20())+(f.m03()*m.m30()));
        result.m01((f.m00()*m.m01())+(f.m01()*m.m11())+(f.m02()*m.m21())+(f.m03()*m.m31()));
        result.m02((f.m00()*m.m02())+(f.m01()*m.m12())+(f.m02()*m.m22())+(f.m03()*m.m32()));
        result.m03((f.m00()*m.m03())+(f.m01()*m.m13())+(f.m02()*m.m23())+(f.m03()*m.m33()));
        result.m10((f.m10()*m.m00())+(f.m11()*m.m10())+(f.m12()*m.m20())+(f.m13()*m.m30()));
        result.m11((f.m10()*m.m01())+(f.m11()*m.m11())+(f.m12()*m.m21())+(f.m13()*m.m31()));
        result.m12((f.m10()*m.m02())+(f.m11()*m.m12())+(f.m12()*m.m22())+(f.m13()*m.m32()));
        result.m13((f.m10()*m.m03())+(f.m11()*m.m13())+(f.m12()*m.m23())+(f.m13()*m.m33()));
        result.m20((f.m20()*m.m00())+(f.m21()*m.m10())+(f.m22()*m.m20())+(f.m23()*m.m30()));
        result.m21((f.m20()*m.m01())+(f.m21()*m.m11())+(f.m22()*m.m21())+(f.m23()*m.m31()));
        result.m22((f.m20()*m.m02())+(f.m21()*m.m12())+(f.m22()*m.m22())+(f.m23()*m.m32()));
        result.m23((f.m20()*m.m03())+(f.m21()*m.m13())+(f.m22()*m.m23())+(f.m23()*m.m33()));
        result.m30((f.m30()*m.m00())+(f.m31()*m.m10())+(f.m32()*m.m20())+(f.m33()*m.m30()));
        result.m31((f.m30()*m.m01())+(f.m31()*m.m11())+(f.m32()*m.m21())+(f.m33()*m.m31()));
        result.m32((f.m30()*m.m02())+(f.m31()*m.m12())+(f.m32()*m.m22())+(f.m33()*m.m32()));
        result.m33((f.m30()*m.m03())+(f.m31()*m.m13())+(f.m32()*m.m23())+(f.m33()*m.m33()));


        return result;
    }
    public Mat4f invertAffine() {
        Mat4f result = new Mat4f();
        float m11m00 = this.m00()*this.m11(), m10m01 = this.m01() * this.m10(), m10m02 = this.m02() * this.m10();
        float m12m00 = this.m00() * this.m12(), m12m01 = this.m01() * this.m12(), m11m02 = this.m02() * this.m11();
        float det = (m11m00 - m10m01) * this.m22() + (m10m02 - m12m00) * this.m21() + (m12m01 - m11m02) * this.m20();
        float invdet = 1 / det;

        float m10m22 = this.m10() * this.m22(), m10m21 = this.m10() * this.m21(), m11m22 = this.m11() * this.m22();
        float m11m20 = this.m11() * this.m20(), m12m21 = this.m12() * this.m21(), m12m20 = this.m12() * this.m20();
        float m20m02 = this.m20() * this.m02(), m20m01 = this.m20() * this.m01(), m21m02 = this.m21() * this.m02();
        float m21m00 = this.m21() * this.m00(), m22m01 = this.m22() * this.m01(), m22m00 = this.m22() * this.m00();
        float nm30 = (m10m22 * this.m31() - m10m21 * this.m32() + m11m20 * this.m32() - m11m22 * this.m30() + m12m21 * this.m30() - m12m20 * this.m31()) * invdet;
        float nm31 = (m20m02 * this.m31() - m20m01 * this.m32() + m21m00 * this.m32() - m21m02 * this.m30() + m22m01 * this.m30() - m22m00 * this.m31()) * invdet;
        float nm32 = (m11m02 * this.m30() - m12m01 * this.m30() + m12m00 * this.m31() - m10m02 * this.m31() + m10m01 * this.m32() - m11m00 * this.m32()) * invdet;
        result.m00((m11m22 - m12m21) * invdet);
        result.m01((m21m02 - m22m01) * invdet);
        result.m02((m12m01 - m11m02) * invdet);
        result.m03(0);
        result.m10((m12m20 - m10m22) * invdet);
        result.m11((m22m00 - m20m02) * invdet);
        result.m12((m10m02 - m12m00) * invdet);
        result.m13(0);
        result.m20((m10m21 - m11m20) * invdet);
        result.m21((m20m01 - m21m00) * invdet);
        result.m22((m11m00 - m10m01) * invdet);
        result.m23(0);
        result.m30(nm30);
        result.m31(nm31);
        result.m32(nm32);
        result.m33(1);

        return result;
    }

    public Vec4f matrixVecMult(Mat4f m, Vec4f v) {

        Vec4f result = new Vec4f(
                m.m00()*v.x() + m.m01()*v.y() + m.m02()*v.z() + m.m03()*v.w(),
                m.m10()*v.x() + m.m11()*v.y() + m.m12()*v.z() + m.m13()*v.w(),
                m.m20()*v.x() + m.m21()*v.y() + m.m22()*v.z() + m.m23()*v.w(),
                m.m30()*v.x() + m.m31()*v.y() + m.m32()*v.z() + m.m33()*v.w()

                );

        return result;

    }






    public FloatBuffer get(FloatBuffer buffer){
        put(this, buffer);

        return buffer;
    }

    private void put(Mat4f matrix, FloatBuffer dest){
        dest.put(0, matrix.m00())
                .put(1, matrix.m10())
                .put(2, matrix.m20())
                .put(3, matrix.m30())
                .put(4, matrix.m01())
                .put(5, matrix.m11())
                .put(6, matrix.m21())
                .put(7, matrix.m31())
                .put(8, matrix.m02())
                .put(9, matrix.m12())
                .put(10, matrix.m22())
                .put(11, matrix.m32())
                .put(12, matrix.m03())
                .put(13, matrix.m13())
                .put(14, matrix.m32())
                .put(15, matrix.m33());
    }
}
