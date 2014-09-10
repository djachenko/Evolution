package ru.nsu.fit.djachenko.evolution;

import mpi.Group;
import mpi.Intracomm;
import mpi.MPI;
import mpi.Request;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MetaCell implements Runnable
{
	//sets for agents which have to be sent to left and to right
	private final Set<Agent> leftEdge = new HashSet<>();
	private final Set<Agent> rightEdge = new HashSet<>();

	//arrays for storing service data (we have to receive and send data to all cells, and we have to store multiple requests)
	private final Request[] cellSizeRecvRequests;
	private final int[][] cellSizeBuffers;

	private final Request[] cellDataRecvRequests;
	private final byte[][] cellDataBuffers;

	//buffers for sending and receivig sizes of transmitted data.
	//one cell for received data size and one for sent data size
	private final int[] leftSizeBuffer = new int[2];
	private final int[] rightSizeBuffer = new int[2];

	//buffers for receiving data. if their size will become too small, they will be enlarged
	private byte[] leftDataRecvBuffer = new byte[1024];
	private byte[] rightDataRecvBuffer = new byte[1024];

	private final Set<Agent> sendSet = new HashSet<>();

	//horizontal bounds
	private final int left;
	private final int right;

	//rank of left cell relatively to current
	private final int leftRank;
	//rank of right cell
	private final int rightRank;

	//communicator whic unites all metacells
	private final Intracomm metaCommunicator;
	//communicator for column of this metacell
	private final Intracomm columnCommunicator;

	//number of meta cells
	private final int metaSize;

	//number of cells in column (including metacell)
	private final int groupSize;

	//height of one cell in column
	private final int cellHeight;

	private final int[] cellHorisontalBounds;
	private final int[][] verticalStats;

	private final Serializer serializer = new Serializer();

	MetaCell(Intracomm columnCommunicator)
	{
		//creating meta communicator
		Group globalGroup = MPI.COMM_WORLD.Group();

		int[] metaCells = new int[Constants.GRID_WIDTH];

		//counting subset of metacells
		for (int i = 0; i < metaCells.length; i++)
		{
			metaCells[i] = i * (Constants.GRID_HEIGHT + 1);
		}

		//creating of subgroup of group of all processes
		Group metaGroup = globalGroup.Incl(metaCells);

		//creating communicator
		metaCommunicator = MPI.COMM_WORLD.Create(metaGroup);

		this.columnCommunicator = columnCommunicator;

		int metaRank = metaCommunicator.Rank();
		this.metaSize = metaCommunicator.Size();

	    this.groupSize = columnCommunicator.Size();

		int columnWidth = (int) Math.ceil(1.0 * Constants.WIDTH / Constants.GRID_WIDTH);

		this.left = metaRank * columnWidth;
		this.right = (metaRank + 1) * columnWidth;

		this.leftRank = metaRank - 1;
		this.rightRank = metaRank + 1;

		this.cellHeight = (int) Math.ceil(1.0 * Constants.HEIGHT / Constants.GRID_HEIGHT);

		cellHorisontalBounds = new int[groupSize];

		cellHorisontalBounds[0] = 0;

		for (int i = 1; i < cellHorisontalBounds.length; i++)
		{
			cellHorisontalBounds[i] = cellHorisontalBounds[i - 1] + cellHeight;
		}

		this.verticalStats = new int[2][Constants.HEIGHT];

		cellSizeRecvRequests = new Request[groupSize];
		cellSizeBuffers = new int[groupSize][2];
		cellDataRecvRequests = new Request[groupSize];
		cellDataBuffers = new byte[groupSize][1];
	}

	//iteration method. this cell just synchronizes
	@Override
	public void run()
	{
		sync();
	}

	private void sync()
	{
		//request can be not initialize (e.g. this is the left or right side)
		Request leftSizeRecvRequest = null;
		Request rightSizeRecvRequest = null;

		//"if left rank is valid"
		if (leftRank < metaSize && leftRank >= 0)
		{
			//start to wait for receiving size of data from left side
			leftSizeRecvRequest = metaCommunicator.Irecv(leftSizeBuffer, 1, 1, MPI.INT, leftRank, Tags.RIGHT_SIZE_TAG);
		}

		if (rightRank >= 0 && rightRank < metaSize)
		{
			rightSizeRecvRequest = metaCommunicator.Irecv(rightSizeBuffer, 1, 1, MPI.INT, rightRank, Tags.LEFT_SIZE_TAG);
		}

		for (int i = 1; i < groupSize; i++)
		{
			//start to wait for receiving data sizes from every simple cell
			cellSizeRecvRequests[i] = columnCommunicator.Irecv(cellSizeBuffers[i], 1, 1, MPI.INT, i, Tags.META_SIZE_TAG);
		}

		for (int i = 1; i < groupSize; i++)
		{
			//wait for receiving data sizes from every simple cell
			cellSizeRecvRequests[i].Wait();

			//enlarge buffers if required
			if (cellDataBuffers[i].length < cellSizeBuffers[i][1])
			{
				cellDataBuffers[i] = new byte[cellSizeBuffers[i][1]];
			}

			//start waiting for data
			cellDataRecvRequests[i] = columnCommunicator.Irecv(cellDataBuffers[i], 0, cellDataBuffers[i].length, MPI.BYTE, i, Tags.META_DATA_TAG);
		}

		//prepare place for received data
		leftEdge.clear();
		rightEdge.clear();

		for (int i = 1; i < groupSize; i++)
		{
			/*
			receive data and put them into corresponding set, filtering them
			*/
			cellDataRecvRequests[i].Wait();

			Set<Agent> agents = serializer.deserialize(cellDataBuffers[i]);

			leftEdge.addAll(agents.stream()
			                      .filter(agent -> agent.getX() < left + Constants.DELTA)
			                      .collect(Collectors.toSet()));

			rightEdge.addAll(agents.stream()
			                       .filter(agent -> agent.getX() >= right - Constants.DELTA)
			                       .collect(Collectors.toSet()));
		}

		//forward received data to left side
		if (leftRank < metaSize && leftRank >= 0)
		{
			byte[] leftDataSendBuffer = serializer.serialize(leftEdge);

			leftSizeBuffer[0] = leftDataSendBuffer.length;

			//size first
			metaCommunicator.Isend(leftSizeBuffer, 0, 1, MPI.INT, leftRank, Tags.LEFT_SIZE_TAG);
			//data second
			metaCommunicator.Isend(leftDataSendBuffer, 0, leftDataSendBuffer.length, MPI.BYTE, leftRank, Tags.LEFT_DATA_TAG);
		}

		//to right side
		if (rightRank >= 0 && rightRank < metaSize)
		{
			byte[] rightDataSendBuffer = serializer.serialize(rightEdge);

			rightSizeBuffer[0] = rightDataSendBuffer.length;

			metaCommunicator.Isend(rightSizeBuffer, 0, 1, MPI.INT, rightRank, Tags.RIGHT_SIZE_TAG);
			metaCommunicator.Isend(rightDataSendBuffer, 0, rightDataSendBuffer.length, MPI.BYTE, rightRank, Tags.RIGHT_DATA_TAG);
		}

		Request leftDataRecvRequest = null;

		if (leftSizeRecvRequest != null)
		{
			//wait for size of data from left side
			leftSizeRecvRequest.Wait();

			//enlarge buffer
			if (leftDataRecvBuffer.length < leftSizeBuffer[1])
			{
				leftDataRecvBuffer = new byte[leftSizeBuffer[1]];
			}

			//and stsrt receiving data
			leftDataRecvRequest = metaCommunicator.Irecv(leftDataRecvBuffer, 0, leftDataRecvBuffer.length, MPI.BYTE, leftRank, Tags.RIGHT_DATA_TAG);
		}

		Request rightDataRecvRequest = null;

		if (rightSizeRecvRequest != null)
		{
			rightSizeRecvRequest.Wait();

			if (rightDataRecvBuffer.length < rightSizeBuffer[1])
			{
				rightDataRecvBuffer = new byte[rightSizeBuffer[1]];
			}

			rightDataRecvRequest = metaCommunicator.Irecv(rightDataRecvBuffer, 0, rightDataRecvBuffer.length, MPI.BYTE, rightRank, Tags.LEFT_DATA_TAG);
		}

		sendSet.clear();

		if (leftDataRecvRequest != null)
		{
			//wait for data from left side
			leftDataRecvRequest.Wait();

			//and add it to set of all received agents
			sendSet.addAll(serializer.deserialize(leftDataRecvBuffer));
		}

		//the same with right side
		if (rightDataRecvRequest != null)
		{
			rightDataRecvRequest.Wait();

			sendSet.addAll(serializer.deserialize(rightDataRecvBuffer));
		}

		for (int i = 1; i < groupSize; i++)
		{
			int index = i - 1;

			/*
			* there is some complex logic: here agents are filtered by height. every cell receives agents, which are between its vertical bounds
			* problem is that we also should send there agents which are not between cell bounds, but are seen from thi cell
			* they are in top and bottom edges, and they are nearer than DELTA to left or right edges of upper/lower cell.
			* so we have to use such complex predicate
			* */
			Set<Agent> agents = sendSet.stream()
			                            .filter(agent -> {
				                            int x = agent.getX();
				                            int y = agent.getY();

				                            return (y >= cellHeight * index &&
				                                    y < cellHeight * (index + 1)) ||
				                                   (x >= left &&
				                                    x < right &&
				                                    y >= cellHeight * index - Constants.DELTA &&
				                                    y < cellHeight * (index + 1) + Constants.DELTA);
			                            })
			                            .collect(Collectors.toSet());

			//once data is filtered, we can send it to destination
			byte[] cellDataSendBuffer = serializer.serialize(agents);

			cellSizeBuffers[i][0] = cellDataSendBuffer.length;

			columnCommunicator.Isend(cellSizeBuffers[i], 0, 1, MPI.INT, i, Tags.META_SIZE_TAG);
			columnCommunicator.Isend(cellDataSendBuffer, 0, cellDataSendBuffer.length, MPI.BYTE, i, Tags.META_DATA_TAG);
		}
	}

	private void balance()
	{
		//clear bufers from previous values
		for (int i = 0; i < verticalStats[0].length; i++)
		{
			verticalStats[0][i] = 0;
			verticalStats[1][i] = 0;
		}

		//receive statistics data. two buffers are used for preventing data interleaving
		//because we have to send data both from edges and main cell parts
		//and, for example, top edge of bottom cell and main part of current may be recieved int the same array cells
		for (int i = 1; i < groupSize; i++)
		{
			cellSizeRecvRequests[i] = columnCommunicator.Irecv(verticalStats[i % 2], cellHorisontalBounds[i - 1], cellHorisontalBounds[i] - cellHorisontalBounds[i - 1], MPI.INT, i, Tags.BALANCE_HEIGHT_DATA_TAG);
		}

		for (int i = 1; i < groupSize; i++)
		{
			cellSizeRecvRequests[i].Wait();
		}

		int sum = 0;

		//then two buffers are merged
		for (int i = 0; i < verticalStats[0].length; i++)
		{
			verticalStats[0][i] += verticalStats[1][i];

			//and sum is counted
			//it will be used for counting agent distribution
			sum += verticalStats[0][i];
		}

		int[] statistics = verticalStats[0];

		//average number of agents in every cell.
		//it can be not be integer, it will be approximated later
		final double average = (double)sum / (groupSize - 1);

		double cellSum = 0;

		int index = 1;

		for (int y = 0; y < statistics.length; y++)
		{
			//counting number of agents in cell for future comparing with average
			cellSum += statistics[y];

			if (y > cellHorisontalBounds[index] - Constants.DELTA &&
			    Math.abs(cellSum - average) < average / 2 ||
			    y == cellHorisontalBounds[index] + Constants.DELTA)
			{
				double nextSum = cellSum + y == statistics.length - 1 ? 0 : statistics[y + 1];

				if (Math.abs(cellSum - average) < Math.abs(nextSum - average))
				{
					cellHorisontalBounds[index] = y;

					cellSum -= average;
					index++;
				}
			}
		}

		for (int i = 1; i < cellHorisontalBounds.length; i++)
		{
			columnCommunicator.Isend(cellHorisontalBounds, i - 1, 2, MPI.INT, i, Tags.BALANCE_DIMENSIONS_TAG);
		}
	}
}
