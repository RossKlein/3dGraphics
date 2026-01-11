package Ross.Modules.math;

public class Rotations {


    public static Quaternion weirdOne(Quaternion rotationctrl, float xrotate, float yrotate) {
        Quaternion y = rotationctrl.rotation(1, 0, 0, -0.2f * yrotate);
        Quaternion x = rotationctrl.rotation(0, 1, 0, -0.2f * xrotate);

        rotationctrl = rotationctrl.mult(rotationctrl, y);

        rotationctrl = rotationctrl.mult(rotationctrl, x);

        Vec3f cameraup = rotationctrl.rotate(rotationctrl, new Vec3f(0,1,0));
        cameraup.print();
        Vec3f correctedup = new Vec3f(0, cameraup.y, 0);
        Vec3f axis = cameraup.cross(cameraup, correctedup);
        float angle = (float)Math.acos(cameraup.dot(cameraup, correctedup));

        rotationctrl = rotationctrl.mult(rotationctrl, rotationctrl.rotation(axis, angle).conjugate()).normalize();

        return rotationctrl;
    }


    public static double extractRoll(Quaternion q) {
        // Calculate roll (ψ) using the formulas mentioned
        double sinr_cosp = 2 * (q.w * q.z + q.x * q.y);
        double cosr_cosp = 1 - 2 * (q.y * q.y + q.z * q.z);
        double roll = Math.atan2(sinr_cosp, cosr_cosp);

        return roll; // Return roll in radians
    }

}

