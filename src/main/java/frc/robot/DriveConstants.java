package frc.robot;

import com.pathplanner.lib.util.ReplanningConfig;
import edu.wpi.first.math.kinematics.DifferentialDriveKinematics;
import edu.wpi.first.math.util.Units;

/**
 * The DriveConstants class has all of the constants needed for autonomous / gyro assisted movement
 * of the robot. This class is kept seperate as its values change quite a lot. Odemetry Is used to
 * track the position and state of the robot. Kinemtatics is used to calculate how much power to
 * apply to each motor.
 */
public final class DriveConstants {
  // general drive constants
  // https://www.chiefdelphi.com/t/encoders-velocity-to-m-s/390332/2
  // https://sciencing.com/convert-rpm-linear-speed-8232280.html
  public static final double WHEEL_DIAMETER = Units.inchesToMeters(6); // meters
  public static final double kTrackwidthMeters = 0.60048;
  // this is not used and is handled by the rev encoder.
  public static final double PULSES_PER_REV = 1;
  public static final double GEAR_RATIO = 8.46; // 8.46:1
  // basically converted from rotations to to radians to then meters using the wheel diameter.
  // the diameter is already *2 so we don't need to multiply by 2 again.
  public static final double POSITION_CONVERSION_RATIO =
      (Math.PI * WHEEL_DIAMETER) / PULSES_PER_REV / GEAR_RATIO;
  public static final double VELOCITY_CONVERSION_RATIO = POSITION_CONVERSION_RATIO / 60;
  // Kinematic constants

  // These characterization values MUST be determined either experimentally or theoretically
  // for *your* robot's drive.
  // The Robot Characterization Toolsuite provides a convenient tool for obtaining these
  // values for your robot.
  // Feed Forward Constants
  public static final double kDriveFeedForward = 0.0;
  // Max speed Constants
  public static final double kMaxOutputDrive = 1.0;
  public static final double kMinOutputDrive = -1.0;
  // Feed Back / PID Constants
  public static final double kPDriveVel = 0.00034388;
  public static final double kIDriveVel = 0.0;
  public static final double kDDriveVel = 0.0;
  public static final double kIzDriveVel = 0.0; // error before integral takes effect

  public static final double kPDrivePos = 5.7745;
  public static final double kIDrivePos = 0.0;
  public static final double kDDrivePos = 0.55289;
  public static final double kIzDrivePos = 0.0; // error before integral takes effect
  // Helper class that converts a chassis velocity (dx and dtheta components) to left and right
  // wheel velocities for a differential drive.
  public static final DifferentialDriveKinematics kDriveKinematics =
      new DifferentialDriveKinematics(kTrackwidthMeters);
  // Default path replanning config. See the API for the options
  public static final ReplanningConfig autoReplanningConfig = new ReplanningConfig();

  // Motor Controller PID Slots
  public static final int kDrivetrainVelocityPIDSlot = 0;
  public static final int kDrivetrainPositionPIDSlot = 1;
}
