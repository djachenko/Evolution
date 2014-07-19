package ru.nsu.fit.djachenko.evolution;

import java.io.Serializable;
import java.util.Random;

public class Agent implements Serializable
{
	private int x;
	private int y;

	private transient Cell owner;

	private static final Random RANDOM = new Random(System.currentTimeMillis());
	private static final AgentPool POOL = AgentPool.getInstance();

	private static final Direction[] DIRECTIONS = Direction.values();

	private static int breedcount = 0;
	private static int diecount = 0;
	private static int movecount = 0;
	private static int survcount = 0;

	Agent()
	{
		setState(-1, -1);
	}

	Agent(int x, int y)
	{
		setState(x, y);
	}

	public void breed()
	{
		Agent pair = owner.findNearest(x, y);

		System.out.println("breed");

		if (pair != null)
		{
			Direction breedDirection = DIRECTIONS[RANDOM.nextInt(DIRECTIONS.length)];
			int distance = RANDOM.nextInt(Constants.DELTA + 1);

			int dx = breedDirection.getDx() * distance;
			int dy = breedDirection.getDy() * distance;

			if (owner.isEmpty(x + dx, y + dy))
			{
				owner.add(POOL.get(x + dx, y + dy));

				System.out.println("success");
				breedcount++;
			}
		}
	}

	public void move()
	{
		Direction moveDirection = DIRECTIONS[RANDOM.nextInt(DIRECTIONS.length)];
		int distance = RANDOM.nextInt(Constants.DELTA + 1);

		int dx = moveDirection.getDx() * distance;
		int dy = moveDirection.getDy() * distance;

		System.out.println(x + " " + (y) + " " + (owner == null));

		if (owner.isEmpty(x + dx, y + dy))
		{
			owner.move(this, x + dx, y + dy);
			System.out.println("move");

			movecount++;
		}
		else
		{
			survive();
			System.out.println("survive");

			survcount++;
		}
	}

	public void die()
	{
		owner.remove(this);
		diecount++;
	}

	public void survive()
	{
		owner.save(this);
		survcount++;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	void setState(int x, int y)
	{
		this.x = x;
		this.y = y;
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

	public static void stat()
	{
		System.out.println("breedcount = " + breedcount);
		System.out.println("diecount = " + diecount);
		System.out.println("movecount = " + movecount);
		System.out.println("survcount = " + survcount);
	}
}
