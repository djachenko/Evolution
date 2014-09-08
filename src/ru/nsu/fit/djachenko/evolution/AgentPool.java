package ru.nsu.fit.djachenko.evolution;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

//singletone class for managing agents reusing
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

	public Agent get(int x, int y, int geneA, int geneB)
	{
		Agent candidate;

		synchronized (pool)
		{
			//agent is being retrieved from global queue (own for every MPI process)
			candidate = pool.poll();
		}

		//if queue was empty, agent is created and may return to queue later
		if (candidate == null)
		{
			candidate = new Agent();
		}

		//now agent is being initialized.
		candidate.setState(x, y, geneA, geneB);

		return candidate;
	}

	public void add(Agent agent)
	{
		//returning agent back to queue
		synchronized (pool)
		{
			pool.add(agent);
		}
	}

	public void addAll(Collection<? extends Agent> agents)
	{
		//sometimes it's useful to return number of agents simultaneously
		synchronized (pool)
		{
			pool.addAll(agents);
		}
	}
}
