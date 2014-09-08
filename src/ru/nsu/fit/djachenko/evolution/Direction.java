package ru.nsu.fit.djachenko.evolution;

//helper class for directions management. mainly handles offsets
public enum Direction
{
	LEFT(-1, 0)
			{
				@Override
				public Direction opposite()
				{
					return RIGHT;
				}
			},
	UP(0, -1)
			{
				@Override
				public Direction opposite()
				{
					return DOWN;
				}
			},//y axis is down-directed
	RIGHT(1, 0)
			{
				@Override
				public Direction opposite()
				{
					return LEFT;
				}
			},
	DOWN(0, 1)
			{
				@Override
				public Direction opposite()
				{
					return UP;
				}
			};//y axis is down-directed


	private final int dx;
	private final int dy;

	private Direction(int x, int y)
	{
		dx = x;
		dy = y;
	}

	public int getDx()
	{
		return dx;
	}

	public int getDy()
	{
		return dy;
	}

	public abstract Direction opposite();

	public boolean isHorisontal()
	{
		return dy == 0;
	}

	public boolean isVertical()
	{
		return dx == 0;
	}

	public static Direction recognize(int dx, int dy)
	{
		if (dx < 0 && dy == 0)
		{
			return LEFT;
		}

		if (dx > 0 && dy == 0)
		{
			return RIGHT;
		}

		if (dx == 0 && dy < 0)
		{
			return UP;
		}

		if (dx == 0 && dy > 0)
		{
			return DOWN;
		}

		return null;
	}
}
