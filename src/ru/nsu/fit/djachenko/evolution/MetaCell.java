package ru.nsu.fit.djachenko.evolution;

import mpi.Intracomm;
import mpi.MPI;
import mpi.Request;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MetaCell implements Runnable
{
	private final Set<Agent> leftEdge = new HashSet<>();
	private final Set<Agent> rightEdge = new HashSet<>();

	private final Request[] cellSizeRecvRequests;
	private final int[][] cellSizeBuffers;

	private final Request[] cellDataRecvRequests;
	private final byte[][] cellDataBuffers;

	private final int[] leftSizeBuffer = new int[2];
	private final int[] rightSizeBuffer = new int[2];

	private byte[] leftDataRecvBuffer = new byte[1024];
	private byte[] rightDataRecvBuffer = new byte[1024];

	private final Set<Agent> sendSet = new HashSet<>();

	private final int left;
	private final int right;

	private final int leftRank;
	private final int rightRank;

	private final Intracomm metaCommunicator;
	private final Intracomm groupCommunicator;

	private final int metaSize;

	private final int groupSize;

	private final int cellHeight;

	private final Serializer serializer = new Serializer();

	MetaCell(Intracomm metaCommunicator, Intracomm groupCommunicator)
	{
		this.metaCommunicator = metaCommunicator;
		this.groupCommunicator = groupCommunicator;

		int metaRank = metaCommunicator.Rank();
		this.metaSize = metaCommunicator.Size();

	    this.groupSize = groupCommunicator.Size();

		int columnWidth = (int) Math.ceil(1.0 * Constants.WIDTH / Constants.GRID_WIDTH);

		this.left = metaRank * columnWidth;
		this.right = (metaRank + 1) * columnWidth;

		this.leftRank = metaRank - 1;
		this.rightRank = metaRank + 1;

		this.cellHeight = (int) Math.ceil(1.0 * Constants.HEIGHT / Constants.GRID_HEIGHT);

		cellSizeRecvRequests = new Request[groupSize];
		cellSizeBuffers = new int[groupSize][2];
		cellDataRecvRequests = new Request[groupSize];
		cellDataBuffers = new byte[groupSize][1];
	}

	@Override
	public void run()
	{
		sync();
	}

	private void sync()
	{
		Request leftSizeRecvRequest = null;
		Request rightSizeRecvRequest = null;

		if (leftRank < metaSize && leftRank >= 0)
		{
			leftSizeRecvRequest = metaCommunicator.Irecv(leftSizeBuffer, 1, 1, MPI.INT, leftRank, Tags.RIGHT_SIZE_TAG);
		}

		if (rightRank >= 0 && rightRank < metaSize)
		{
			rightSizeRecvRequest = metaCommunicator.Irecv(rightSizeBuffer, 1, 1, MPI.INT, rightRank, Tags.LEFT_SIZE_TAG);
		}

		for (int i = 1; i < groupSize; i++)
		{
			cellSizeRecvRequests[i] = groupCommunicator.Irecv(cellSizeBuffers[i], 1, 1, MPI.INT, i, Tags.META_SIZE_TAG);
		}

		System.out.println("precells");

		for (int i = 1; i < groupSize; i++)
		{
			cellSizeRecvRequests[i].Wait();

			if (cellDataBuffers[i].length < cellSizeBuffers[i][1])
			{
				cellDataBuffers[i] = new byte[cellSizeBuffers[i][1]];
			}

			System.out.println("cellDataBuffers[i].length = " + cellDataBuffers[i].length + " " + metaCommunicator.Rank() + " " + i);

			cellDataRecvRequests[i] = groupCommunicator.Irecv(cellDataBuffers[i], 0, cellDataBuffers[i].length, MPI.BYTE, i, Tags.META_DATA_TAG);
		}

		System.out.println("preedges");

		leftEdge.clear();
		rightEdge.clear();

		for (int i = 1; i < groupSize; i++)
		{
			cellDataRecvRequests[i].Wait();

			Set<Agent> agents = serializer.deserialize(cellDataBuffers[i]);

			leftEdge.addAll(agents.stream()
			                      .filter(agent -> agent.getX() < left + Constants.DELTA)
			                      .collect(Collectors.toSet()));

			rightEdge.addAll(agents.stream()
			                       .filter(agent -> agent.getX() >= right - Constants.DELTA)
			                       .collect(Collectors.toSet()));
		}

		for (int i = 0; i < metaSize; i++)
		{
			if (i == metaCommunicator.Rank())
			{
				System.out.println("left " + i);

				leftEdge.forEach(System.out::println);

				System.out.println("right " + i);

				rightEdge.forEach(System.out::println);
			}

			metaCommunicator.Barrier();
		}

		if (leftRank < metaSize && leftRank >= 0)
		{
			byte[] leftDataSendBuffer = serializer.serialize(leftEdge);

			leftSizeBuffer[0] = leftDataSendBuffer.length;

			metaCommunicator.Isend(leftSizeBuffer, 0, 1, MPI.INT, leftRank, Tags.LEFT_SIZE_TAG);
			metaCommunicator.Isend(leftDataSendBuffer, 0, leftDataSendBuffer.length, MPI.BYTE, leftRank, Tags.LEFT_DATA_TAG);
		}

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
			leftSizeRecvRequest.Wait();

			if (leftDataRecvBuffer.length < leftSizeBuffer[1])
			{
				leftDataRecvBuffer = new byte[leftSizeBuffer[1]];
			}

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
			leftDataRecvRequest.Wait();

			sendSet.addAll(serializer.deserialize(leftDataRecvBuffer));
		}

		if (rightDataRecvRequest != null)
		{
			rightDataRecvRequest.Wait();

			sendSet.addAll(serializer.deserialize(rightDataRecvBuffer));
		}

		for (int i = 1; i < groupSize; i++)
		{
			int index = i - 1;

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

			for (int j = 0; j < metaSize; j++)
			{
				if (j == metaCommunicator.Rank())
				{
					System.out.println("sending from " + j + " to " + i);

					agents.forEach(System.out::println);
				}

				metaCommunicator.Barrier();
			}

			byte[] cellDataSendBuffer = serializer.serialize(agents);

			cellSizeBuffers[i][0] = cellDataSendBuffer.length;

			groupCommunicator.Isend(cellSizeBuffers[i], 0, 1, MPI.INT, i, Tags.META_SIZE_TAG);
			groupCommunicator.Isend(cellDataSendBuffer, 0, cellDataSendBuffer.length, MPI.BYTE, i, Tags.META_DATA_TAG);
		}

		groupCommunicator.Barrier();
	}
}
