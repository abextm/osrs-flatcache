package net.runelite.cache.fs.flat;

import net.runelite.cache.io.InputStream;

public class VarPlayer
{
	public int id;
	public int configType;

	void decode(InputStream var1)
	{
		while (true)
		{
			int var2 = var1.readUnsignedByte();
			if (var2 == 0)
			{
				return;
			}

			this.decode(var1, var2);
		}
	}

	void decode(InputStream var1, int var2)
	{
		if (var2 == 5)
		{
			this.configType = var1.readUnsignedShort();
		}
	}
}
