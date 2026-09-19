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

package sc.fiji.updater.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import sc.fiji.updater.FileObject;
import sc.fiji.updater.internal.UpdaterUtil;

/**
 * A helper class to read information from Maven-built .jar files.
 * 
 * @author Johannes Schindelin
 */
public class POMParser extends DefaultHandler {
	private FileObject file;
	private String body, prefix;

	public POMParser(final FileObject file) {
		this.file = file;
	}

	public static void fillMetadataFromJar(final FileObject object, final File file) throws ParserConfigurationException, IOException, SAXException {
		final JarFile jar = new JarFile(file);
		boolean read = false;
		for (final JarEntry entry : UpdaterUtil.iterate(jar.entries())) {
			if (!read && entry.getName().matches("META-INF/maven/.*/pom.xml")) {
				new POMParser(object).read(jar.getInputStream(entry));
				read = true;
			}
		}
		object.setLocalCoordinate(coordinate(jar));
		jar.close();
	}

	/**
	 * Reads the {@code groupId:artifactId} a .jar was built as.
	 *
	 * @param file the .jar file
	 * @return the coordinate, or null if the file does not name exactly one
	 * @throws IOException if the file cannot be read as a .jar
	 */
	public static String readCoordinate(final File file) throws IOException {
		final JarFile jar = new JarFile(file);
		try {
			return coordinate(jar);
		}
		finally {
			jar.close();
		}
	}

	private static String coordinate(final JarFile jar) {
		final Set<String> coordinates = new LinkedHashSet<>();
		for (final JarEntry entry : UpdaterUtil.iterate(jar.entries())) {
			final Matcher matcher = POM_PATH.matcher(entry.getName());
			if (matcher.matches()) {
				coordinates.add(matcher.group(1) + ":" + matcher.group(2));
			}
		}
		// Note: a shaded .jar carries the coordinates of everything it absorbed,
		// so the one it is known by cannot be told from the ones it contains.
		return coordinates.size() == 1 ? coordinates.iterator().next() : null;
	}

	/**
	 * Where a Maven-built .jar records the coordinate it was built as.
	 * <p>
	 * The two path components are the groupId and the artifactId, which is
	 * better than reading them from the {@code pom.xml}: either can be
	 * inherited from a parent this parser cannot see.
	 * </p>
	 */
	private static final Pattern POM_PATH = Pattern
		.compile("META-INF/maven/([^/]+)/([^/]+)/pom\\.xml");

	public void read(final InputStream in) throws ParserConfigurationException, IOException, SAXException {
		final InputSource inputSource = new InputSource(in);
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);

		// commented-out as per Postel's law
		// factory.setValidating(true);

		final SAXParser parser = factory.newSAXParser();
		final XMLReader xr = parser.getXMLReader();
		xr.setContentHandler(this);
		xr.setErrorHandler(new XMLFileErrorHandler());
		xr.parse(inputSource);
	}

	@Override
	public void startDocument() {
		body = "";
		prefix = "";
	}

	@Override
	public void endDocument() {}

	@Override
	public void startElement(final String uri, final String name,
		final String qName, final Attributes atts)
	{
		prefix += ">" + qName;
		body = "";
	}

	@Override
	public void
		endElement(final String uri, final String name, final String qName)
	{
		if (prefix.equals(">project>description")) {
			if (!"".equals(body)) {
				file.setDescription(body);
				file.setDescriptionFromPOM(true);
			}
		}
		else if (prefix.equals(">project>developers>developer>name")) {
			file.addAuthor(body);
		}

		prefix = prefix.substring(0, prefix.length() - 1 - qName.length());
	}

	@Override
	public void characters(final char ch[], final int start, final int length) {
		body += new String(ch, start, length);
	}

}
