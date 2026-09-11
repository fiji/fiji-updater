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
package sc.fiji.updater.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link AppLayout}'s reading of the launcher-declared system properties.
 *
 * @author Curtis Rueden
 */
public class AppLayoutTest {


	@Before
	@After
	public void clearProperties() {
		for (final String property : AppLayout.APP_DIRECTORY_PROPERTIES) {
			System.clearProperty(property);
		}
		System.clearProperty(AppLayout.DEBIAN_PACKAGE_PROPERTY);
	}

	private void set(final String property, final String value) {
		System.setProperty(property, value);
	}

	/** With no launcher in the picture, there is no declared app directory. */
	@Test
	public void testNoPropertySet() {
		assertNull(AppLayout.appDirectory());
	}

	/** The properties are consulted in order, most current first. */
	@Test
	public void testPrecedence() {
		set("ij.dir", "/legacy-ij");
		assertEquals("/legacy-ij", AppLayout.appDirectory());

		set("imagej.dir", "/legacy-imagej");
		assertEquals("/legacy-imagej", AppLayout.appDirectory());

		set("fiji.dir", "/fiji");
		assertEquals("/fiji", AppLayout.appDirectory());

		set("scijava.app.directory", "/app");
		assertEquals("/app", AppLayout.appDirectory());
	}

	/** Every property named in the list is actually consulted. */
	@Test
	public void testEachPropertyWorksAlone() {
		for (final String property : AppLayout.APP_DIRECTORY_PROPERTIES) {
			clearProperties();
			set(property, "/somewhere");
			assertEquals(property, "/somewhere", AppLayout.appDirectory());
		}
	}

	/**
	 * {@link AppLayout#appRoot()} honours whichever property the launcher set --
	 * not just the legacy {@code imagej.dir} that the entry points used to
	 * consult individually. Jaunch sets {@code scijava.app.directory} and
	 * {@code fiji.dir} and neither of the legacy two, so a regression here means
	 * every entry point silently operating on a guessed directory.
	 */
	@Test
	public void testAppRootFollowsAnyProperty() {
		for (final String property : AppLayout.APP_DIRECTORY_PROPERTIES) {
			clearProperties();
			set(property, "/somewhere");
			assertEquals(property, new java.io.File("/somewhere"), AppLayout.appRoot());
		}
	}

	/** With no property set, there is nothing to trust, so it is a guess. */
	@Test
	public void testAppRootFallsBackWhenUndeclared() {
		assertTrue(AppLayout.isDeveloperSetup());
		assertNotNull(AppLayout.appRoot());
	}

	/** A declared root means a launcher started us, i.e. not a developer setup. */
	@Test
	public void testDeveloperSetupDetection() {
		assertTrue(AppLayout.isDeveloperSetup());
		set("scijava.app.directory", "/app");
		assertFalse(AppLayout.isDeveloperSetup());
	}

	@Test
	public void testDebianPackageDetection() {
		assertFalse(AppLayout.isDebianPackage());
		set(AppLayout.DEBIAN_PACKAGE_PROPERTY, "false");
		assertFalse(AppLayout.isDebianPackage());
		set(AppLayout.DEBIAN_PACKAGE_PROPERTY, "true");
		assertTrue(AppLayout.isDebianPackage());
	}
}
