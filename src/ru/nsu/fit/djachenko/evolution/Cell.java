package ru.nsu.fit.djachenko.evolution;

import mpi.Intracomm;
import mpi.MPI;
import mpi.Request;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Cell implements Runnable
{
	//array with cell bounds. is used for determining wheter agent has left this cell
	Rectangle bounds;

	//two agent generations. current generation is read on every iteration, next generation is being counted based on current.
	private Set<Agent> currentGeneration = new HashSet<>();
	private Set<Agent> nextGeneration = new HashSet<>();

	//strategy for choosing pair for breeding agent
	private final FindStrategy findStrategy = new FindStrategy();
	private final Random random = new Random();

	//pool for reusing agents
	private final AgentPool agentPool = AgentPool.getInstance();

	//serializer is used for preparing data for sending and interpreting received data
	private final Serializer serializer = new Serializer();

	//number of cells in column communicator
	private final int size;

	//rank of upper cell relatively to current
	private final int topRank;
	//rank of lower cell
	private final int bottomRank;

	//comunicator for sending data within column
	private final Intracomm columnCommunicator;

	//buffers for receiving data. if their size will become too small, they will be enlarged
	private byte[] topBuffer = new byte[1024];
	private byte[] bottomBuffer = new byte[1024];
	private byte[] metaBuffer = new byte[1024];

	//buffers for sending and receivig sizes of transmitted data.
	//one cell for received data size and one for sent data size
	private final int[] topSizeBuffer = new int[2];
	private final int[] bottomSizeBuffer = new int[2];
	private final int[] metaSizeBuffer = new int[2];

	private final Intracomm outputCommunicator;

	public Cell(Intracomm columnCommunicator, Intracomm outputCommunicator, int xPosition)
	{
		this.columnCommunicator = columnCommunicator;
		this.outputCommunicator = outputCommunicator;

		int rank = columnCommunicator.Rank();
		this.size = columnCommunicator.Size();

		//determining current cell place in column
		this.topRank = rank + 1;
		this.bottomRank = rank - 1;

		int cellHeight = (int) Math.ceil(1.0 * Constants.HEIGHT / Constants.GRID_HEIGHT);
		int cellWidth = (int) Math.ceil(1.0 * Constants.WIDTH / Constants.GRID_WIDTH);

		//counting its bounds
		bounds = new Rectangle(xPosition * cellWidth, (rank - 1) * cellHeight, cellWidth, cellHeight);

		Agent agent = AgentPool.getInstance().get((int)bounds.getX() + 1, (int)bounds.getY() + 1, random.nextInt(254), random.nextInt(254));

		add(agent);
	}

	//finding pair for breeding agent
	Agent findNearest(int x, int y)
	{
		//current generation is filtered by straight distance from breeding agent
		//this distance is set as paramater and can be changed.
		List<Agent> candidates = currentGeneration.stream()
		                                          .filter(agent -> Math.abs(agent.getX() - x) <= Constants.DELTA &&
		                                                           Math.abs(agent.getY() - y) <= Constants.DELTA)
		                                          .collect(Collectors.toCollection(LinkedList::new));

		//then strategy chooses one from filtered above
		return findStrategy.choose(candidates, x, y);
	}

	//checks if there isn't agent with given coordinates in current generation
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

	//agent is added to next generation
	void add(Agent agent)
	{
		nextGeneration.add(agent);

		//agent owner is set as delegate.
		agent.setOwner(this);
	}

	//"moving agent". next generation is generated from scrath
	//we can't just add agent to next generation with changed coordinates because it still can be used by other agents on current iteration
	void move(Agent agent, int toX, int toY)
	{
		add(agentPool.get(toX, toY, agent.getGeneA(), agent.getGeneB()));
	}

	//if agent was previously added to next generation, it will be removed. if not, nothing happens
	void remove(Agent agent)
	{
		nextGeneration.remove(agent);
	}

	//agent will be just copied to next generation
	void save(Agent agent)
	{
	add(agent);
	}

	//iteration method
	@Override
	public void run()
	{
		//each iteration contains three phases: counting
		countIteration();

		//synchronization
		sync();

		//and sending data for output
		drawSync();
	}

	private void countIteration()
	{
		Set<Agent> temp = currentGeneration;

		//next generation is being set as current
		currentGeneration = nextGeneration;
		nextGeneration = temp;

		nextGeneration.clear();

		int top = (int)(bounds.getY() + bounds.getHeight());
		int bottom = (int)bounds.getY();
		int left = (int)bounds.getX();
		int right = (int)(bounds.getX() + bounds.getWidth());

		//current generation contains not only cell, but also its edges
		//here we are creating subset of generation, which doesn't contain edges
		Set<Agent> agents = currentGeneration.stream()
		                                     .filter(agent -> agent.getX() >= left &&
		                                                      agent.getX() < right &&
		                                                      agent.getY() >= bottom &&
		                                                      agent.getY() < top)
		                                     .collect(Collectors.toSet());

		//creating arbitrary subset of agents and making each of them breed
		agents.stream()
		      .filter(agent -> random.nextInt(Constants.BREEDCHANCE) == 0)
		      .forEach(Agent::breed);

		//iterating through agents is required this way because of more complex causes
		for (Agent agent : agents)
		{
			//for every agent we choose if it dies
			if (random.nextInt(Constants.DIECHANCE) == 0)
			{
				agent.die();
			}
			//if it survives, it tries to move
			else if (random.nextInt(Constants.MOVECHANCE) == 0)
			{
				agent.move();
			}
			//and, if not, it just survives
			else
			{
				agent.survive();
			}
		}

		//here we leave in current generation those who wasn't copied to next generation directly
		currentGeneration.removeAll(nextGeneration);

		//and then all of them are returned to pool
		AgentPool.getInstance().addAll(currentGeneration);
	}

	void sync()
	{
		int top = (int)(bounds.getY() + bounds.getHeight());
		int bottom = (int)bounds.getY();
		int left = (int)bounds.getX();
		int right = (int)(bounds.getX() + bounds.getWidth());

		//here we create subset of agents who moved to left/right cells or are visible from them
		//and then they are serialized into byte array (MPI send functions require arrays as parameters) (more in Serializer.java)
		byte[] metaArray = serializer.serialize(nextGeneration.stream()
		                                                      .filter(agent -> agent.getX() < left + Constants.DELTA ||
		                                                                       agent.getX() >= right - Constants.DELTA)
		                                                      .collect(Collectors.toSet()));

		//its size is stored into array cell
		metaSizeBuffer[0] = metaArray.length;

		//and then this cell is sent as subarray with length 1
		//we have to send size before data in order to let receiving cell prepare enough place for data

		//this agents are sent to metacell, which manages horizontal synchronization
		//metacell always has 0 rank within column communicator
		columnCommunicator.Isend(metaSizeBuffer, 0, 1, MPI.INT, 0, Tags.META_SIZE_TAG);
		//we don't need to wait for receiving size, because this will definetely happen before data receiving
		//they won't be messed because of tags
		//but we have to wait data receiving
		Request metaDataSendRequest = columnCommunicator.Isend(metaArray, 0, metaArray.length, MPI.BYTE, 0, Tags.META_DATA_TAG);

		//requests for sending/receiving data from top/bottom can be not initialized
		//(if current cell is top or bottom)
		Request topDataSendRequest = null;
		Request bottomDataSendRequest = null;

		Request topSizeRecvRequest = null;
		Request bottomSizeRecvRequest = null;

		//if topRank is valid...
		if (topRank < size && topRank > 0)
		{
			//...serialize moved and visible from top agents to array...
			byte[] topArray = serializer.serialize(nextGeneration.stream()
			                                                     .filter(agent -> top - agent.getY() <=
			                                                                      Constants.DELTA &&
			                                                                      agent.getX() >= left &&
			                                                                      agent.getX() < right)
			                                                     .collect(Collectors.toSet()));

			//...store its size...
			topSizeBuffer[0] = topArray.length;

			//...and send them the same way
			columnCommunicator.Isend(topSizeBuffer, 0, 1, MPI.INT, topRank, Tags.LEFT_SIZE_TAG);
			topDataSendRequest = columnCommunicator.Isend(topArray, 0, topArray.length, MPI.BYTE, topRank, Tags.LEFT_DATA_TAG);

			//also cell starts to wait for receiving size of data which will be sent from top
			topSizeRecvRequest = columnCommunicator.Irecv(topSizeBuffer, 1, 1, MPI.INT, topRank, Tags.RIGHT_SIZE_TAG);
		}

		//here the same logic happens, changing several details:
		//1. rank valid bounds
		//2. predicate for filtering
		//3. rank
		//4. tags
		if (bottomRank > 0 && bottomRank < size)
		{
			byte[] bottomArray = serializer.serialize(nextGeneration.stream()
			                                                        .filter(agent->agent.getY() - bottom <
			                                                                       Constants.DELTA &&
			                                                                       agent.getX() >= left &&
			                                                                       agent.getX() < right)
			                                                        .collect(Collectors.toSet()));

			bottomSizeBuffer[0] = bottomArray.length;

			columnCommunicator.Isend(bottomSizeBuffer, 0, 1, MPI.INT, bottomRank, Tags.RIGHT_SIZE_TAG);
			bottomDataSendRequest = columnCommunicator.Isend(bottomArray, 0, bottomArray.length, MPI.BYTE, bottomRank, Tags.RIGHT_DATA_TAG);

			bottomSizeRecvRequest = columnCommunicator.Irecv(bottomSizeBuffer, 1, 1, MPI.INT, bottomRank, Tags.LEFT_SIZE_TAG);
		}

		//here cell starts to wait for data size from metacell
		Request metaSizeRecvRequest = columnCommunicator.Irecv(metaSizeBuffer, 1, 1, MPI.INT, 0, Tags.META_SIZE_TAG);


		Request bottomDataRecvRequest = null;

		//here we start to wait for data from bottom (it will send data to top first, so chance of receiving data from bottom is bigger)
		if (bottomSizeRecvRequest != null)
		{
			//we wait for receiving size
			bottomSizeRecvRequest.Wait();

			int bottomSize = bottomSizeBuffer[1];
			//enlarging buffer size if required
			bottomBuffer = bottomSize > bottomBuffer.length ? new byte[bottomSize] : bottomBuffer;

			//and start to wait for receiving data
			bottomDataRecvRequest = columnCommunicator.Irecv(bottomBuffer, 0, bottomBuffer.length, MPI.BYTE, bottomRank, Tags.LEFT_DATA_TAG);
		}


		Request topDataRecvRequest = null;

		//here we do mainly the same logic, but with other direction
		if (topSizeRecvRequest != null)
		{
			topSizeRecvRequest.Wait();

			int topSize = topSizeBuffer[1];
			topBuffer = topSize > topBuffer.length ? new byte[topSize] : topBuffer;

			topDataRecvRequest = columnCommunicator.Irecv(topBuffer, 0, topBuffer.length, MPI.BYTE, topRank, Tags.RIGHT_DATA_TAG);
		}


		//hrer waiting and receiving size and data from metacell using the same scheme
		metaSizeRecvRequest.Wait();

		int metaSize = metaSizeBuffer[1];
		metaBuffer = metaSize > metaBuffer.length ? new byte[metaSize] : metaBuffer;

		Request metaDataRecvRequest = columnCommunicator.Irecv(metaBuffer, 0, metaBuffer.length, MPI.BYTE, 0, Tags.META_DATA_TAG);

		//waiting for data from bottom...
		if (bottomDataRecvRequest != null)
		{
			bottomDataRecvRequest.Wait();

			//received data is interpreted as collection of agents and added to next generation
			serializer.deserialize(bottomBuffer).forEach(this::add);
		}

		//...top...
		if (topDataRecvRequest != null)
		{
			topDataRecvRequest.Wait();

			serializer.deserialize(topBuffer).forEach(this::add);
		}

		//...and sides
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
	}

	//sending data for output
	private void drawSync()
	{
		int top = (int)(bounds.getY() + bounds.getHeight());
		int bottom = (int)bounds.getY();
		int left = (int)bounds.getX();
		int right = (int)(bounds.getX() + bounds.getWidth());
		
		//the same model as in simple sync():
		//filter needed (those which are in cell)
		Set<Agent> agentsToDraw = nextGeneration.stream()
		                                   .filter(agent->agent.getX() >= left &&
		                                                  agent.getX() < right &&
		                                                  agent.getY() >= bottom &&
		                                                  agent.getY() < top)
		                                   .collect(Collectors.toSet());

		//serialize them
		byte[] drawBytes = serializer.serialize(agentsToDraw);
		int[] drawBytesSize = {drawBytes.length};

		//and send. outputting process always is 0 within output communicator
		outputCommunicator.Isend(drawBytesSize, 0, drawBytesSize.length, MPI.INT, 0, Tags.DRAW_SIZE_TAG);
		Request drawDataRequest = outputCommunicator.Isend(drawBytes, 0, drawBytes.length, MPI.BYTE, 0, Tags.DRAW_DATA_TAG);
		drawDataRequest.Wait();
	}

	//console output. can be useful while testing. methods below are submethods containing repeated parts of code
	@Override
	public String toString()
	{
		String border = border();

		return border +
		       toSection((int)(bounds.getY() + bounds.getHeight()) + Constants.DELTA, (int)(bounds.getY() + bounds.getHeight())) +
		       border +
		       toSection((int)(bounds.getY() + bounds.getHeight()), (int)bounds.getY()) +
		       border +
		       toSection((int)bounds.getY(), (int)bounds.getY() - Constants.DELTA) +
		       border +
		       "\n ";
	}

	private String border()
	{
		StringBuilder builder = new StringBuilder();

		for (int x = (int)bounds.getX() - Constants.DELTA - 2; x < (int)(bounds.getX() + bounds.getWidth()) + Constants.DELTA + 2; x++)
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

		int left = (int)bounds.getX();
		int right = (int)(bounds.getX() + bounds.getWidth());

		for (int y = topY - 1; y >= bottomY; y--)
		{
			builder.append(border)
			       .append(subLine(y, left - Constants.DELTA, left))
			       .append(border)
			       .append(subLine(y, left, right))
			       .append(border)
			       .append(subLine(y, right, right + Constants.DELTA))
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
