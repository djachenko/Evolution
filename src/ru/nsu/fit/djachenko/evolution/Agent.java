package ru.nsu.fit.djachenko.evolution;

import java.io.Serializable;
import java.util.Random;

public class Agent implements Serializable
{
	private int x;
	private int y;

	private int geneA;
	private int geneB;

	//owner shouldn't be transferred because it will be assigned after receiving anyway
	private transient Cell owner;

	private static final Random RANDOM = new Random(System.currentTimeMillis());
	//for reusing agents instead of creating.
	private static final AgentPool POOL = AgentPool.getInstance();

	private static final Direction[] DIRECTIONS = Direction.values();

	Agent()
	{
		setState(-1, -1, 127, 127);
	}

	public void breed()
	{
		//Agent tries to breed: tries to find pair and, in case of success, tries to produce new agent
		Agent pair = owner.findNearest(x, y);

		if (pair != null)
		{
			Direction breedDirection = DIRECTIONS[RANDOM.nextInt(DIRECTIONS.length)];
			int distance = RANDOM.nextInt(Constants.DELTA + 1);

			//shift from current agent is chosen arbitrary, but it can't be diagonal
			int dx = breedDirection.getDx() * distance;
			int dy = breedDirection.getDy() * distance;

			/*checks if chosen random place near agent is free and, if yes, puts new agent there*/
			if (owner.isEmpty(x + dx, y + dy))
			{
				Agent child = POOL.get(x + dx, y + dy, this.geneB, pair.geneA);

				owner.add(child);
			}
		}
	}

	public void move()
	{
		Direction moveDirection = DIRECTIONS[RANDOM.nextInt(DIRECTIONS.length)];
		int distance = RANDOM.nextInt(Constants.DELTA + 1);

		int dx = moveDirection.getDx() * distance;
		int dy = moveDirection.getDy() * distance;

		//agent can move to arbitrary chosen number of cell, but only in straight directions
		//movement happens only if chosen place is empty.
		if (owner.isEmpty(x + dx, y + dy))
		{
			owner.move(this, x + dx, y + dy);
		}
		else
		{
			//if not, agent just stays still
			survive();
		}
	}

	public void die()
	{
		//death is implemented by marking this agent. once agent is marked, it won't appear on next iteration
		owner.remove(this);
	}

	public void survive()
	{
		//if agent survives, pointer to it is moved to next generation
		owner.save(this);
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getFenotype()
	{
		return (geneA + geneB) / 2;
	}

	public int getGeneA()
	{
		return geneA;
	}

	public int getGeneB()
	{
		return geneB;
	}

	//there is method for setting state because we need to set state of already allocated object when reusing
	void setState(int x, int y, int fatherGene, int motherGene)
	{
		this.x = x;
		this.y = y;
		this.geneA = fatherGene;
		this.geneB = motherGene;
	}

	public void setOwner(Cell owner)
	{
		this.owner = owner;
	}

	@Override
	public String toString()
	{
		return "agent {" + x + "; " + y + '}';
	}
}
