package ru.nsu.fit.djachenko.evolution;

import mpi.Intracomm;
import mpi.MPI;
import mpi.Request;

import java.util.*;
import java.util.stream.Collectors;

public class Cell implements Runnable
{
	int[] sizes = new int[4];

	private Set<Agent> currentGeneration = new HashSet<>();
	private Set<Agent> nextGeneration = new HashSet<>();

	private final FindStrategy findStrategy = new FindStrategy();
	private final Random random = new Random();

	private final AgentPool agentPool = AgentPool.getInstance();

	private final Serializer serializer = new Serializer();

	private final int size;

	private final int topRank;
	private final int bottomRank;

	private final Intracomm communicator;

	private byte[] topBuffer = new byte[1024];
	private byte[] bottomBuffer = new byte[1024];
	private byte[] metaBuffer = new byte[1024];

	private final int[] topSizeBuffer = new int[2];
	private final int[] bottomSizeBuffer = new int[2];
	private final int[] metaSizeBuffer = new int[2];

	public Cell(Intracomm communicator, int xPosition)
	{
		this.communicator = communicator;

		int rank = communicator.Rank();
		this.size = communicator.Size();

		this.topRank = rank + 1;
		this.bottomRank = rank - 1;

		int cellHeight = (int) Math.ceil(1.0 * Constants.HEIGHT / Constants.GRID_HEIGHT);
		int cellWidth = (int) Math.ceil(1.0 * Constants.WIDTH / Constants.GRID_WIDTH);

		sizes[Constants.BOTTOM] = (rank - 1) * cellHeight;
		sizes[Constants.TOP] = rank * cellHeight;
		sizes[Constants.LEFT] = xPosition * cellWidth;
		sizes[Constants.RIGHT] = (xPosition + 1) * cellWidth;

		Agent agent = AgentPool.getInstance().get(sizes[Constants.LEFT] + 1, sizes[Constants.BOTTOM] + 1);

		add(agent);

		System.out.println(agent + " " + rank);
	}

	Agent findNearest(int x, int y)
	{
		List<Agent> candidates = currentGeneration.stream()
		                                          .filter(agent -> Math.abs(agent.getX() - x) <= Constants.DELTA &&
		                                                           Math.abs(agent.getY() - y) <= Constants.DELTA)
		                                          .collect(Collectors.toCollection(LinkedList::new));

		return findStrategy.choose(candidates, x, y);
	}

	boolean isEmpty(int x, int y)
	{
		for (Agent agent : currentGeneration)
		{
			if (agent.getX() == x && agent.getY() == y)
			{
				return false;
			}
		}

		return true;
	}

	boolean nextEmpty(int x, int y)
	{
		for (Agent agent : nextGeneration)
		{
			if (agent.getX() == x && agent.getY() == y)
			{
				return false;
			}
		}

		return true;
	}

	void add(Agent agent)
	{
		nextGeneration.add(agent);

		agent.setOwner(this);
	}

	void move(Agent agent, int toX, int toY)
	{
		add(agentPool.get(toX, toY));
	}

	void remove(Agent agent)
	{
		nextGeneration.remove(agent);
	}

	void save(Agent agent)
	{
	add(agent);
	}

	@Override
	public void run()
	{
		for (int i = 0; i < Constants.ITERATION_COUNT; i++)
		{
			countIteration();

			sync();
		}
	}

	private void countIteration()
	{
		Set<Agent> temp = currentGeneration;

		currentGeneration = nextGeneration;
		nextGeneration = temp;

		nextGeneration.clear();

		Set<Agent> agents = currentGeneration.stream()
		                                     .filter(agent -> agent.getX() >= sizes[Constants.LEFT] &&
		                                                      agent.getX() < sizes[Constants.RIGHT] &&
		                                                      agent.getY() >= sizes[Constants.BOTTOM] &&
		                                                      agent.getY() < sizes[Constants.TOP])
		                                     .collect(Collectors.toSet());

		agents.stream()
		      .filter(agent -> random.nextInt(Constants.BREEDCHANCE) == 0)
		      .forEach(Agent::breed);

		for (Agent agent : agents)
		{
			if (random.nextInt(Constants.DIECHANCE) == 0)
			{
				agent.die();
			}
			else if (random.nextInt(Constants.MOVECHANCE) == 0)
			{
				agent.move();
			}
			else
			{
				agent.survive();
			}
		}

		currentGeneration.removeAll(nextGeneration);

		AgentPool.getInstance().addAll(currentGeneration);
	}

	void populate()
	{
		for (int i = 0; i < 10; i++)
		{
			Agent agent = new Agent(random.nextInt(sizes[Constants.RIGHT] - sizes[Constants.LEFT]) + sizes[Constants.LEFT],
			                        random.nextInt(sizes[Constants.TOP] - sizes[Constants.BOTTOM]) + sizes[Constants.BOTTOM]);

			while (!nextEmpty(agent.getX(), agent.getY()))
			{
				agent = new Agent(random.nextInt(sizes[Constants.RIGHT] - sizes[Constants.LEFT]) + sizes[Constants.LEFT],
				                  random.nextInt(sizes[Constants.TOP] - sizes[Constants.BOTTOM]) + sizes[Constants.BOTTOM]);
			}

			add(agent);
			System.out.println(agent);
		}
	}

	void sync()
	{
		int top = sizes[Constants.TOP];
		int bottom = sizes[Constants.BOTTOM];
		int left = sizes[Constants.LEFT];
		int right = sizes[Constants.RIGHT];

		byte[] metaArray = serializer.serialize(nextGeneration.stream()
		                                                      .filter(agent -> agent.getX() < left + Constants.DELTA ||
		                                                                       agent.getX() >= right - Constants.DELTA)
		                                                      .collect(Collectors.toSet()));

		System.out.println("metaArray.length = " + metaArray.length);

		metaSizeBuffer[0] = metaArray.length;

		communicator.Isend(metaSizeBuffer, 0, 1, MPI.INT, 0, Tags.META_SIZE_TAG);
		Request metaDataSendRequest = communicator.Isend(metaArray, 0, metaArray.length, MPI.BYTE, 0, Tags.META_DATA_TAG);

		Request topDataSendRequest = null;
		Request bottomDataSendRequest = null;

		Request topSizeRecvRequest = null;
		Request bottomSizeRecvRequest = null;

		if (topRank < size && topRank > 0)
		{
			byte[] topArray = serializer.serialize(nextGeneration.stream()
			                                                     .filter(agent -> top - agent.getY() <=
			                                                                      Constants.DELTA &&
			                                                                      agent.getX() >= left &&
			                                                                      agent.getX() < right)
			                                                     .collect(Collectors.toSet()));

			topSizeBuffer[0] = topArray.length;

			communicator.Isend(topSizeBuffer, 0, 1, MPI.INT, topRank, Tags.LEFT_SIZE_TAG);
			topDataSendRequest = communicator.Isend(topArray, 0, topArray.length, MPI.BYTE, topRank, Tags.LEFT_DATA_TAG);

			topSizeRecvRequest = communicator.Irecv(topSizeBuffer, 1, 1, MPI.INT, topRank, Tags.RIGHT_SIZE_TAG);
		}

		if (bottomRank > 0 && bottomRank < size)
		{
			byte[] bottomArray = serializer.serialize(nextGeneration.stream()
			                                                        .filter(agent -> agent.getY() - bottom <
			                                                                         Constants.DELTA &&
			                                                                         agent.getX() >= left &&
			                                                                         agent.getX() < right)
			                                                        .collect(Collectors.toSet()));

			bottomSizeBuffer[0] = bottomArray.length;

			communicator.Isend(bottomSizeBuffer, 0, 1, MPI.INT, bottomRank, Tags.RIGHT_SIZE_TAG);
			bottomDataSendRequest = communicator.Isend(bottomArray, 0, bottomArray.length, MPI.BYTE, bottomRank, Tags.RIGHT_DATA_TAG);

			bottomSizeRecvRequest = communicator.Irecv(bottomSizeBuffer, 1, 1, MPI.INT, bottomRank, Tags.LEFT_SIZE_TAG);
		}

		Request metaSizeRecvRequest = communicator.Irecv(metaSizeBuffer, 1, 1, MPI.INT, 0, Tags.META_SIZE_TAG);

		Request topDataRecvRequest = null;

		if (topSizeRecvRequest != null)
		{
			topSizeRecvRequest.Wait();

			int topSize = topSizeBuffer[1];
			topBuffer = topSize > topBuffer.length ? new byte[topSize] : topBuffer;

			topDataRecvRequest = communicator.Irecv(topBuffer, 0, topBuffer.length, MPI.BYTE, topRank, Tags.RIGHT_DATA_TAG);
		}

		Request bottomDataRecvRequest = null;

		if (bottomSizeRecvRequest != null)
		{
			bottomSizeRecvRequest.Wait();

			int bottomSize = bottomSizeBuffer[1];
			bottomBuffer = bottomSize > bottomBuffer.length ? new byte[bottomSize] : bottomBuffer;

			bottomDataRecvRequest = communicator.Irecv(bottomBuffer, 0, bottomBuffer.length, MPI.BYTE, bottomRank, Tags.LEFT_DATA_TAG);
		}

		metaSizeRecvRequest.Wait();

		int metaSize = metaSizeBuffer[1];
		metaBuffer = metaSize > metaBuffer.length ? new byte[metaSize] : metaBuffer;

		Request metaDataRecvRequest = communicator.Irecv(metaBuffer, 0, metaBuffer.length, MPI.BYTE, 0, Tags.META_DATA_TAG);

		if (topDataRecvRequest != null)
		{
			topDataRecvRequest.Wait();

			serializer.deserialize(topBuffer).forEach(this::add);
		}

		if (bottomDataRecvRequest != null)
		{
			bottomDataRecvRequest.Wait();

			serializer.deserialize(bottomBuffer).forEach(this::add);
		}

		metaDataRecvRequest.Wait();

		serializer.deserialize(metaBuffer).forEach(this::add);

		metaDataSendRequest.Wait();

		if (topDataSendRequest != null)
		{
			topDataSendRequest.Wait();
		}

		if (bottomDataSendRequest != null)
		{
			bottomDataSendRequest.Wait();
		}

		communicator.Barrier();

		//maybe at start!
		//or not to wait
	}

	@Override
	public String toString()
	{
		String border = border();

		return border +
		       toSection(sizes[Constants.TOP] + Constants.DELTA, sizes[Constants.TOP]) +
		       border +
		       toSection(sizes[Constants.TOP], sizes[Constants.BOTTOM]) +
		       border +
		       toSection(sizes[Constants.BOTTOM], sizes[Constants.BOTTOM] - Constants.DELTA) +
		       border +
		       "\n ";
	}

	private String border()
	{
		StringBuilder builder = new StringBuilder();

		for (int x = sizes[Constants.LEFT] - Constants.DELTA - 2; x < sizes[Constants.RIGHT] + Constants.DELTA + 2; x++)
		{
			builder.append("NN");
		}

		builder.append('\n');

		return builder.toString();
	}

	private String toSection(int topY, int bottomY)
	{
		StringBuilder builder = new StringBuilder();
		String border = "NN";

		for (int y = topY - 1; y >= bottomY; y--)
		{
			builder.append(border)
			       .append(subLine(y, sizes[Constants.LEFT] - Constants.DELTA, sizes[Constants.LEFT]))
			       .append(border)
			       .append(subLine(y, sizes[Constants.LEFT], sizes[Constants.RIGHT]))
			       .append(border)
			       .append(subLine(y, sizes[Constants.RIGHT], sizes[Constants.RIGHT] + Constants.DELTA))
			       .append(border)
			       .append('\n');
		}

		return builder.toString();
	}

	private String subLine(int y, int xMin, int xMax)
	{
		StringBuilder builder = new StringBuilder();

		for (int x = xMin; x < xMax; x++)
		{
			builder.append(testCell(x, y));
		}

		return builder.toString();
	}

	private String testCell(int x, int y)
	{
		if (nextGeneration.stream()
		                  .anyMatch(agent -> agent.getX() == x && agent.getY() == y))
		{
			return String.valueOf(x) + String.valueOf(y);
		}
		else
		{
			return "  ";
		}
	}
}
