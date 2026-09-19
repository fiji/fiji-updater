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

import java.util.Collections;

/**
 * A common base for all actions to be applied to a set of files.
 * 
 * <p>
 * This is the business end of the combo-boxes and context menus in the updater
 * GUI ("Keep as-is", "Update / Install", ...).
 * </p>
 * 
 * @author Johannes Schindelin
 */
public abstract class GroupAction {

	/** Whether this action can be applied to the given file. */
	public abstract boolean isValid(final FilesCollection files,
			final FileObject file);

	/** Applies this action to the given file. */
	public abstract void setAction(final FilesCollection files,
			final FileObject file);

	/**
	 * What to call this action for the given selection, which some actions
	 * describe more precisely than others -- whether an install would also be
	 * an update, say, or whether an upload would shadow another site.
	 */
	public abstract String getLabel(final FilesCollection files,
			final Iterable<FileObject> selected);

	/**
	 * What to call this action with nothing selected, which is what a button
	 * offering it is labelled before anything is picked.
	 * <p>
	 * Note: this is why {@code GroupAction} is a class rather than the
	 * interface it was. Every implementation had a {@code toString} that was
	 * this, spelled out again, and Java does not let an interface default an
	 * {@link Object} method.
	 * </p>
	 */
	@Override
	public final String toString() {
		return getLabel(null, Collections.emptyList());
	}
}
