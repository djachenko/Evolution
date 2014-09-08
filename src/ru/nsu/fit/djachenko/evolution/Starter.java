package ru.nsu.fit.djachenko.evolution;

import mpi.Intracomm;
import mpi.MPI;

import java.io.IOException;

//enter point class
public class Starter
{
	public static void main(String[] args) throws IOException, InterruptedException
	{
		MPI.Init(args);

		int rank = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();

		int required = (Constants.GRID_HEIGHT + 1) * Constants.GRID_WIDTH + 1;

		if (size != required)
		{
			if (rank == 0)
			{
				System.out.println("Wrong number of processes, " + required + " instead of " + size + " required");
			}

			MPI.COMM_WORLD.Barrier();

			System.exit(1);
		}

		int iter = 200;

		Intracomm columnCommunicator = MPI.COMM_WORLD.Split(rank / (Constants.GRID_HEIGHT + 1), rank);//create communicator for every vertical group

		Intracomm outputCommunicator = Drawer.intracomm();

		Runnable runnable;

		if (rank == size - 1)
		{
			runnable = new Drawer(outputCommunicator);
		}
		else if (rank % (Constants.GRID_HEIGHT + 1) == 0)
		{
			runnable = new MetaCell(columnCommunicator);
		}
		else
		{
			runnable = new Cell(columnCommunicator, outputCommunicator, rank / (Constants.GRID_HEIGHT + 1));
		}

		for (int i = 0; i < iter; i++)
		{
			runnable.run();

			Thread.sleep(Constants.DELAY_MILLIS);
		}

		outputCommunicator.Free();

		MPI.Finalize();

		System.exit(0);
	}
}
