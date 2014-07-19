#include <iostream>
#include <mpi.h>

using namespace std;

int main(int argc, char **argv)
{
	MPI_Init(&argc, &argv);

	int rank;
	int size;

	MPI_Comm_rank(MPI_COMM_WORLD, &rank);
	MPI_Comm_size(MPI_COMM_WORLD, &size);

	printf("hello from %d:%d\n", rank, size);

	MPI_Finalize();

	return 0;
}
