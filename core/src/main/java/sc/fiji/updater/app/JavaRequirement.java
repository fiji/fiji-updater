/*-
 * #%L
 * Fiji software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2026 Fiji developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package sc.fiji.updater.app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.scijava.launcher.Versions;

/**
 * The Java version an update channel expects, read from the launcher
 * configuration it ships.
 * <p>
 * A channel exists largely because the application's Java requirement moved:
 * A.punctulata on 21, some later channel on 25 or 29. The requirement travels
 * with the channel, in the Jaunch TOML the channel publishes, which the updater
 * manages like any other file.
 * </p>
 * <h2>Why this has to be read before the restart</h2>
 * <p>
 * Jaunch applies pending updates itself, via an {@code apply-update} directive
 * in the very configuration it has already read. So the first launch after a
 * channel switch moves the new files into place -- including the new TOML --
 * but has already decided which JVM to use, from the <em>old</em> TOML. That
 * launch therefore runs the new channel's code on the previous channel's Java.
 * If the new channel needs a newer one, the result is not a helpful prompt from
 * the app-launcher; it is an UnsupportedClassVersionError before anything can
 * prompt at all.
 * </p>
 * <p>
 * Reading the requirement while the old session is still running lets the Java
 * be installed first. {@code Java.upgrade} records the chosen JVM in the
 * launcher's CFG, which the launcher honours over its own search, so the next
 * launch gets the new files and a Java that can run them, in one restart.
 * </p>
 * <h2>On parsing</h2>
 * <p>
 * This looks for known lines rather than parsing TOML, deliberately: it wants
 * two well-known settings, and a TOML parser is a dependency the updater should
 * not take on for that. A line it cannot find yields an unknown requirement,
 * and an unknown requirement means the eager upgrade is skipped -- which leaves
 * exactly the behavior there would have been without this class. Failure
 * degrades to the status quo rather than to damage.
 * </p>
 *
 * @author Curtis Rueden
 */
public final class JavaRequirement {

	/** What the application prefers, and what app-launcher offers to install. */
	private static final Pattern RECOMMENDED = Pattern.compile(
		"-Dscijava\\.app\\.java-version-recommended=([^'\"\\s,]+)");

	/** What the launcher will refuse to start below. */
	private static final Pattern MINIMUM = Pattern.compile(
		"^\\s*jvm\\.version-min\\s*=\\s*['\"]([^'\"]+)['\"]");

	/** Where to download a suitable Java from. */
	private static final Pattern LINKS = Pattern.compile(
		"-Dscijava\\.app\\.java-links=([^'\"\\s,]+)");

	private final String recommended;
	private final String minimum;
	private final String links;

	private JavaRequirement(final String recommended, final String minimum,
		final String links)
	{
		this.recommended = recommended;
		this.minimum = minimum;
		this.links = links;
	}

	/** A requirement that says nothing, because none could be read. */
	public static JavaRequirement unknown() {
		return new JavaRequirement(null, null, null);
	}

	/**
	 * Reads the requirement from a launcher configuration file.
	 *
	 * @param toml the application's Jaunch TOML; may not exist, in which case the
	 *          requirement is {@link #unknown()}.
	 */
	public static JavaRequirement read(final File toml) {
		if (toml == null || !toml.isFile()) return unknown();
		final List<String> lines;
		try {
			lines = Files.readAllLines(toml.toPath(), StandardCharsets.UTF_8);
		}
		catch (final IOException e) {
			return unknown();
		}
		String recommended = null, minimum = null, links = null;
		for (final String line : lines) {
			if (recommended == null) recommended = firstGroup(RECOMMENDED, line);
			if (minimum == null) minimum = firstGroup(MINIMUM, line);
			if (links == null) links = firstGroup(LINKS, line);
		}
		return new JavaRequirement(recommended, minimum, links);
	}

	private static String firstGroup(final Pattern pattern, final String line) {
		final Matcher matcher = pattern.matcher(line);
		return matcher.find() ? matcher.group(1) : null;
	}

	/** The preferred Java version, or null if the configuration did not say. */
	public String recommended() {
		return recommended;
	}

	/** The lowest Java version the launcher will start on, or null. */
	public String minimum() {
		return minimum;
	}

	/** Where a suitable Java may be downloaded from, or null. */
	public String links() {
		return links;
	}

	/** Whether anything at all could be read. */
	public boolean isKnown() {
		return recommended != null || minimum != null;
	}

	/**
	 * Whether the given Java version satisfies this requirement.
	 * <p>
	 * An unknown requirement is treated as satisfied: refusing to proceed
	 * because a configuration file could not be read would block channel
	 * switches over a parsing detail.
	 * </p>
	 *
	 * @param version the Java version to test, as {@code Java.currentVersion}
	 *          reports it.
	 */
	public boolean isSatisfiedBy(final String version) {
		if (version == null) return !isKnown();
		if (minimum != null && Versions.compare(version, minimum) < 0) return false;
		if (recommended != null && Versions.compare(version, recommended) < 0) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		if (!isKnown()) return "unknown";
		final StringBuilder sb = new StringBuilder();
		if (recommended != null) sb.append("recommended ").append(recommended);
		if (minimum != null) {
			if (sb.length() > 0) sb.append(", ");
			sb.append("minimum ").append(minimum);
		}
		return sb.toString();
	}
}
