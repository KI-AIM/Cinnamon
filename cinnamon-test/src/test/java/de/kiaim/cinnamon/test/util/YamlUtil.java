package de.kiaim.cinnamon.test.util;

import java.util.regex.Matcher;

public class YamlUtil {
	public static String indentYaml(final String value) {
		final String indent = "  ";
		return indent + value.replaceAll("(?:\r\n?|\n)(?!\\z)", "$0" + Matcher.quoteReplacement(indent));
	}
}
