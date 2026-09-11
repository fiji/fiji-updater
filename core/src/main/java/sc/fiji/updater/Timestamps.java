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

import java.io.File;
import java.text.DecimalFormat;
import java.util.Calendar;

/**
 * The updater's timestamp format, and conversions to and from it.
 * <p>
 * A timestamp is a {@code long} whose decimal digits read
 * {@code yyyyMMddHHmmss} in the local time zone -- not a Unix epoch. It is the
 * form written to {@code db.xml.gz}, so it is part of the file format and
 * cannot be swapped for something more sensible without a migration.
 * </p>
 *
 * @author Johannes Schindelin
 */
public final class Timestamps {

	private Timestamps() {
		// NB: prevent instantiation of utility class
	}

	private static final String[] MONTHS = { "Zero", "Jan", "Feb", "Mar", "Apr",
		"May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	/** The current time, as a timestamp. */
	public static long currentTimestamp() {
		return Long.parseLong(timestamp(Calendar.getInstance()));
	}

	/** The file's last-modified time, as a timestamp. */
	public static long getTimestamp(final File file) {
		return Long.parseLong(timestamp(file.lastModified()));
	}

	/** Formats a Unix epoch in milliseconds as a timestamp. */
	public static String timestamp(final long millis) {
		final Calendar date = Calendar.getInstance();
		date.setTimeInMillis(millis);
		return timestamp(date);
	}

	/** Formats a date as a timestamp. */
	public static String timestamp(final Calendar date) {
		final DecimalFormat format = new DecimalFormat("00");
		final int month = date.get(Calendar.MONTH) + 1;
		final int day = date.get(Calendar.DAY_OF_MONTH);
		final int hour = date.get(Calendar.HOUR_OF_DAY);
		final int minute = date.get(Calendar.MINUTE);
		final int second = date.get(Calendar.SECOND);
		return "" + date.get(Calendar.YEAR) + format.format(month) +
			format.format(day) + format.format(hour) + format.format(minute) +
			format.format(second);
	}

	/** Converts a timestamp to a Unix epoch in milliseconds. */
	public static long timestamp2millis(final long timestamp) {
		return timestamp2millis("" + timestamp);
	}

	/** Converts a timestamp to a Unix epoch in milliseconds. */
	public static long timestamp2millis(final String timestamp) {
		final Calendar calendar = Calendar.getInstance();
		calendar.set(Integer.parseInt(timestamp.substring(0, 4)), Integer
			.parseInt(timestamp.substring(4, 6)) - 1, Integer.parseInt(timestamp
			.substring(6, 8)), Integer.parseInt(timestamp.substring(8, 10)), Integer
			.parseInt(timestamp.substring(10, 12)), Integer.parseInt(timestamp
			.substring(12, 14)));
		return calendar.getTimeInMillis();
	}

	/** Renders a timestamp for human consumption, e.g. {@code 15 Jun 2012 10:30:00}. */
	public static String prettyPrintTimestamp(final long timestamp) {
		final String t = "" + timestamp + "00000000000000";
		return t.substring(6, 8) + " " +
			MONTHS[Integer.parseInt(t.substring(4, 6))] + " " + t.substring(0, 4) + " " +
			t.substring(8, 10) + ":" + t.substring(10, 12) + ":" + t.substring(12, 14);
	}

}
