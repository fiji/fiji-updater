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


/**
 * The Fiji Updater: what an installation has, what its update sites offer, and
 * how to reconcile the two.
 * <p>
 * The exported packages are the ones something outside this module needs. Three
 * are deliberately not exported: {@code cli} is an entry point, {@code xml} is
 * the {@code db.xml.gz} codec, and {@code internal} is plumbing. Nothing in the
 * GUI or in either uploader refers to any of them.
 * </p>
 */
module sc.fiji.updater {

	requires java.xml;
	requires org.scijava;
	requires org.scijava.launcher;

	exports sc.fiji.updater;
	exports sc.fiji.updater.action;
	exports sc.fiji.updater.app;
	exports sc.fiji.updater.channel;
	exports sc.fiji.updater.diff;
	exports sc.fiji.updater.progress;
	exports sc.fiji.updater.site;
	exports sc.fiji.updater.ui;
	exports sc.fiji.updater.upload;

	// SciJava discovers plugins by reflection, and injects their @Parameter
	// fields the same way, so every package holding one must be open to it.
	opens sc.fiji.updater to org.scijava;
	opens sc.fiji.updater.cli to org.scijava;
	opens sc.fiji.updater.upload to org.scijava;
}
