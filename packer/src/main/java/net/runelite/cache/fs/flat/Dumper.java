/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.fs.flat;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.InterfaceManager;
import net.runelite.cache.InventoryManager;
import net.runelite.cache.ItemManager;
import net.runelite.cache.NpcManager;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.OverlayManager;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.UnderlayManager;
import net.runelite.cache.definitions.InventoryDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.OverlayDefinition;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.UnderlayDefinition;
import net.runelite.cache.definitions.loaders.EnumLoader;
import net.runelite.cache.definitions.loaders.KitLoader;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.definitions.loaders.VarbitLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.script.disassembler.Disassembler;

public enum Dumper
{
	UNDERLAYS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				UnderlayManager um = new UnderlayManager(store);
				um.load();
				for (UnderlayDefinition ud : um.getUnderlays())
				{
					writeFile(output, ud.getId(), ud);
				}
			}
		},
	KITS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				writeConfig(store, output, ConfigType.IDENTKIT, new KitLoader()::load);
			}
		},
	OVERLAYS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				OverlayManager om = new OverlayManager(store);
				om.load();
				for (OverlayDefinition od : om.getOverlays())
				{
					writeFile(output, od.getId(), od);
				}
			}
		},
	INVENTORIES
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				InventoryManager im = new InventoryManager(store);
				im.load();
				for (InventoryDefinition id : im.getInventories())
				{
					writeFile(output, id.id, id);
				}
			}
		},
	OBJECT_DEFS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				ObjectManager m = new ObjectManager(store);
				m.load();
				m.dump(output);
			}
		},
	ENUMS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				writeConfig(store, output, ConfigType.ENUM, new EnumLoader()::load);
			}
		},
	NPC_DEFS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				NpcManager m = new NpcManager(store);
				m.load();
				m.dump(output);
			}
		},
	ITEM_DEFS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				ItemManager m = new ItemManager(store);
				m.load();
				m.export(output);
			}
		},
	SEQUENCES
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				writeConfig(store, output, ConfigType.SEQUENCE, new SequenceLoader()::load);
			}
		},
	VAR_BITS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				writeConfig(store, output, ConfigType.VARBIT, new VarbitLoader()::load);
			}
		},
	INTERFACE_DEFS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				InterfaceManager m = new InterfaceManager(store);
				m.load();
				m.export(output);
			}
		},
	MODELS_RAW
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				Index i = store.getIndex(IndexType.MODELS);
				for (Archive a : i.getArchives())
				{
					byte[] cad = store.getStorage().loadArchive(a);
					byte[] data = a.decompress(cad);
					writeFile(output, a.getArchiveId() + ".model", data);
				}
			}
		},
	MODELS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				ModelLoader l = new ModelLoader();

				Index i = store.getIndex(IndexType.MODELS);
				for (Archive a : i.getArchives())
				{
					byte[] cad = store.getStorage().loadArchive(a);
					byte[] data = a.decompress(cad);
					ModelDefinition d = l.load(a.getArchiveId(), data);
					writeFile(output, a.getArchiveId(), d);
				}
			}
		},
	SPRITES
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				SpriteManager sm = new SpriteManager(store);
				sm.load();
				sm.export(output);
			}
		},
	TEXTURE_DEFS
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				TextureManager tm = new TextureManager(store);
				tm.load();

				for (TextureDefinition td : tm.getTextures())
				{
					writeFile(output, td.getId(), td);
				}
			}
		},
	RS2ASM
		{
			@Override
			public void dump(Store store, File output) throws Exception
			{
				ScriptLoader sl = new ScriptLoader();
				Disassembler ds = new Disassembler();

				Index i = store.getIndex(IndexType.CLIENTSCRIPT);
				for (Archive a : i.getArchives())
				{
					byte[] cab = store.getStorage().loadArchive(a);
					byte[] data = a.decompress(cab);

					String shasum = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(data).asBytes());
					writeFile(output, a.getArchiveId() + ".hash", shasum.getBytes());

					ScriptDefinition sd = sl.load(a.getArchiveId(), data);
					String disasm = ds.disassemble(sd);
					writeFile(output, a.getArchiveId() + ".rs2asm", disasm.getBytes());
				}
			}
		};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static void writeFile(File dir, String name, byte[] data) throws IOException
	{
		Files.write(new File(dir, name).toPath(), data);
	}

	private static void writeFile(File dir, int name, Object data) throws IOException
	{
		writeFile(dir, name + ".json", GSON.toJson(data).getBytes());
	}

	@FunctionalInterface
	private interface LoadFunction<D>
	{
		D load(int id, byte[] data);
	}

	public static <D> void writeConfig(Store store, File output, ConfigType config, LoadFunction<D> load) throws IOException
	{
		Index i = store.getIndex(IndexType.CONFIGS);
		Archive a = i.getArchive(config.getId());
		byte[] cad = store.getStorage().loadArchive(a);
		ArchiveFiles fs = a.getFiles(cad);

		for (FSFile f : fs.getFiles())
		{
			D d = load.load(f.getFileId(), f.getContents());
			writeFile(output, f.getFileId(), d);
		}
	}

	abstract public void dump(Store store, File output) throws Exception;
}
