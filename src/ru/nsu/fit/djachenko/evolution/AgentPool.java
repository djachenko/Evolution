package ru.nsu.fit.djachenko.evolution;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

public class AgentPool
{
	private final Queue<Agent> pool = new LinkedList<>();
	private static final AgentPool instance = new AgentPool();

	private AgentPool()
	{}

	public static AgentPool getInstance()
	{
		return instance;
	}

	public Agent get(int x, int y)
	{
		Agent candidate;

		synchronized (pool)
		{
			if (!pool.isEmpty())
			{
				candidate = pool.peek();
				pool.poll();
			}
			else
			{
				candidate = new Agent();
			}
		}

		candidate.setState(x, y);

		return candidate;
	}

	public void add(Agent agent)
	{
		synchronized (pool)
		{
			pool.add(agent);
		}
	}

	public void addAll(Collection<? extends Agent> agents)
	{
		synchronized (pool)
		{
			pool.addAll(agents);
		}
	}
}
