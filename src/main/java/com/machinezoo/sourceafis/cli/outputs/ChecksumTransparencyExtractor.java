// Part of SourceAFIS for Java CLI: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis.cli.outputs;

import java.nio.file.*;
import com.machinezoo.sourceafis.*;
import com.machinezoo.sourceafis.cli.samples.*;
import com.machinezoo.sourceafis.cli.utils.*;
import one.util.streamex.*;

public class ChecksumTransparencyExtractor extends ChecksumTransparencyBase {
	private static final Path category = Paths.get("checksums", "transparency", "extractor");
	private static Table checksum(Fingerprint fp) {
		return Cache.get(Table.class, category, fp.path(), () -> {
			return collect(() -> new FingerprintTemplate(fp.decode()));
		});
	}
	public static int count(Fingerprint fp, String key) {
		return count(checksum(fp), key);
	}
	private static Table checksum() {
		return Cache.get(Table.class, category, Paths.get("all"), () -> {
			return merge(StreamEx.of(Fingerprint.all()).map(fp -> checksum(fp)).toList());
		});
	}
	public static String mime(String key) {
		return mime(checksum(), key);
	}
	public static byte[] global() {
		return global(checksum());
	}
	public static void report() {
		report(checksum());
	}
}
