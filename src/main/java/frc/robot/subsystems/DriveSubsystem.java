// Copyright (c) Jack Nelson & Miami Beach Bots

package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.NeutralMode;
// import motor & frc dependencies
import com.ctre.phoenix.motorcontrol.can.WPI_VictorSPX;
import com.kauailabs.navx.frc.AHRS;
import com.pathplanner.lib.auto.AutoBuilder;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.estimator.DifferentialDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.DifferentialDriveWheelSpeeds;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.Constants.CANConstants;
import frc.robot.DriveConstants;

/** This Subsystem is what allows the code to interact with the drivetrain of the robot. */
public class DriveSubsystem extends SubsystemBase {
  // Gyro
  private final AHRS m_Gyro;

  // motors
  private final WPI_VictorSPX m_backLeft; // Main / Master Motor for Left
  private final WPI_VictorSPX m_frontLeft; // Slave Motor for Left (Follow Master)
  private final WPI_VictorSPX m_backRight; // Main / Master Motor for Right
  private final WPI_VictorSPX m_frontRight; // Slave Motor for Right (Follow Master)
  // Main drive function
  private final DifferentialDrive m_ddrive;

  // Encoders
  private final Encoder m_encoderLeft;
  private final Encoder m_encoderRight;

  // Odometry class for tracking robot pose (position on field)
  private final DifferentialDrivePoseEstimator m_driveOdometry;

  // Angle PID / RotateToAngle
  static final double turn_P = 0.1;
  static final double turn_I = 0.00;
  static final double turn_D = 0.00;
  static final double MaxTurnRateDegPerS = 100;
  static final double MaxTurnAccelerationDegPerSSquared = 300;
  static final double TurnToleranceDeg = 3; // max diff in degrees
  static final double TurnRateToleranceDegPerS = 10; // degrees per second
  // false when inactive, true when active / a target is set.
  private boolean turnControllerEnabled = false;
  private double turnRotateToAngleRate; // This value will be updated by the PID Controller
  // pid controller for "RotateToAngle"
  private final ProfiledPIDController m_turnController =
      new ProfiledPIDController(
          turn_P,
          turn_I,
          turn_D,
          new TrapezoidProfile.Constraints(MaxTurnRateDegPerS, MaxTurnAccelerationDegPerSSquared));

  // Distance PID / MoveDistance
  static final double distance_P = 0.1;
  static final double distance_I = 0.00;
  static final double distance_D = 0.00;
  static final double distanceMaxSpeed = 1; // m/s
  static final double distanceMaxAcceleration = 2; // m/s^2
  static final double DistanceTolerance = 0.01; // max diff in meters
  static final double DistanceSpeedTolerance = 0.1; // ignore if velocity is below. (m)
  // false when inactive, true when active / a target is set.
  private boolean distanceControllerEnabled = false;
  private double distanceThrottleRate; // This value will be updated by the PID Controller
  // pid controller for "MoveDistance"
  private final ProfiledPIDController m_distanceController =
      new ProfiledPIDController(
          distance_P,
          distance_I,
          distance_D,
          new TrapezoidProfile.Constraints(distanceMaxSpeed, distanceMaxAcceleration));

  // Balance PID / AutoBalance
  static final double balance_P = 0.0625; // 1/16
  static final double balance_I = 0.00;
  static final double balance_D = 0.00;
  static final double MaxBalanceRateDegPerS = 10;
  static final double MaxBalanceAccelerationDegPerSSquared = 20;
  static final double BalanceToleranceDeg = 2; // max diff in degrees
  // false when inactive, true when active / a target is set.
  private boolean balanceControllerEnabled = false;
  private double balanceThrottleRate; // This value will be updated by the PID Controller
  // pid controller for balanceCorrection
  private final ProfiledPIDController m_balanceController =
      new ProfiledPIDController(
          balance_P,
          balance_I,
          balance_D,
          new TrapezoidProfile.Constraints(
              MaxBalanceRateDegPerS, MaxBalanceAccelerationDegPerSSquared));

  // track robot field location for dashboard
  private Field2d field = new Field2d();

  /** Creates a new DriveSubsystem. */
  public DriveSubsystem() {
    // Init gyro
    m_Gyro = new AHRS(SPI.Port.kMXP);
    // init motors
    // rio means built into the roboRIO
    m_backLeft = new WPI_VictorSPX(CANConstants.MOTORBACKLEFTID);
    m_frontLeft = new WPI_VictorSPX(CANConstants.MOTORFRONTLEFTID);
    m_frontRight = new WPI_VictorSPX(CANConstants.MOTORFRONTRIGHTID);
    m_backRight = new WPI_VictorSPX(CANConstants.MOTORBACKRIGHTID);

    // setup main and secondary motors
    m_frontLeft.follow(m_backLeft); // set front left to follow back left
    m_frontRight.follow(m_backRight); // set front right to follow back right

    m_backRight.setInverted(true); // invert right side

    // init drive function
    m_ddrive = new DifferentialDrive(m_backLeft, m_backRight);

    // init Encoders
    m_encoderLeft = new Encoder(Constants.DRIVEENCODERLEFTA, Constants.DRIVEENCODERLEFTB);
    m_encoderRight = new Encoder(Constants.DRIVEENCODERRIGHTA, Constants.DRIVEENCODERRIGHTB);
    m_encoderRight.setReverseDirection(true); // invert left to match drive

    // configure encoders
    m_encoderLeft.setDistancePerPulse(DriveConstants.DISTANCE_PER_PULSE); // distance in meters
    m_encoderRight.setDistancePerPulse(DriveConstants.DISTANCE_PER_PULSE); // distance in meters
    m_encoderLeft.setSamplesToAverage(5);
    m_encoderRight.setSamplesToAverage(5);
    m_encoderLeft.setMinRate(0.1); // min rate to be determined moving
    m_encoderRight.setMinRate(0.1); // min rate to be determined moving
    resetEncoders();
    // Every time this function is called, A dollar is taken out of Jack's savings. Aka do it more.
    resetGyro();

    // configure Odemetry
    m_driveOdometry =
        new DifferentialDrivePoseEstimator(
            DriveConstants.kDriveKinematics,
            getRotation2d(),
            m_encoderLeft.getDistance(),
            m_encoderRight.getDistance(),
            new Pose2d());

    // config turn pid controller.
    m_turnController.enableContinuousInput(-180.0f, 180.0f);
    m_turnController.setTolerance(TurnToleranceDeg, TurnRateToleranceDegPerS);
    // config distance pid controller
    m_distanceController.setTolerance(DistanceTolerance, DistanceSpeedTolerance);
    // this is the target pitch/ tilt error.
    m_balanceController.setGoal(0);
    m_balanceController.setTolerance(BalanceToleranceDeg); // max error in degrees

    // Setup Base AutoBuilder (Autonomous)
    AutoBuilder.configureRamsete(
        this::getPose, // Pose2d supplier
        this::resetPose, // Pose2d consumer, used to reset odometry at the beginning of auto
        this::getSpeeds, // A method for getting the chassis' current speed and direction
        this::setSpeeds, // A consumer that takes the desired chassis speed and direction
        DriveConstants.autoReplanningConfig,
        () -> {
          // Boolean supplier that controls when the path will be mirrored for the red alliance
          // This will flip the path being followed to the red side of the field.
          // THE ORIGIN WILL REMAIN ON THE BLUE SIDE

          var alliance = DriverStation.getAlliance();
          if (alliance.isPresent()) {
            return alliance.get() == DriverStation.Alliance.Red;
          }
          return false;
        },
        this // Reference to this subsystem to set requirements
        );

    SmartDashboard.putData("Field", field); // add field to dashboard
  }

  // default tank drive function
  // **tank drive = specific control style where two parallel forces of motion are controlled to
  // create linear and rotational motion
  public void tankDrive(double leftSpeed, double rightSpeed) {
    m_ddrive.tankDrive(leftSpeed, rightSpeed);
  }

  public void balanceResetPID() {
    /** This should be run when stopping a pid command. */
    balanceControllerEnabled = false;
  }

  public void balanceCorrection(double gyroPitchAngle) {
    if (!balanceControllerEnabled) {
      m_balanceController.reset(gyroPitchAngle);
      balanceControllerEnabled = true;
    }
    balanceThrottleRate = MathUtil.clamp(m_balanceController.calculate(gyroPitchAngle), -1.0, 1.0);
    if (!m_balanceController.atGoal()) {
      // we reverse the values beacuse the robot was balancing in the wrong direction.
      this.tankDrive(-balanceThrottleRate, -balanceThrottleRate);
      System.out.println(balanceThrottleRate);
    }
  }

  public void distanceResetPID() {
    /** This should be run when stopping a pid command. */
    distanceControllerEnabled = false;
  }

  public void distanceSetGoal(double targetDistance) {
    m_distanceController.setGoal(AverageDistance() + targetDistance);
  }

  private void calculateDistanceRate(double targetDistance) {
    if (!distanceControllerEnabled) {
      m_distanceController.reset(AverageDistance());
      m_distanceController.setGoal(AverageDistance() + targetDistance);
      distanceControllerEnabled = true;
    }
    distanceThrottleRate =
        MathUtil.clamp(m_distanceController.calculate(AverageDistance()), -1.0, 1.0);
  }

  public void driveToDistance(double targetDistance) {
    this.calculateDistanceRate(targetDistance);
    double leftStickValue = turnRotateToAngleRate;
    double rightStickValue = turnRotateToAngleRate;
    if (!m_distanceController.atGoal()) {
      this.tankDrive(leftStickValue, rightStickValue);
    }
  }

  public void driveAndTurn(double gyroYawAngle, double TargetAngleDegrees, double targetDistance) {
    /*
    This lets you set a gyro angle and a distance you need to travel.
    this should not be used in auto mode.
     */
    this.calcuateAngleRate(gyroYawAngle, TargetAngleDegrees);
    this.calculateDistanceRate(targetDistance);
    double leftStickValue = distanceThrottleRate + turnRotateToAngleRate;
    double rightStickValue = distanceThrottleRate - turnRotateToAngleRate;
    if (!m_distanceController.atGoal() || !m_turnController.atGoal()) {
      this.tankDrive(leftStickValue, rightStickValue);
    }
  }

  // these next 4 functions are for turning a set radius while using the gyro.
  public void turnResetPID() {
    /** This should be run when stopping a pid command. */
    turnControllerEnabled = false;
  }

  public void turnSetGoal(double targetAngleDegrees) {
    m_turnController.setGoal(targetAngleDegrees);
  }

  private void calcuateAngleRate(double gyroYawAngle, double targetAngleDegrees) {
    if (!turnControllerEnabled) {
      m_turnController.reset(gyroYawAngle);
      m_turnController.setGoal(targetAngleDegrees);
      turnControllerEnabled = true;
    }
    turnRotateToAngleRate = MathUtil.clamp(m_turnController.calculate(gyroYawAngle), -1.0, 1.0);
  }

  public void turnToAngle(double gyroYawAngle, double TargetAngleDegrees) {
    /*
     * When this function is activated, execute another command to rotate to target angle. Since a Tank drive
     * system cannot move forward simultaneously while rotating, all joystick input
     * is ignored until this button is released.
     */
    this.calcuateAngleRate(gyroYawAngle, TargetAngleDegrees);
    double leftStickValue = turnRotateToAngleRate;
    double rightStickValue = -turnRotateToAngleRate;
    if (!m_turnController.atGoal()) {
      this.tankDrive(leftStickValue, rightStickValue);
    }
  }

  // magnitude = (joystickL + joystickR) / 2;
  public void driveStraight(
      double gyroYawAngle, double gyroAccumYawAngle, double joystickMagnitude) {
    /*
     * WWhen this function is activated, the robot is in "drive straight" mode.
     * Whatever direction the robot was heading when "drive straight" mode was
     * entered will be maintained. The average speed of both joysticks is the
     * magnitude of motion.
     */
    this.calcuateAngleRate(gyroYawAngle, gyroAccumYawAngle);
    double angleRate;
    if (!m_turnController.atGoal()) {
      angleRate = turnRotateToAngleRate;
    } else {
      angleRate = 0;
    }
    double leftStickValue = joystickMagnitude + angleRate;
    double rightStickValue = joystickMagnitude - angleRate;
    this.tankDrive(leftStickValue, rightStickValue);
  }

  /*
   * This function can return our robots DiffernentialDriveWheelSpeeds, which is the speed of each side of the robot.
   */
  public DifferentialDriveWheelSpeeds getWheelSpeeds() {
    return new DifferentialDriveWheelSpeeds(m_encoderLeft.getRate(), m_encoderRight.getRate());
  }

  /*
   * This function can return our robots ChassisSpeeds, which is vx (m/s), vy (m/s), and omega (rad/s).
   */
  public ChassisSpeeds getSpeeds() {
    return DriveConstants.kDriveKinematics.toChassisSpeeds(getWheelSpeeds());
  }

  /*
   * This function can set our robots ChassisSpeeds, which is vx (m/s), vy (m/s), and omega (rad/s).
   * vy is always 0 as we are not strafing.
   */
  public void setSpeeds(ChassisSpeeds speeds) {
    setWheelVelocities(DriveConstants.kDriveKinematics.toWheelSpeeds(speeds));
  }

  /*
   * This function can set our robots DifferentialDriveWheelSpeeds, which is the speed of each side of the robot.
   */
  public void setWheelVelocities(DifferentialDriveWheelSpeeds speeds) {
    // TODO: Implement, this makes everything more accurate and allows auto to work.
    // https://docs.wpilib.org/en/stable/docs/software/advanced-controls/trajectories/ramsete.html#ramsete-in-the-command-based-framework
    // we should use in controller velocity PID
  }

  public Pose2d getPose() {
    return m_driveOdometry.getEstimatedPosition();
  }

  public void updateVisionPose(Pose2d visionRobotPose, double timestamp) {
    m_driveOdometry.addVisionMeasurement(visionRobotPose, timestamp);
  }

  public void resetEncoders() {
    m_encoderLeft.reset();
    m_encoderRight.reset();
  }

  public double AverageDistance() {
    return (m_encoderLeft.getDistance() + m_encoderRight.getDistance()) / 2;
  }

  public void SetBrakemode() {
    m_backLeft.setNeutralMode(NeutralMode.Brake);
    m_backRight.setNeutralMode(NeutralMode.Brake);
    m_frontLeft.setNeutralMode(NeutralMode.Brake);
    m_frontRight.setNeutralMode(NeutralMode.Brake);
  }

  public void SetCoastmode() {
    m_backLeft.setNeutralMode(NeutralMode.Coast);
    m_backRight.setNeutralMode(NeutralMode.Coast);
    m_frontLeft.setNeutralMode(NeutralMode.Coast);
    m_frontRight.setNeutralMode(NeutralMode.Coast);
  }

  /**
   * Resets the odometry to the specified pose.
   *
   * @param pose The pose to which to set the odometry.
   */
  public void resetPose(Pose2d pose) {
    resetEncoders();
    m_driveOdometry.resetPosition(
        getRotation2d(), m_encoderLeft.getDistance(), m_encoderRight.getDistance(), pose);
  }

  public void stop() {
    this.tankDrive(0, 0);
  }

  public Rotation2d getRotation2d() {
    return m_Gyro.getRotation2d();
  }

  // for balance correction
  public double getPitch() {
    return m_Gyro.getPitch(); // get pitch in degrees
  }

  // for PID control (turn by degrees)
  public double getAccumYaw() {
    return m_Gyro.getAngle(); // get angle in degrees
  }

  public double getYaw() {
    return m_Gyro.getYaw();
  }

  public void resetGyro() {
    m_Gyro.reset();
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    SmartDashboard.putNumber("Left Encoder Speed (M/s)", this.m_encoderLeft.getRate());
    SmartDashboard.putNumber("Right Encoder Speed (M/s)", this.m_encoderRight.getRate());
    SmartDashboard.putNumber("Distance L", this.m_encoderLeft.getDistance());
    SmartDashboard.putNumber("Distance R", this.m_encoderRight.getDistance());
    SmartDashboard.putNumber("Current Robot Location X axis", getPose().getX());
    SmartDashboard.putNumber("Current Robot Location Y axis", getPose().getY());
    SmartDashboard.putNumber("Current Robot Rotation", getPose().getRotation().getDegrees());
    SmartDashboard.putNumber("Average Distance Traveled", AverageDistance());
    SmartDashboard.putNumber("Current Gyro Pitch", getPitch());
    SmartDashboard.putNumber("Current Gyro Yaw", getYaw());
    SmartDashboard.putBoolean("Gyro Calibrating", m_Gyro.isCalibrating());
    // Update the odometry in the periodic block
    m_driveOdometry.update(
        getRotation2d(), m_encoderLeft.getDistance(), m_encoderRight.getDistance());
    field.setRobotPose(getPose());
  }

  @Override
  public void simulationPeriodic() {
    // This method will be called once per scheduler run during simulation
  }
}
