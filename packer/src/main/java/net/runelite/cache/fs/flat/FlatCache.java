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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.cache.client.CacheClient;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.jagex.DiskStorage;
import net.runelite.protocol.api.login.HandshakeResponseType;

public class FlatCache
{
	private FlatCache()
	{
	}

	private static void printUsage()
	{
		System.err.println("download [old id] [flat cache directory]");
		System.err.println("pack [jagex cache directory] [flat cache directory]");
		System.err.println("unpack [flat cache directory] [jagex cache directory]");
		System.err.println("dump [type[,type]] [flat cache directory] [output directory or 7z]");
		System.err.println("dump types: all," + Stream.of(Dumper.values()).map(Enum::name).map(String::toLowerCase).collect(Collectors.joining(", ")));
		System.exit(1);
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			printUsage();
		}

		switch (args[0])
		{
			case "help":
				printUsage();
				return;
			case "pack":
			{
				if (args.length != 3)
				{
					break;
				}

				try (Store store = new Store(new File(args[1])))
				{
					store.load();

					File od = new File(args[2]);
					od.mkdirs();

					FlatStorage fs = new FlatStorage(od);
					try (Store fss = new Store(fs))
					{
						copyStore(fss, store);
					}
				}
				return;
			}
			case "unpack":
			{
				if (args.length != 3)
				{
					break;
				}

				FlatStorage fs = new FlatStorage(new File(args[1]));
				try (Store store = new Store(fs))
				{
					store.load();

					File od = new File(args[2]);
					od.mkdirs();

					DiskStorage ds = new DiskStorage(od);
					try (Store fss = new Store(ds))
					{
						copyStore(fss, store);
					}
				}

				return;
			}
			case "download":
			{
				if (args.length != 3)
				{
					break;
				}

				int rev = Integer.parseInt(args[1]);

				File od = new File(args[2]);
				od.mkdirs();

				FlatStorage fs = new FlatStorage(od);
				try (Store fss = new Store(fs))
				{
					fss.load();

					CacheClient ccli = null;
					try
					{
						for (int i = 0; ; i++)
						{
							if (i >= 5)
							{
								System.err.println("Unable to guess revision after " + i + " tries");
								System.exit(1);
								return;
							}

							if (ccli != null)
							{
								ccli.close();
							}
							ccli = new CacheClient(fss, rev);
							ccli.connect();
							HandshakeResponseType res = ccli.handshake().get();

							if (res == HandshakeResponseType.RESPONSE_OK)
							{
								break;
							}
							else if (res == HandshakeResponseType.RESPONSE_OUTDATED)
							{
								rev++;
								continue;
							}
							else
							{
								System.err.println("Unable to download cache: got " + res + " from server");
								System.exit(1);
								return;
							}
						}

						System.out.printf("New revision: %d\n", rev);

						ccli.download();
					}
					finally
					{
						if (ccli != null)
						{
							ccli.close();
						}
					}
					fss.save();
				}

				return;
			}
			case "dump":
			{
				if (args.length != 4)
				{
					break;
				}

				List<String> names = Stream.of(args[1].split(",")).map(String::toUpperCase).collect(Collectors.toList());
				List<Dumper> dumpers = new ArrayList<>();
				if (names.contains("ALL"))
				{
					dumpers = Stream.of(Dumper.class.getDeclaredFields())
						.filter(i -> Modifier.isStatic(i.getModifiers()) && !i.isSynthetic() && Modifier.isPublic(i.getModifiers()))
						.filter(i -> i.getAnnotationsByType(NotAll.class).length == 0)
						.map(i ->
						{
							try
							{
								return (Dumper) i.get(null);
							}
							catch (ReflectiveOperationException e)
							{
								throw new RuntimeException(e);
							}
						})
						.collect(Collectors.toList());
				}
				dumpers.addAll(names.stream()
					.filter(i -> !"ALL".equals(i))
					.map(Dumper::valueOf)
					.collect(Collectors.toList()));

				try (Store store = loadStore(new File(args[2])))
				{
					File outdir = new File(args[3]);
					ExecutorService tp = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

					tp.invokeAll(dumpers.stream().map(d -> (Callable<Void>) () ->
					{
						File f = new File(outdir, d.name().toLowerCase());
						f.mkdirs();
						d.dump(store, f);
						return null;
					})
						.collect(Collectors.toList())).forEach(i ->
					{
						try
						{
							i.get();
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
					});

					tp.shutdown();
				}

				return;
			}
			default:
				System.err.println("Unknown option \"" + args[0] + "\"");
				printUsage();
				return;
		}
		printUsage();
	}

	private static void copyStore(Store dst, Store src) throws IOException
	{
		for (Index srcIdx : src.getIndexes())
		{
			Index dstIdx = dst.addIndex(srcIdx.getId());
			for (Archive srcArc : srcIdx.getArchives())
			{
				Archive dstArc = dstIdx.addArchive(srcArc.getArchiveId());

				dstArc.setCompression(srcArc.getCompression());
				dstArc.setCrc(srcArc.getCrc());
				dstArc.setFileData(srcArc.getFileData());
				dstArc.setHash(srcArc.getHash());
				dstArc.setNameHash(srcArc.getNameHash());
				dstArc.setRevision(srcArc.getRevision());

				byte[] data = src.getStorage().loadArchive(srcArc);
				if (data == null)
				{
					System.out.println("" + srcIdx.getId() + " " + srcArc.getArchiveId());
				}
				else
				{
					dst.getStorage().saveArchive(dstArc, data);
				}
			}
		}

		dst.save();
	}

	public static Store loadStore(File directory) throws IOException
	{
		Storage s = null;
		if (new File(directory, "main_file_cache.dat2").exists())
		{
			s = new DiskStorage(directory);
		}
		else if (new File(directory, "0.flatcache").exists())
		{
			s = new FlatStorage(directory);
		}
		else
		{
			throw new IOException("\"" + directory + "\" isn't a cache");
		}

		Store st = new Store(s);
		st.load();
		return st;
	}
}
