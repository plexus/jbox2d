package org.jbox2d.dynamics.joints;

import org.jbox2d.common.Mat22;
import org.jbox2d.common.MathUtils;
import org.jbox2d.common.Settings;
import org.jbox2d.common.Transform;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.TimeStep;
import org.jbox2d.pooling.TLMat22;
import org.jbox2d.pooling.TLVec2;

public class MouseJoint extends Joint {

	private final Vec2 m_localAnchor = new Vec2();
	private final Vec2 m_target = new Vec2();
	private final Vec2 m_impulse = new Vec2();

	private final Mat22 m_mass = new Mat22();	// effective mass for point-to-point constraint.
	private final Vec2 m_C = new Vec2();		// position error
	private float m_maxForce;
	private float m_frequencyHz;
	private float m_dampingRatio;
	private float m_beta;
	private float m_gamma;
	

	protected MouseJoint(MouseJointDef def) {
		super(def);
		assert(def.target.isValid());
		assert(def.maxForce >= 0);
		assert(def.frequencyHz >= 0);
		assert(def.dampingRatio >= 0);
		
		m_target.set(def.target);
		Transform.mulTransToOut(m_bodyB.getTransform(), m_target, m_localAnchor);
		
		m_maxForce = def.maxForce;
		m_impulse.setZero();
		
		m_frequencyHz = def.frequencyHz;
		m_dampingRatio = def.dampingRatio;
		
		m_beta = 0;
		m_gamma = 0;
	}
	
	@Override
	public Vec2 getAnchorA() {
		return m_target;
	}

	private final Vec2 anchorBPool = new Vec2();
	@Override
	public Vec2 getAnchorB() {
		m_bodyB.getWorldPointToOut(m_localAnchor, anchorBPool);
		return anchorBPool;
	}

	private final Vec2 reactionForcePool = new Vec2();
	@Override
	public Vec2 getReactionForce(float invDt) {
		reactionForcePool.set(m_impulse).mulLocal(invDt);
		return reactionForcePool;
	}

	@Override
	public float getReactionTorque(float invDt) {
		return invDt * 0.0f;
	}

	
	public void setTarget( Vec2 target){
		if(m_bodyB.isAwake() == false){
			m_bodyB.setAwake(true);
		}
		m_target.set(target);
	}
	public Vec2 getTarget(){
		return m_target;
	}

	/// set/get the maximum force in Newtons.
	public void setMaxForce(float force){
		m_maxForce = force;
	}
	public float getMaxForce(){
		return m_maxForce;
	}

	/// set/get the frequency in Hertz.
	public void setFrequency(float hz){
		m_frequencyHz = hz;
	}
	public float getFrequency(){
		return m_frequencyHz;
	}

	/// set/get the damping ratio (dimensionless).
	public void setDampingRatio(float ratio){
		m_dampingRatio = ratio;
	}
	public float getDampingRatio(){
		return m_dampingRatio;
	}
	
	private static final TLVec2 tlr = new TLVec2();
	private static final TLMat22 tlK1 = new TLMat22();
	private static final TLMat22 tlK2 = new TLMat22();
	private static final TLMat22 tlK = new TLMat22();

	@Override
	public void initVelocityConstraints(TimeStep step) {
		Body b = m_bodyB;

		float mass = b.getMass();

		// Frequency
		float omega = 2.0f * MathUtils.PI * m_frequencyHz;

		// Damping coefficient
		float d = 2.0f * mass * m_dampingRatio * omega;

		// Spring stiffness
		float k = mass * (omega * omega);

		// magic formulas
		// gamma has units of inverse mass.
		// beta has units of inverse time.
		assert(d + step.dt * k > Settings.EPSILON);
		m_gamma = step.dt * (d + step.dt * k);
		if (m_gamma != 0.0f)
		{
			m_gamma = 1.0f / m_gamma;
		}
		m_beta = step.dt * k * m_gamma;

		Vec2 r = tlr.get();
		// Compute the effective mass matrix.
		//Vec2 r = Mul(b.getTransform().R, m_localAnchor - b.getLocalCenter());
		r.set(m_localAnchor).subLocal(b.getLocalCenter());
		Mat22.mulToOut(b.getTransform().R, r, r);
		
		// K    = [(1/m1 + 1/m2) * eye(2) - skew(r1) * invI1 * skew(r1) - skew(r2) * invI2 * skew(r2)]
		//      = [1/m1+1/m2     0    ] + invI1 * [r1.y*r1.y -r1.x*r1.y] + invI2 * [r1.y*r1.y -r1.x*r1.y]
		//        [    0     1/m1+1/m2]           [-r1.x*r1.y r1.x*r1.x]           [-r1.x*r1.y r1.x*r1.x]
		float invMass = b.m_invMass;
		float invI = b.m_invI;

		Mat22 K1 = tlK1.get();
		K1.col1.x = invMass;	K1.col2.x = 0.0f;
		K1.col1.y = 0.0f;		K1.col2.y = invMass;

		Mat22 K2 = tlK2.get();
		K2.col1.x =  invI * r.y * r.y;	K2.col2.x = -invI * r.x * r.y;
		K2.col1.y = -invI * r.x * r.y;	K2.col2.y =  invI * r.x * r.x;

		Mat22 K = tlK.get();
		K.set(K1).addLocal(K2);
		K.col1.x += m_gamma;
		K.col2.y += m_gamma;

		K.invertToOut(m_mass);

		m_C.set(b.m_sweep.c).addLocal(r).subLocal(m_target);

		// Cheat with some damping
		b.m_angularVelocity *= 0.98f;

		// Warm starting.
		m_impulse.mulLocal(step.dtRatio);
		// pool
		r.set(m_impulse).mulLocal(invMass);
		b.m_linearVelocity.addLocal(r);
		b.m_angularVelocity += invI * Vec2.cross(r, m_impulse);
	}

	@Override
	public boolean solvePositionConstraints(float baumgarte) {
		return true;
	}

	private static final TLVec2 tlCdot = new TLVec2();
	private static final TLVec2 tlimpulse = new TLVec2();
	private static final TLVec2 tloldImpulse = new TLVec2();

	@Override
	public void solveVelocityConstraints(TimeStep step) {
		Body b = m_bodyB;

		Vec2 r = tlr.get();
		r.set(m_localAnchor.subLocal(b.getLocalCenter()));
		Mat22.mulToOut(b.getTransform().R, r, r);
		
		// Cdot = v + cross(w, r)
		Vec2 Cdot = tlCdot.get();
		Vec2.crossToOut(b.m_angularVelocity, r, Cdot);
		Cdot.addLocal(b.m_linearVelocity);
		
		Vec2 impulse = tlimpulse.get();
		Vec2 temp = tloldImpulse.get();
		//Mul(m_mass, -(Cdot + m_beta * m_C + m_gamma * m_impulse));
		impulse.set(m_C).mulLocal(m_beta);
		temp.set(m_impulse).mulLocal(m_gamma);
		temp.addLocal(impulse).addLocal(Cdot).mulLocal(-1);
		Mat22.mulToOut(m_mass, temp, impulse);

		Vec2 oldImpulse = temp;
		oldImpulse.set(m_impulse);
		m_impulse.addLocal(impulse);
		float maxImpulse = step.dt * m_maxForce;
		if (m_impulse.lengthSquared() > maxImpulse * maxImpulse){
			m_impulse.mulLocal(maxImpulse / m_impulse.length());
		}
		impulse.set(m_impulse).subLocal(oldImpulse);

		// pooling
		oldImpulse.set(impulse).mulLocal(b.m_invMass);
		b.m_linearVelocity.addLocal(oldImpulse);
		b.m_angularVelocity += b.m_invI * Vec2.cross(r, impulse);
	}

}