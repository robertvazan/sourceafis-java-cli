// Part of SourceAFIS for Java CLI: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis.cli;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.zip.*;
import org.apache.commons.io.*;
import org.slf4j.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.cbor.*;
import com.machinezoo.noexception.*;
import com.machinezoo.sourceafis.*;

abstract class PersistentCache<T> implements Supplier<T> {
	static final Path home;
	static final Path output;
	private static final Logger logger = LoggerFactory.getLogger(PersistentCache.class);
	static {
		/*
		 * First try XDG_* variables. Data directories may be in strange locations, for example inside flatpak.
		 */
		var configured = System.getenv("XDG_CACHE_HOME");
		Path root;
		if (configured != null)
			root = Paths.get(configured);
		else {
			/*
			 * Fall back to XDG_* default. This will perform poorly on Windows, but it will work.
			 */
			root = Paths.get(System.getProperty("user.home"), ".cache");
		}
		home = root.resolve("sourceafis");
		output = home.resolve("java").resolve(FingerprintCompatibility.version());
		logger.info("Cache directory: {}", home);
		logger.info("Library version: {}", FingerprintCompatibility.version());
	}
	static Path withExtension(Path path, String extension) {
		return path.resolveSibling(path.getFileName() + extension);
	}
	private static interface Serialization {
		Path rename(Path path);
		byte[] serialize(Object value);
		<T> T deserialize(byte[] serialized, Class<T> clazz);
	}
	private static class TrivialSerialization implements Serialization {
		@Override
		public Path rename(Path path) {
			return path;
		}
		@Override
		public byte[] serialize(Object value) {
			return (byte[])value;
		}
		@Override
		@SuppressWarnings("unchecked")
		public <T> T deserialize(byte[] serialized, Class<T> clazz) {
			return (T)serialized;
		}
	}
	private static class CborSerialization implements Serialization {
		private static final ObjectMapper mapper = new ObjectMapper(new CBORFactory())
			.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		@Override
		public Path rename(Path path) {
			return withExtension(path, ".cbor");
		}
		@Override
		public byte[] serialize(Object value) {
			return Exceptions.sneak().get(() -> mapper.writeValueAsBytes(value));
		}
		@Override
		public <T> T deserialize(byte[] serialized, Class<T> clazz) {
			return Exceptions.sneak().get(() -> mapper.readValue(serialized, clazz));
		}
	}
	private static Serialization serialization(Class<?> clazz) {
		if (clazz == byte[].class)
			return new TrivialSerialization();
		return new CborSerialization();
	}
	private static interface Compression {
		Path rename(Path path);
		byte[] compress(byte[] data);
		byte[] decompress(byte[] compressed);
	}
	private static class TrivialCompression implements Compression {
		@Override
		public Path rename(Path path) {
			return path;
		}
		@Override
		public byte[] compress(byte[] data) {
			return data;
		}
		@Override
		public byte[] decompress(byte[] compressed) {
			return compressed;
		}
	}
	private static class GzipCompression implements Compression {
		@Override
		public Path rename(Path path) {
			return withExtension(path, ".gz");
		}
		@Override
		public byte[] compress(byte[] data) {
			return Exceptions.sneak().get(() -> {
				var buffer = new ByteArrayOutputStream();
				try (var gzip = new GZIPOutputStream(buffer)) {
					gzip.write(data);
				}
				return buffer.toByteArray();
			});
		}
		@Override
		public byte[] decompress(byte[] compressed) {
			return Exceptions.sneak().get(() -> {
				try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
					return IOUtils.toByteArray(gzip);
				}
			});
		}
	}
	private static Compression compression(Path path) {
		if (path.getFileName().endsWith(".cbor"))
			return new GzipCompression();
		return new TrivialCompression();
	}
	private static final ConcurrentMap<Path, AtomicBoolean> reported = new ConcurrentHashMap<>();
	static <T> T get(Class<T> clazz, Path category, Path identity, Supplier<T> supplier) {
		return Exceptions.sneak().get(() -> {
			var serialization = serialization(clazz);
			var compression = compression(serialization.rename(identity));
			var path = compression.rename(serialization.rename(output.resolve(category).resolve(identity)));
			if (Files.exists(path))
				return serialization.deserialize(compression.decompress(Files.readAllBytes(path)), clazz);
			if (!reported.computeIfAbsent(category, c -> new AtomicBoolean()).getAndSet(true))
				logger.info("Computing {}...", category);
			T value = supplier.get();
			Files.createDirectories(path.getParent());
			Files.write(path, compression.compress(serialization.serialize(value)));
			return value;
		});
	}
}
