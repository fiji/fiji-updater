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

package sc.fiji.updater.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import sc.fiji.updater.site.UpdateSiteNetwork;

/**
 * What is left of the updater's original utility class: file and JAR
 * checksums, and a handful of string and stream helpers.
 * <p>
 * Nothing here is referenced outside the core, which is why this package is
 * not exported. The members that were -- the timestamp format, the HTTP
 * plumbing, the two questions about the installation directory, the login
 * prefs key and the fallback log service -- moved to {@code Timestamps},
 * {@code Connections}, {@code AppLayout} and {@code UpdaterConsole}
 * respectively.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public final class UpdaterUtil {

	public static String MAIN_URL = "https://" + UpdateSiteNetwork.MAIN_SITE_PATH;
	static String UPDATE_DIRECTORY =
		UpdateSiteNetwork.MAIN_SITE_UPLOAD_DIRECTORY;
	static String SSH_HOST = UpdateSiteNetwork.MAIN_SITE_SSH_HOST;

	public static final String XML_COMPRESSED = "db.xml.gz";

	private UpdaterUtil() {
		// Prevent instantiation of utility class.
	}

	public static String stripSuffix(final String string, final String suffix) {
		if (!string.endsWith(suffix)) return string;
		return string.substring(0, string.length() - suffix.length());
	}

	static String stripPrefix(final String string, final String prefix) {
		if (!string.startsWith(prefix)) return string;
		return string.substring(prefix.length());
	}

	// get digest of the file as according to fullPath
	public static String getDigest(final String path, final File file)
		throws NoSuchAlgorithmException, FileNotFoundException, IOException,
		UnsupportedEncodingException
	{
		if (path.endsWith(".jar")) return getJarDigest(file);
		final MessageDigest digest = getDigest();
		digest.update(path.getBytes("ASCII"));
		if (file != null) updateDigest(new FileInputStream(file), digest);
		return toHex(digest.digest());
	}

	public static MessageDigest getDigest() throws NoSuchAlgorithmException {
		return MessageDigest.getInstance("SHA-1");
	}

	/**
	 * Handle previous methods to calculate the checksums gracefully Earlier, we
	 * simply checksummed all the contents in .jar files. But that leads to files
	 * marked as modified all the time when Maven rebuilds them. So we changed the
	 * way they are checksummed. Let's handle old checksums, too, though, by
	 * simply adding them to the previous versions.
	 * 
	 * @param path the path relative to the ImageJ directory
	 * @param file the file
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static List<String> getObsoleteDigests(final String path,
		final File file) throws FileNotFoundException, IOException
	{
		if (!path.endsWith(".jar")) return null;
		final List<String> result = new ArrayList<>();
		result.add(getJarDigest(file, true, true, false));
		result.add(getJarDigest(file, true, false, false));
		result.add(getJarDigest(file, false, false, false));
		return result;
	}

	static void updateDigest(final InputStream input,
		final MessageDigest digest) throws IOException
	{
		final byte[] buffer = new byte[65536];
		final DigestInputStream digestStream = new DigestInputStream(input, digest);
		while (digestStream.read(buffer) >= 0); /* do nothing */
		digestStream.close();
	}

	public static final char[] hex = { '0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	static String toHex(final byte[] bytes) {
		final char[] buffer = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			buffer[i * 2] = hex[(bytes[i] & 0xf0) >> 4];
			buffer[i * 2 + 1] = hex[bytes[i] & 0xf];
		}
		return new String(buffer);
	}

	public static String getJarDigest(final File file) throws FileNotFoundException, IOException {
		return getJarDigest(file, true, true, true);
	}

	public static String getJarDigest(final File file, boolean treatPropertiesSpecially, boolean treatManifestsSpecially, boolean keepOnlyMainClassInManifest) throws FileNotFoundException, IOException {
		MessageDigest digest = null;
		try {
			digest = getDigest();
		}
		catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		if (file != null) {
			final JarFile jar = new JarFile(file);
			final List<JarEntry> list = Collections.list(jar.entries());
			Collections.sort(list, new JarEntryComparator());

			for (final JarEntry entry : list) {
				digest.update(entry.getName().getBytes("ASCII"));
				InputStream inputStream = jar.getInputStream(entry);
				// .properties files have a date in a comment; let's ignore this for the checksum
				// For backwards-compatibility, activate the .properties mangling only from June 15th, 2012
				if (treatPropertiesSpecially && entry.getName().endsWith(".properties")) {
					inputStream = new SkipHashedLines(inputStream);
				}
				// same for manifests, but with July 6th, 2012
				if (treatManifestsSpecially && entry.getName().equals("META-INF/MANIFEST.MF")) {
					inputStream = new FilterManifest(inputStream, keepOnlyMainClassInManifest);
				}
				updateDigest(inputStream, digest);
			}
			jar.close();
		}
		return toHex(digest.digest());
	}

	private static class JarEntryComparator implements Comparator<JarEntry> {

		@Override
		public int compare(final JarEntry entry1, final JarEntry entry2) {
			final String name1 = entry1.getName();
			final String name2 = entry2.getName();
			return name1.compareTo(name2);
		}

	}

	public static <T> String join(final String delimiter, final Iterable<T> list)
	{
		final StringBuilder builder = new StringBuilder();
		for (final T object : list)
			builder.append((builder.length() > 0 ? delimiter : "") + object.toString());
		return builder.toString();
	}

	// Get entire byte data
	public static byte[] readStreamAsBytes(final InputStream input)
		throws IOException
	{
		byte[] buffer = new byte[1024];
		int offset = 0, len = 0;
		for (;;) {
			if (offset == buffer.length) buffer = realloc(buffer, 2 * buffer.length);
			len = input.read(buffer, offset, buffer.length - offset);
			if (len < 0) return realloc(buffer, offset);
			offset += len;
		}
	}

	private static byte[] realloc(final byte[] buffer, final int newLength) {
		if (newLength == buffer.length) return buffer;
		final byte[] newBuffer = new byte[newLength];
		System.arraycopy(buffer, 0, newBuffer, 0, Math
			.min(newLength, buffer.length));
		return newBuffer;
	}

	protected static String readFile(final File file) throws IOException {
		final StringBuilder builder = new StringBuilder();
		final BufferedReader reader = new BufferedReader(new FileReader(file));
		for (;;) {
			final String line = reader.readLine();
			if (line == null) break;
			builder.append(line).append('\n');
		}
		reader.close();

		return builder.toString();
	}

	// This method writes to a .bup file and then renames; this might not work on
	// Windows
	protected static void writeFile(final File file, final String contents)
		throws IOException
	{
		final File result =
			new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".new");
		final FileOutputStream out = new FileOutputStream(result);
		out.write(contents.getBytes());
		out.close();
		result.renameTo(file);
	}

	// This method protects us from Java5's absence of File.canExecute
	public static boolean canExecute(File file) {
		try {
			return file.canExecute();
		} catch (Throwable t) {
			// ignore
			return false;
		}
	}

	public static<T> Iterable<T> iterate(final Enumeration<T> en) {
        final Iterator<T> iterator = new Iterator<T>() {
            @Override
			public boolean hasNext() {
                    return en.hasMoreElements();
            }

            @Override
			public T next() {
                    return en.nextElement();
            }

            @Override
			public void remove() {
                    throw new UnsupportedOperationException();
            }
        };

	    return new Iterable<T>() {
            @Override
			public Iterator<T> iterator() {
                    return iterator;
            }
	    };
	}

	/**
	 * CamelCases a {@link String}.
	 */
	public static String toCamelCase(final String string) {
		if (string == null || string.length() == 0) return string;
		final StringBuilder builder = new StringBuilder();
		boolean upCase = true;
		for (char ch : string.toCharArray()) {
			if (Character.isLetterOrDigit(ch)) {
				builder.append(upCase ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
				upCase = false;
			} else {
				upCase = true;
			}
		}
		return builder.toString();
	}

}
