package ru.nsu.fit.djachenko.evolution;

import mpi.Group;
import mpi.Intracomm;
import mpi.MPI;

import java.io.IOException;

public class Tester
{
	public static void main(String[] args) throws IOException, InterruptedException
	{
		MPI.Init(args);

		int rank = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();

		int iter = 20;

		Intracomm groupCommunicator = MPI.COMM_WORLD.Split(rank / (Constants.GRID_HEIGHT + 1), rank);//create communicator for every vertical group

		if (rank % (Constants.GRID_HEIGHT + 1) == 0)
		{
			Group globalGroup = MPI.COMM_WORLD.Group();

			int[] metaCells = new int[Constants.GRID_WIDTH];

			for (int i = 0; i < metaCells.length; i++)
			{
				metaCells[i] = i * (Constants.GRID_HEIGHT + 1);
			}

			Group metaGroup = globalGroup.Incl(metaCells);

			Intracomm metaCommunicator = MPI.COMM_WORLD.Create(metaGroup);

			MetaCell stub = new MetaCell(metaCommunicator, groupCommunicator);

			for (int process = 0; process < size; process++)
			{
				MPI.COMM_WORLD.Barrier();
			}

			for (int i = 0; i < iter; i++)
			{
				System.out.println("prerun");
				stub.run();
				System.out.println("postrun");

				for (int process = 0; process < size; process++)
				{
					MPI.COMM_WORLD.Barrier();
				}
			}

			metaCommunicator.Free();
		}
		else
		{
			Cell cell = new Cell(groupCommunicator, rank / (Constants.GRID_HEIGHT + 1));

			for (int process = 0; process < size; process++)
			{
				MPI.COMM_WORLD.Barrier();

				if (process == rank)
				{
					System.out.println(cell);
				}
			}

			for (int i = 0; i < iter; i++)
			{
				cell.run();

				for (int process = size - 1; process >= 0; process--)
				{
					MPI.COMM_WORLD.Barrier();

					if (process == rank)
					{
						System.out.println(cell);
					}
				}
			}
		}

		//System.out.println("final");

		MPI.Finalize();
	}
}
