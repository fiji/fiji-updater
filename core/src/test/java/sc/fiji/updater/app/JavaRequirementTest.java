/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests {@link JavaRequirement}, against the shape of a real Jaunch TOML.
 *
 * @author Curtis Rueden
 */
public class JavaRequirementTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Excerpted verbatim from a shipping fiji.toml, including its quirks. */
	private static final String[] REAL_TOML = {
		"# Minimum Java version to accept.",
		"jvm.version-min = '8'",
		"",
		"jvm.runtime-args = [",
		"    '-Dscijava.app.java-version-minimum=1.8',",
		"    '-Dscijava.app.java-version-recommended=21',",
		"    '-Dscijava.app.java-links=https://downloads.imagej.net/java/jdk-urls.txt',",
		"]",
	};

	private File toml(final String... lines) throws IOException {
		final File file = tmp.newFile("fiji.toml");
		Files.write(file.toPath(), String.join("\n", lines).getBytes("UTF-8"));
		return file;
	}

	@Test
	public void testReadsRealConfiguration() throws IOException {
		final JavaRequirement req = JavaRequirement.read(toml(REAL_TOML));
		assertTrue(req.isKnown());
		assertEquals("21", req.recommended());
		assertEquals("8", req.minimum());
		assertEquals("https://downloads.imagej.net/java/jdk-urls.txt", req.links());
	}

	/** The case the whole mechanism exists for: a channel that moved to 25. */
	@Test
	public void testNewerChannelIsNotSatisfiedByOlderJava() throws IOException {
		final JavaRequirement req = JavaRequirement.read(toml(
			"jvm.version-min = '25'",
			"    '-Dscijava.app.java-version-recommended=25',"));
		assertFalse("Java 21 must not be considered good enough for a Java 25 " +
			"channel", req.isSatisfiedBy("21.0.7"));
		assertTrue(req.isSatisfiedBy("25"));
		assertTrue(req.isSatisfiedBy("29.0.1"));
	}

	/** Being above the recommended version is fine. */
	@Test
	public void testNewerJavaSatisfiesOlderRequirement() throws IOException {
		final JavaRequirement req = JavaRequirement.read(toml(REAL_TOML));
		assertTrue(req.isSatisfiedBy("21.0.7"));
		assertTrue(req.isSatisfiedBy("25"));
	}

	/** A recommended version below the running one still fails if min is above. */
	@Test
	public void testMinimumIsEnforcedIndependently() throws IOException {
		final JavaRequirement req =
			JavaRequirement.read(toml("jvm.version-min = '25'"));
		assertFalse(req.isSatisfiedBy("21"));
		assertNull(req.recommended());
		assertTrue(req.isKnown());
	}

	/**
	 * A configuration that says nothing yields no requirement, and an unknown
	 * requirement is treated as satisfied -- failing to parse a file must not
	 * block a channel switch.
	 */
	@Test
	public void testUnreadableConfigurationIsNotAnObstacle() throws IOException {
		final JavaRequirement missing =
			JavaRequirement.read(new File(tmp.getRoot(), "nonesuch.toml"));
		assertFalse(missing.isKnown());
		assertTrue(missing.isSatisfiedBy("21"));

		final JavaRequirement silent =
			JavaRequirement.read(toml("# nothing to see here"));
		assertFalse(silent.isKnown());
		assertTrue(silent.isSatisfiedBy("8"));
	}

	@Test
	public void testNullConfiguration() {
		assertFalse(JavaRequirement.read(null).isKnown());
		assertFalse(JavaRequirement.unknown().isKnown());
	}
}
