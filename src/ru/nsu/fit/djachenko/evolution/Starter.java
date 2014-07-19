package ru.nsu.fit.djachenko.evolution;

import mpi.*;

public class Starter
{
	public static void main(String[] args)
	{
		MPI.Init(args);

		int rank = MPI.COMM_WORLD.Rank();

		int gridWidth = 4;
		int gridHeight = 4;

		Intracomm verticalGroupCommunicator = MPI.COMM_WORLD.Split(rank % gridWidth, rank);//create communicator for every vertical group

		if (rank % (gridHeight + 1) == 0)
		{
			Group globalGroup = MPI.COMM_WORLD.Group();

			int[] metaCells = new int[gridWidth];

			for (int i = 0; i < gridWidth; i++)
			{
				metaCells[i] = i * (gridHeight + 1);
			}

			Group metaGroup = globalGroup.Incl(metaCells);

			Intracomm metaCommuniator = MPI.COMM_WORLD.Create(metaGroup);

			metaCommuniator.Free();

			MetaCell cell = new MetaCell(metaCommuniator, verticalGroupCommunicator);
		}
		else
		{
			Cell cell = new Cell(verticalGroupCommunicator, 0);
		}

		verticalGroupCommunicator.Free();

		MPI.Finalize();
	}
}
