package ru.nsu.fit.djachenko.evolution;

public class FindStrategy
{
	private static int sqr(int a)
	{
		return a * a;
	}

	public Agent choose(Iterable<Agent> candidates, int x, int y)
	{
		double delta = -1;
		Agent cache = null;

		for (Agent candidate : candidates)
		{
			double distance = Math.sqrt(sqr(candidate.getX() - x) + sqr(candidate.getY() - y));

			if (distance < delta || delta == -1)
			{
				delta = distance;
				cache = candidate;
			}
		}

		return cache;
	}
}
