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

package sc.fiji.updater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static sc.fiji.updater.UpdaterTestUtils.cleanup;
import static sc.fiji.updater.UpdaterTestUtils.initialize;

import java.util.Collections;
import java.util.Set;

import org.junit.Test;

import sc.fiji.updater.action.InstallOrUpdate;
import sc.fiji.updater.action.KeepAsIs;
import sc.fiji.updater.action.Remove;
import sc.fiji.updater.action.Uninstall;
import sc.fiji.updater.action.Upload;

/**
 * Tests that each action names itself the same way whether asked with nothing
 * selected or asked directly -- which is what lets {@link GroupAction} say it
 * once rather than every implementation restating it.
 *
 * @author Curtis Rueden
 */
public class GroupActionTest {

	@Test
	public void testLabelsWithNothingSelected() {
		assertEquals("Keep as-is", KeepAsIs.INSTANCE.toString());
		assertEquals("Uninstall", Uninstall.INSTANCE.toString());
		// Neither an install nor an update is implied by an empty selection, so
		// the button offers both.
		assertEquals("Install / Update", InstallOrUpdate.INSTANCE.toString());
		assertEquals("Upload to Fiji", new Upload("Fiji").toString());
		assertEquals("Mark obsolete (Fiji)", new Remove("Fiji").toString());
	}

	/** toString is the label for an empty selection, and nothing else. */
	@Test
	public void testToStringIsTheEmptySelectionLabel() {
		for (final GroupAction action : new GroupAction[] { KeepAsIs.INSTANCE,
			Uninstall.INSTANCE, InstallOrUpdate.INSTANCE, new Upload("Fiji"),
			new Remove("Fiji") })
		{
			assertEquals(action.getClass().getSimpleName(),
				action.getLabel(null, Collections.emptyList()), action.toString());
		}
	}

	/**
	 * The stateless actions are shared rather than allocated afresh, which
	 * matters because getValidActions is called per file per repaint.
	 */
	@Test
	public void testStatelessActionsAreNotReallocated() throws Exception {
		final FilesCollection files = initialize();
		try {
			final GroupAction first = keepAsIs(files.getValidActions());
			final GroupAction second = keepAsIs(files.getValidActions());
			assertSame(first, second);
			assertSame(KeepAsIs.INSTANCE, first);
		}
		finally {
			cleanup(files);
		}
	}

	private static GroupAction keepAsIs(final Set<GroupAction> actions) {
		return actions.stream().filter(a -> a instanceof KeepAsIs).findFirst()
			.orElseThrow(() -> new AssertionError("no KeepAsIs among " + actions));
	}
}
