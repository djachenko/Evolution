package ru.nsu.fit.djachenko.evolution;

import java.io.*;
import java.util.Collections;
import java.util.Set;

//class for serializing collections of agents to byte array and backwards
public class Serializer
{
	private final ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();

	byte[] serialize(Set<Agent> o)
	{
		byteOutStream.reset();

		try (ObjectOutputStream stream = new ObjectOutputStream(byteOutStream))
		{
			stream.writeObject(o);
			stream.flush();

			return byteOutStream.toByteArray();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return null;
	}

	Set<Agent> deserialize(byte[] buffer)
	{
		try
		{
			return (Set<Agent>) new ObjectInputStream(new ByteArrayInputStream(buffer)).readObject();
		}
		catch (IOException | ClassNotFoundException e)
		{
			e.printStackTrace();
		}

		return Collections.emptySet();
	}
}
