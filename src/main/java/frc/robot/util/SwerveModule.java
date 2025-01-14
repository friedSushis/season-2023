package frc.robot.util;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.DemandType;
import com.ctre.phoenix.motorcontrol.can.TalonFX;
import com.ctre.phoenix.sensors.CANCoder;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import frc.robot.Robot;
import frc.robot.RobotMap.DriveMap;

public class SwerveModule {
  public int moduleNumber;
  private Rotation2d angleOffset;
  private Rotation2d lastAngle;

  private TalonFX rotator;
  private TalonFX drive;
  private CANCoder angleEncoder;

  SimpleMotorFeedforward feedforward =
      new SimpleMotorFeedforward(DriveMap.DRIVE_KS, DriveMap.DRIVE_KV, DriveMap.DRIVE_KA);

  public SwerveModule(int moduleNumber, SwerveModuleConstants moduleConstants) {
    this.moduleNumber = moduleNumber;
    this.angleOffset = moduleConstants.angleOffset;

    /* Angle Encoder Config */
    angleEncoder = new CANCoder(moduleConstants.encoderId);
    configAngleEncoder();

    /* Angle Motor Config */
    rotator = new TalonFX(moduleConstants.rotatorId);
    configAngleMotor();

    /* Drive Motor Config */
    drive = new TalonFX(moduleConstants.driveId);
    configDriveMotor();

    lastAngle = getState().angle;
  }

  public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
    /*
     * This is a custom optimize function, since default WPILib optimize assumes
     * continuous controller which CTRE and Rev onboard is not
     */
    desiredState = CTREModuleState.optimize(desiredState, getState().angle);
    setAngle(desiredState);
    setSpeed(desiredState, isOpenLoop);
  }

  private void setSpeed(SwerveModuleState desiredState, boolean isOpenLoop) {
    if (isOpenLoop) {
      double percentOutput = desiredState.speedMetersPerSecond / DriveMap.MAX_VELOCITY;
      drive.set(ControlMode.PercentOutput, percentOutput);
    } else {
      double velocity =
          Conversions.MPSToFalcon(
              desiredState.speedMetersPerSecond,
              DriveMap.WHEEL_CIRCUMFERENCE,
              DriveMap.DRIVE_GEAR_RATIO);
      drive.set(
          ControlMode.Velocity,
          velocity,
          DemandType.ArbitraryFeedForward,
          feedforward.calculate(desiredState.speedMetersPerSecond));
    }
  }

  private void setAngle(SwerveModuleState desiredState) {
    Rotation2d angle =
        (Math.abs(desiredState.speedMetersPerSecond) <= (DriveMap.MAX_VELOCITY * 0.01))
            ? lastAngle
            : desiredState
                .angle; // Prevent rotating module if speed is less then 1%. Prevents Jittering.

    rotator.set(
        ControlMode.Position,
        Conversions.degreesToFalcon(angle.getDegrees(), DriveMap.ANGLE_GEAR_RATIO));
    lastAngle = angle;
  }

  private Rotation2d getAngle() {
    return Rotation2d.fromDegrees(
        Conversions.falconToDegrees(
            rotator.getSelectedSensorPosition(), DriveMap.ANGLE_GEAR_RATIO));
  }

  public Rotation2d getCanCoder() {
    return Rotation2d.fromDegrees(angleEncoder.getAbsolutePosition());
  }

  private void resetToAbsolute() {
    double absolutePosition =
        Conversions.degreesToFalcon(
            getCanCoder().getDegrees() - angleOffset.getDegrees(), DriveMap.ANGLE_GEAR_RATIO);
    rotator.setSelectedSensorPosition(absolutePosition);
  }

  private void configAngleEncoder() {
    angleEncoder.configFactoryDefault();
    angleEncoder.configAllSettings(Robot.ctreConfigs.swerveCanCoderConfig);
  }

  private void configAngleMotor() {
    rotator.configFactoryDefault();
    rotator.configAllSettings(Robot.ctreConfigs.swerveAngleFXConfig);
    rotator.setInverted(DriveMap.ANGLE_MOTOR_INVERT);
    rotator.setNeutralMode(DriveMap.ROTATOR_NEUTRAL_MODE);
    resetToAbsolute();
  }

  private void configDriveMotor() {
    drive.configFactoryDefault();
    drive.configAllSettings(Robot.ctreConfigs.swerveDriveFXConfig);
    drive.setInverted(DriveMap.DRIVE_MOTOR_INVERT);
    drive.setNeutralMode(DriveMap.DRIVE_NEUTRAL_MODE);
    drive.setSelectedSensorPosition(0);
  }

  public SwerveModuleState getState() {
    return new SwerveModuleState(
        Conversions.falconToMPS(
            drive.getSelectedSensorVelocity(),
            DriveMap.WHEEL_CIRCUMFERENCE,
            DriveMap.DRIVE_GEAR_RATIO),
        getAngle());
  }

  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(
        Conversions.falconToMeters(
            drive.getSelectedSensorPosition(),
            DriveMap.WHEEL_CIRCUMFERENCE,
            DriveMap.DRIVE_GEAR_RATIO),
        getAngle());
  }
}
