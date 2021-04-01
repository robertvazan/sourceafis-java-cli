// Part of SourceAFIS for Java CLI: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis.cli.outputs;

import java.nio.file.*;
import com.machinezoo.sourceafis.*;
import com.machinezoo.sourceafis.cli.samples.*;
import com.machinezoo.sourceafis.cli.utils.*;

public class LogExtractor {
	public static Path identity(String key, Fingerprint fp) {
		return Cache.withExtension(fp.path(), Pretty.extension(ChecksumTransparencyExtractor.row(fp, key).mime));
	}
	public static byte[] collect(String key, Fingerprint fp) {
		return Cache.get(byte[].class, Paths.get("logs", "extractor", "raw", key), identity(key, fp), () -> {
			return Log.ofKey(key, () -> new FingerprintTemplate(fp.decode())).get(0);
		});
	}
	public static void collect(String key) {
		for (var fp : Fingerprint.all())
			collect(key, fp);
	}
}