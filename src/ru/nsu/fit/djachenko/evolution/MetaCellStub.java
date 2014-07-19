package ru.nsu.fit.djachenko.evolution;

import mpi.Intracomm;
import mpi.MPI;
import mpi.Request;

import java.util.HashSet;

public class MetaCellStub implements Runnable
{
	private final Request[] cellSizeRecvRequests;
	private final int[][] cellSizeBuffers;

	private final Request[] cellDataRecvRequests;
	private final byte[][] cellDataBuffers;

	private final Intracomm groupCommunicator;

	private final int groupSize;

	private final Serializer serializer = new Serializer();

	MetaCellStub(Intracomm groupCommunicator)
	{
		this.groupCommunicator = groupCommunicator;

		this.groupSize = groupCommunicator.Size();

		cellSizeRecvRequests = new Request[groupSize];
		cellSizeBuffers = new int[groupSize][2];

		cellDataRecvRequests = new Request[groupSize];
		cellDataBuffers = new byte[groupSize][2];
	}

	@Override
	public void run()
	{
		for (int i = 0; i < Constants.ITERATION_COUNT; i++)
		{
			sync();
		}
	}

	private void sync()
	{
		for (int i = 1; i < groupSize; i++)
		{
			cellSizeRecvRequests[i] = groupCommunicator.Irecv(cellSizeBuffers[i], 1, 1, MPI.INT, i, Tags.META_SIZE_TAG);
		}

		for (int i = 1; i < groupSize; i++)
		{
			cellSizeRecvRequests[i].Wait();

			if (cellDataBuffers[i].length < cellSizeBuffers[i][1])
			{
				cellDataBuffers[i] = new byte[cellSizeBuffers[i][1]];
			}

			cellDataRecvRequests[i] = groupCommunicator.Irecv(cellDataBuffers[i], 0, cellDataBuffers[i].length, MPI.BYTE, i, Tags.META_DATA_TAG);
		}

		byte[] cellDataSendBuffer = serializer.serialize(new HashSet<>());

		for (int i = 1; i < groupSize; i++)
		{
			cellSizeBuffers[i][0] = cellDataSendBuffer.length;

			groupCommunicator.Isend(cellSizeBuffers[i], 0, 1, MPI.INT, i, Tags.META_SIZE_TAG);
			groupCommunicator.Isend(cellDataSendBuffer, 0, cellDataSendBuffer.length, MPI.BYTE, i, Tags.META_DATA_TAG);
		}

		for (int i = 1; i < groupSize; i++)
		{
			cellDataRecvRequests[i].Wait();
		}

		groupCommunicator.Barrier();
	}
}
