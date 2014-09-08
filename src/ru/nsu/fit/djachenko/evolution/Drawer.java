package ru.nsu.fit.djachenko.evolution;

import mpi.Group;
import mpi.Intracomm;
import mpi.MPI;
import mpi.Request;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//class for performing graphics output. receives data from cells and allows GUI to read them
public class Drawer implements Runnable
{
	private int[][] currentSpace = new int[Constants.HEIGHT][Constants.WIDTH];
	private int[][] nextSpace = new int[Constants.HEIGHT][Constants.WIDTH];

	private final Intracomm intracomm;

	Drawer(Intracomm intracomm)
	{
		//intracomm for output communication. this process always 0 rank within it
		this.intracomm = intracomm;

		//here window is being created and shown
		Window window = new Window(this);
		window.setVisible(true);
	}

	//iteration method
	@Override
	public void run()
	{
		ExecutorService service = Executors.newCachedThreadPool();

		//here two display models are used. one is already set, and this can't be changed, and other, which is being counted
		//before counting next model has to be cleared (color will be set to white)
		for (int[] row : nextSpace)
		{
			for (int x = 0; x < row.length; x++)
			{
				row[x] = 254;
			}
		}

		List<Future<?>> futures = new LinkedList<>();

		//for every cell process new asynchronous task is being started here, because every piece of data can be received independently.
		//the only synchronized part is counting display model
		for (int i = 1; i < intracomm.Size(); i++)
		{
			//compilator requires constant value for acessing from async task
			final int finalI = i;

			Serializer serializer = new Serializer();

			futures.add(service.submit(()->{
				int[] recvSize = new int[1];

				//receiving data the same way as in Cell.sync()
				Request sizeRequest = intracomm.Irecv(recvSize, 0, 1, MPI.INT, finalI, Tags.DRAW_SIZE_TAG);
				sizeRequest.Wait();

				byte[] buffer = new byte[recvSize[0]];

				Request dataRequest = intracomm.Irecv(buffer, 0, buffer.length, MPI.BYTE, finalI, Tags.DRAW_DATA_TAG);
				dataRequest.Wait();

				Set<Agent> agents = serializer.deserialize(buffer);

				//while setting its piece, display model is locked by this task
				synchronized (nextSpace)
				{
					for (Agent agent : agents)
					{
						//coordinates should be changed. in model axes zero point is in bottom-left corner, but Swing has zero point in top-left corner
						int x = agent.getX();
						int y = Constants.HEIGHT - agent.getY() - 1;

						nextSpace[y][x] = agent.getFenotype();
					}
				}
			}));
		}

		//waiting for all parts to be fixes
		for (Future<?> future : futures)
		{
			try
			{
				future.get();
			}
			catch (InterruptedException | ExecutionException e)
			{
				e.printStackTrace();
			}
		}

		//and then changing models. now new model will be drawn and old will be reused
		int[][] temp = currentSpace;

		synchronized (this)
		{
			currentSpace = nextSpace;
		}

		nextSpace = temp;
	}

	public int[][] getCurrentSpace()
	{
		return currentSpace;
	}

	static Intracomm intracomm()
	{
		int size = MPI.COMM_WORLD.Size();

		Group globalGroup = MPI.COMM_WORLD.Group();

		int[] simpleCells = new int[Constants.GRID_WIDTH * Constants.GRID_HEIGHT + 1];
		simpleCells[0] = size - 1;

		int index = 1;

		for (int i = 0; i < size - 1; i++)
		{
			if (i % (Constants.GRID_HEIGHT + 1) != 0)
			{
				simpleCells[index] = i;

				index++;
			}
		}

		Group drawGroup = globalGroup.Incl(simpleCells);

		System.out.println("postgroup " + Arrays.toString(simpleCells));

		return MPI.COMM_WORLD.Create(drawGroup);
	}
}
