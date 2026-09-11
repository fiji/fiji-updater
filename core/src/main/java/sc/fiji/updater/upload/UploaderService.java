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

package sc.fiji.updater.upload;

import org.scijava.service.Service;

import sc.fiji.updater.FilesCollection;
import sc.fiji.updater.progress.Progress;

/**
 * Interface for the service that manages available upload mechanisms.
 * <p>
 * Extends {@link Service} directly rather than any grouping marker. It used to
 * extend {@code net.imagej.ImageJService}, whose purpose is to let the ImageJ2
 * gateway instantiate the ImageJ2 layer wholesale; the updater is no longer
 * part of that layer. {@code SciJavaService} would be wrong in the other
 * direction, as it marks core SciJava Common services only.
 * </p>
 * <p>
 * Nothing depends on a marker here: callers ask for this service by its own
 * interface, as {@code FilesUploader.createUploaderService} does.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public interface UploaderService extends Service {

	// CTR TODO: Extend SingletonService<Uploader>.

	/** TODO. */
	boolean hasUploader(String protocol);

	/** TODO. */
	Uploader getUploader(String protocol) throws IllegalArgumentException;

	/** TODO. */
	Uploader installUploader(String protocol, FilesCollection files, final Progress progress);
}
