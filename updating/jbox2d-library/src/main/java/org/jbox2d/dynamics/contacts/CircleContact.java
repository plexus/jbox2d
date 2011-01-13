package org.jbox2d.dynamics.contacts;

import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.ShapeType;
import org.jbox2d.common.Transform;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.pooling.SingletonPool;

// updated to rev 100

public class CircleContact extends Contact {

	public CircleContact(){}
	
	public void init(Fixture fixtureA, Fixture fixtureB){
		super.init(fixtureA, fixtureB);
		assert(m_fixtureA.getType() == ShapeType.CIRCLE);
		assert(m_fixtureB.getType() == ShapeType.CIRCLE);
	}
	
	@Override
	public void evaluate(Manifold manifold, Transform xfA, Transform xfB) {
		SingletonPool.getCollision().collideCircles(manifold,
				(CircleShape)m_fixtureA.getShape(), xfA,
				(CircleShape)m_fixtureB.getShape(), xfB);
	}
}