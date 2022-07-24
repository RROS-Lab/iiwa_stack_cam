/**
 * Copyright (C) 2016 Salvatore Virga - salvo.virga@tum.de, Marco Esposito - marco.esposito@tum.de
 * Technische Universit�t M�nchen
 * Chair for Computer Aided Medical Procedures and Augmented Reality
 * Fakult�t f�r Informatik / I16, Boltzmannstra�e 3, 85748 Garching bei M�nchen, Germany
 * http://campar.in.tum.de
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 * following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package de.tum.in.camp.kuka.ros;

import java.util.ArrayList;
import java.util.List;

import iiwa_msgs.RedundancyInformation;
import iiwa_msgs.SplineSegment;
import geometry_msgs.PoseStamped;

import com.kuka.connectivity.motionModel.smartServo.SmartServo;
import com.kuka.connectivity.motionModel.smartServoLIN.SmartServoLIN;
import com.kuka.roboticsAPI.deviceModel.JointPosition;
import com.kuka.roboticsAPI.deviceModel.LBR;
import com.kuka.roboticsAPI.deviceModel.LBRE1Redundancy;
import com.kuka.roboticsAPI.geometricModel.Frame;
import com.kuka.roboticsAPI.geometricModel.ObjectFrame;
import com.kuka.roboticsAPI.geometricModel.redundancy.IRedundancyCollection;
import com.kuka.roboticsAPI.motionModel.CartesianPTP;
import com.kuka.roboticsAPI.motionModel.LIN;
import com.kuka.roboticsAPI.motionModel.PTP;
import com.kuka.roboticsAPI.motionModel.Spline;
import com.kuka.roboticsAPI.motionModel.SplineMotionCP;
import com.kuka.roboticsAPI.motionModel.SplineJP;
import com.kuka.roboticsAPI.motionModel.SplineMotionJP;
import com.kuka.roboticsAPI.motionModel.controlModeModel.IMotionControlMode;
import com.kuka.roboticsAPI.motionModel.controlModeModel.CartesianImpedanceControlMode;
import com.kuka.roboticsAPI.motionModel.controlModeModel.JointImpedanceControlMode;
import com.kuka.roboticsAPI.motionModel.controlModeModel.PositionControlMode;
import com.kuka.roboticsAPI.geometricModel.CartDOF;

import static com.kuka.roboticsAPI.motionModel.BasicMotions.ptp;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.lin;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.circ;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.spl;

public class Motions {
  private LBR robot;
  private String robotBaseFrameId;

  protected JointPosition maxJointLimits;
  protected JointPosition minJointLimits;

  protected ObjectFrame endPointFrame;
  protected iiwaActionServer actionServer;
  protected iiwaPublisher publisher;

  private JointPosition jp;
  private JointPosition jv;
  private JointPosition jointDisplacement;

  private long currentTime = System.nanoTime();
  private long previousTime = System.nanoTime();
  private double loopPeriod = 0.0; // Loop period in s.
  private final double softJointLimit = 0.0174533; // in radians.

  public Motions(LBR robot, String robotBaseFrameId, SmartServo motion, ObjectFrame endPointFrame, iiwaPublisher publisher, iiwaActionServer actionServer) {
    this.robot = robot;
    this.robotBaseFrameId = robotBaseFrameId;
    this.endPointFrame = endPointFrame;
    this.actionServer = actionServer;
    this.publisher = publisher;

    jp = new JointPosition(robot.getJointCount());
    jv = new JointPosition(robot.getJointCount());
    jointDisplacement = new JointPosition(robot.getJointCount());
    maxJointLimits = robot.getJointLimits().getMaxJointPosition();
    minJointLimits = robot.getJointLimits().getMinJointPosition();
  }

  public void setEnpointFrame(ObjectFrame endpointFrame) {
    this.endPointFrame = endpointFrame;
  }

  /**
   * Start SmartServo motion to cartesian target pose.
   * 
   * @param motion
   * @param commandPosition
   * @param status : Redundancy information. Set to -1 if not needed
   */
  public void cartesianPositionMotion(SmartServo motion, geometry_msgs.PoseStamped commandPosition, RedundancyInformation redundancy) {
    if (commandPosition != null) {
      Frame destinationFrame = Conversions.rosPoseToKukaFrame(robot.getRootFrame(), commandPosition.getPose());
      if (redundancy != null && redundancy.getStatus() >= 0 && redundancy.getTurn() >= 0) {
        // You can get this info from the robot Cartesian Position (SmartPad).
        IRedundancyCollection redundantData = new LBRE1Redundancy(redundancy.getE1(), redundancy.getStatus(), redundancy.getTurn());
        destinationFrame.setRedundancyInformation(robot, redundantData);
      }
      if (robot.isReadyToMove()) {
        motion.getRuntime().setDestination(destinationFrame);
      }
    }
  }

  public void cartesianPositionLinMotion(SmartServoLIN linearMotion, PoseStamped commandPosition, RedundancyInformation redundancy) {
    if (commandPosition != null) {
      Frame destinationFrame = Conversions.rosPoseToKukaFrame(robot.getRootFrame(), commandPosition.getPose());
      if (redundancy != null && redundancy.getStatus() >= 0 && redundancy.getTurn() >= 0) {
        // You can get this info from the robot Cartesian Position (SmartPad).
        IRedundancyCollection redundantData = new LBRE1Redundancy(redundancy.getE1(), redundancy.getStatus(), redundancy.getTurn());
        destinationFrame.setRedundancyInformation(robot, redundantData);
      }
      if (robot.isReadyToMove()) {
        linearMotion.getRuntime().setDestination(destinationFrame);
      }
    }
  }

  public void pointToPointCartesianMotion(IMotionControlMode motion, PoseStamped commandPosition, RedundancyInformation redundancy) {
    if (commandPosition != null) {
      Frame destinationFrame = Conversions.rosPoseToKukaFrame(robot.getRootFrame(), commandPosition.getPose());
      if (redundancy != null && redundancy.getStatus() >= 0 && redundancy.getTurn() >= 0) {
        // You can get this info from the robot Cartesian Position (SmartPad).
        IRedundancyCollection redundantData = new LBRE1Redundancy(redundancy.getE1(), redundancy.getStatus(), redundancy.getTurn());
        destinationFrame.setRedundancyInformation(robot, redundantData);
      }
      CartesianPTP ptpMotion = ptp(destinationFrame);
      SpeedLimits.applySpeedLimits(ptpMotion);
      endPointFrame.moveAsync(ptpMotion, new PTPMotionFinishedEventListener(publisher, actionServer));
    }
  }

  public void pointToPointLinearCartesianMotion(IMotionControlMode mode, PoseStamped commandPosition, RedundancyInformation redundancy) {
    if (commandPosition != null) {
      Frame destinationFrame = Conversions.rosPoseToKukaFrame(robot.getRootFrame(), commandPosition.getPose());
      if (redundancy != null && redundancy.getStatus() >= 0 && redundancy.getTurn() >= 0) {
        // You can get this info from the robot Cartesian Position (SmartPad).
        IRedundancyCollection redundantData = new LBRE1Redundancy(redundancy.getE1(), redundancy.getStatus(), redundancy.getTurn());
        destinationFrame.setRedundancyInformation(robot, redundantData);
      }
      LIN linMotion = lin(destinationFrame);
      SpeedLimits.applySpeedLimits(linMotion);
      endPointFrame.moveAsync(linMotion, new PTPMotionFinishedEventListener(publisher, actionServer));
    }
  }

  /**
   * Executes a motion along a spline, in joint space
   * 
   * @param motion
   * @param splineMsg
   * @param subscriber
   * @return
   */
  public boolean pointToPointJointSplineMotion(IMotionControlMode motion, iiwa_msgs.Spline splineMsg, iiwaSubscriber subscriber) {
    if (splineMsg == null) { return false; }

    boolean success = true;

    PTP[] path = new PTP[splineMsg.getSegments().size()];
    int idx = 0;
    for (SplineSegment segment : splineMsg.getSegments()) {
      JointPosition jointPosition = new JointPosition(
        segment.getPoint().getPoseStamped().getPose().getPosition().getX(),
        segment.getPoint().getPoseStamped().getPose().getPosition().getY(),
        segment.getPoint().getPoseStamped().getPose().getPosition().getZ(),
        segment.getPoint().getPoseStamped().getPose().getOrientation().getW(),
        segment.getPoint().getPoseStamped().getPose().getOrientation().getX(),
        segment.getPoint().getPoseStamped().getPose().getOrientation().getY(),
        segment.getPoint().getPoseStamped().getPose().getOrientation().getZ()
      );

      path[idx++] = new PTP(jointPosition);
    }

    String speedStr = splineMsg.getSegments().get(0).getPoint().getPoseStamped().getHeader().getFrameId();

    double jpVel = speedStr.isEmpty() ? 0.1 : Double.parseDouble(speedStr);

    if (jpVel < 0.01)
      jpVel = 0.1;
    else if (jpVel > 1)
      jpVel = 1;

      
    SplineJP splineJP = new SplineJP(path);
    Logger.info("get Joint Spline with size: " + idx + ", at speed: " + jpVel);

    // 0: cartesian impedence, 1: joint impedence, other: position control 
    int executeMode = splineMsg.getSegments().get(0).getPointAux().getRedundancy().getStatus();


    if (executeMode == 0) {
      CartesianImpedanceControlMode impedanceMode = new CartesianImpedanceControlMode();

      double stiffX = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getPosition().getX();
      double stiffY = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getPosition().getY();
      double stiffZ = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getPosition().getZ();
      double dampX = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getOrientation().getX();
      double dampY = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getOrientation().getY();
      double dampZ = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getOrientation().getZ();

      if (stiffX < 0.01)
        stiffX = 2000.0;
      else if (stiffX > 5000)
        stiffX = 5000.0;

      if (stiffY < 0.01)
        stiffY = 2000.0;
      else if (stiffY > 5000)
        stiffY = 5000.0;

      if (stiffZ < 0.01)
        stiffZ = 2000.0;
      else if (stiffZ > 5000)
        stiffZ = 5000.0;

      if (dampX < 0)
        dampX = 0.7;
      else if (dampX > 1)
        dampX = 1.0;

      if (dampY < 0)
        dampY = 0.7;
      else if (dampY > 1)
        dampY = 1.0;

      if (dampZ < 0)
        dampZ = 0.7;
      else if (dampZ > 1)
        dampZ = 1.0;

      impedanceMode.parametrize(CartDOF.X).setStiffness(stiffX);
      impedanceMode.parametrize(CartDOF.Y).setStiffness(stiffY);
      impedanceMode.parametrize(CartDOF.Z).setStiffness(stiffZ);
      impedanceMode.parametrize(CartDOF.X).setDamping(dampX);
      impedanceMode.parametrize(CartDOF.Y).setDamping(dampY);
      impedanceMode.parametrize(CartDOF.Z).setDamping(dampZ);

      try {
        endPointFrame.moveAsync(splineJP.setJointVelocityRel(jpVel).setMode(impedanceMode));
      } catch (Exception e) {
        System.out.println(e);
      }

    }else if(executeMode == 1){
      JointImpedanceControlMode impedanceMode = new JointImpedanceControlMode(robot.getJointCount());

      double stiff0 = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getPosition().getX();
      double stiff1 = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getPosition().getY();
      double stiff2 = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getPosition().getZ();
      double stiff3 = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getOrientation().getW();
      double stiff4 = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getOrientation().getX();
      double stiff5 = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getOrientation().getY();
      double stiff6 = splineMsg.getSegments().get(0).getPointAux().getPoseStamped().getPose().getOrientation().getZ();

      double damping0 = splineMsg.getSegments().get(1).getPointAux().getPoseStamped().getPose().getPosition().getX();
      double damping1 = splineMsg.getSegments().get(1).getPointAux().getPoseStamped().getPose().getPosition().getY();
      double damping2 = splineMsg.getSegments().get(1).getPointAux().getPoseStamped().getPose().getPosition().getZ();
      double damping3 = splineMsg.getSegments().get(1).getPointAux().getPoseStamped().getPose().getOrientation().getW();
      double damping4 = splineMsg.getSegments().get(1).getPointAux().getPoseStamped().getPose().getOrientation().getX();
      double damping5 = splineMsg.getSegments().get(1).getPointAux().getPoseStamped().getPose().getOrientation().getY();
      double damping6 = splineMsg.getSegments().get(1).getPointAux().getPoseStamped().getPose().getOrientation().getZ();

      impedanceMode.setStiffness(stiff0, stiff1, stiff2, stiff3, stiff4, stiff5, stiff6);
      impedanceMode.setDamping(damping0, damping1, damping2, damping3, damping4, damping5, damping6);

      try {
        endPointFrame.moveAsync(splineJP.setJointVelocityRel(jpVel).setMode(impedanceMode));
      } catch (Exception e) {
        System.out.println(e);
      }

    }else{
      try {
        endPointFrame.moveAsync(splineJP.setJointVelocityRel(jpVel).setMode(new PositionControlMode()));
      } catch (Exception e) {
        System.out.println(e);
      }
    }

    

    

    return success;

  }

  /**
   * Executes a motion along a spline
   * 
   * @param motion
   * @param splineMsg
   * @param subscriber: Required for TF lookups
   */
  public boolean pointToPointCartesianSplineMotion(IMotionControlMode motion, iiwa_msgs.Spline splineMsg, iiwaSubscriber subscriber) {
    if (splineMsg == null) { return false; }

    boolean success = true;
    List<SplineMotionCP<?>> splineSegments = new ArrayList<SplineMotionCP<?>>();
    int i = 0;

    for (SplineSegment segmentMsg : splineMsg.getSegments()) {
      SplineMotionCP<?> segment = null;
      switch (segmentMsg.getType()) {
        case SplineSegment.SPL: {
          Frame p = subscriber.cartesianPoseToRosFrame(robot.getRootFrame(), segmentMsg.getPoint(), robotBaseFrameId);
          if (p != null) {
            segment = spl(p);
          }
          break;
        }
        case SplineSegment.LIN: {
          Frame p = subscriber.cartesianPoseToRosFrame(robot.getRootFrame(), segmentMsg.getPoint(), robotBaseFrameId);
          if (p != null) {
            segment = lin(p);
          }
          break;
        }
        case SplineSegment.CIRC: {
          Frame p = subscriber.cartesianPoseToRosFrame(robot.getRootFrame(), segmentMsg.getPoint(), robotBaseFrameId);
          Frame pAux = subscriber.cartesianPoseToRosFrame(robot.getRootFrame(), segmentMsg.getPointAux(), robotBaseFrameId);
          if (p != null && pAux != null) {
            segment = circ(p, pAux);
          }
          break;
        }
        default: {
          Logger.error("Unknown spline segment type: " + segmentMsg.getType());
          break;
        }
      }

      if (segment != null) {
        splineSegments.add(segment);
      }
      else {
        Logger.warn("Invalid spline segment: " + i);
        success = false;
      }

      i++;
    }

    if (success) {
      Logger.debug("Executing spline with " + splineSegments.size() + " segments");
      Spline spline = new Spline(splineSegments.toArray(new SplineMotionCP<?>[splineSegments.size()]));
      SpeedLimits.applySpeedLimits(spline);
      endPointFrame.moveAsync(spline, new PTPMotionFinishedEventListener(publisher, actionServer));
    }

    return success;
  }

  public void pointToPointJointPositionMotion(IMotionControlMode motion, iiwa_msgs.JointPosition commandPosition) {
    if (commandPosition != null) {
      Conversions.rosJointQuantityToKuka(commandPosition.getPosition(), jp);
      PTP ptpMotion = ptp(jp);
      SpeedLimits.applySpeedLimits(ptpMotion);
      robot.moveAsync(ptpMotion, new PTPMotionFinishedEventListener(publisher, actionServer));
    }
  }

  public void cartesianVelocityMotion(SmartServo motion, geometry_msgs.TwistStamped commandVelocity, ObjectFrame toolFrame) {
    if (commandVelocity != null) {
      if (loopPeriod > 1.0) {
        loopPeriod = 0.0;
      }

      motion.getRuntime().updateWithRealtimeSystem();
      Frame destinationFrame = motion.getRuntime().getCurrentCartesianDestination(toolFrame);

      destinationFrame.setX(commandVelocity.getTwist().getLinear().getX() * loopPeriod + destinationFrame.getX());
      destinationFrame.setY(commandVelocity.getTwist().getLinear().getY() * loopPeriod + destinationFrame.getY());
      destinationFrame.setZ(commandVelocity.getTwist().getLinear().getZ() * loopPeriod + destinationFrame.getZ());
      destinationFrame.setAlphaRad(commandVelocity.getTwist().getAngular().getX() * loopPeriod + destinationFrame.getAlphaRad());
      destinationFrame.setBetaRad(commandVelocity.getTwist().getAngular().getY() * loopPeriod + destinationFrame.getBetaRad());
      destinationFrame.setGammaRad(commandVelocity.getTwist().getAngular().getZ() * loopPeriod + destinationFrame.getGammaRad());
      previousTime = currentTime;

      if (robot.isReadyToMove()) {
        motion.getRuntime().setDestination(destinationFrame);
      }
      currentTime = System.nanoTime();
      // loopPeriod is stored in seconds.
      loopPeriod = (double) (currentTime - previousTime) / 1000000000.0;
    }
  }

  public void jointPositionMotion(SmartServo motion, iiwa_msgs.JointPosition commandPosition) {
    if (commandPosition != null) {
      Conversions.rosJointQuantityToKuka(commandPosition.getPosition(), jp);
      if (robot.isReadyToMove()) {
        motion.getRuntime().setDestination(jp);
      }
    }
  }

  public void jointPositionVelocityMotion(SmartServo motion, iiwa_msgs.JointPositionVelocity commandPositionVelocity) {
    if (commandPositionVelocity != null) {
      Conversions.rosJointQuantityToKuka(commandPositionVelocity.getPosition(), jp);
      Conversions.rosJointQuantityToKuka(commandPositionVelocity.getVelocity(), jv);
      if (robot.isReadyToMove()) {
        motion.getRuntime().setDestination(jp, jv);
      }
    }
  }

  public void jointVelocityMotion(SmartServo motion, iiwa_msgs.JointVelocity commandVelocity) {
    if (commandVelocity != null) {
      if (loopPeriod > 1.0) {
        loopPeriod = 0.0;
      }

      jp = motion.getRuntime().getCurrentJointDestination();
      // Compute the joint displacement over the current period.
      Conversions.rosJointQuantityToKuka(commandVelocity.getVelocity(), jointDisplacement, loopPeriod);
      Conversions.rosJointQuantityToKuka(commandVelocity.getVelocity(), jv);

      for (int i = 0; i < robot.getJointCount(); ++i) {
        double updatedPotision = jp.get(i) + jointDisplacement.get(i);
        if ((updatedPotision <= maxJointLimits.get(i) - softJointLimit && updatedPotision >= minJointLimits.get(i) + softJointLimit)) {
          // Add the displacement to the joint destination.
          jp.set(i, updatedPotision);
        }
      }
      previousTime = currentTime;

      if (robot.isReadyToMove())
      /*
       * This KUKA APIs should work, but notrly... && !(jp.isNearlyEqual(maxJointLimits, 0.1) ||
       * jp.isNearlyEqual(minJointLimits, 0.1))
       */
      {

        motion.getRuntime().setDestination(jp, jv);
      }

      currentTime = System.nanoTime();
      // loopPeriod is stored in seconds.
      loopPeriod = (double) (currentTime - previousTime) / 1000000000.0;
    }
  }

}
