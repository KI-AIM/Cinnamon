package de.kiaim.cinnamon.platform.helper.fhir;

import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts the XHTML of FHIR narratives ({@code text.div}) into plain text and back.
 * <p>
 * Narratives are imported as plain text, so the free text anonymization works on text without markup
 * and exported narratives are always valid XHTML.
 * Block elements and line breaks become line breaks, table cells are separated by spaces, and all other markup
 * is removed. When exporting, each line becomes a line of the narrative separated by {@code <br/>}.
 *
 * @author Daniel Preciado-Marquez
 */
public final class FhirNarrativeText {

	/**
	 * Namespace required for the div of a narrative.
	 */
	public static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";

	/**
	 * Elements that start a new line.
	 */
	private static final Set<String> BLOCK_ELEMENTS = Set.of(
			"address", "blockquote", "caption", "dd", "div", "dl", "dt", "h1", "h2", "h3", "h4", "h5", "h6", "hr",
			"li", "ol", "p", "pre", "table", "tbody", "tfoot", "thead", "tr", "ul");

	/**
	 * Elements whose content is separated from the surrounding text by a space.
	 */
	private static final Set<String> CELL_ELEMENTS = Set.of("td", "th");

	private FhirNarrativeText() {
	}

	/**
	 * Extracts the text of the given XHTML.
	 * Whitespace within a line is collapsed and empty lines are removed.
	 *
	 * @param div The div of the narrative.
	 * @return The plain text, empty if the narrative does not contain any text.
	 */
	public static String toPlainText(final XhtmlNode div) {
		final StringBuilder text = new StringBuilder();
		appendText(div, text);

		final List<String> lines = new ArrayList<>();
		for (final String line : text.toString().split("\n")) {
			final String normalizedLine = line.replaceAll("\\s+", " ").trim();
			if (!normalizedLine.isEmpty()) {
				lines.add(normalizedLine);
			}
		}
		return String.join("\n", lines);
	}

	/**
	 * Creates the div of a narrative containing the given text.
	 * Lines are separated by {@code <br/>}. Special characters are escaped when the narrative is encoded.
	 *
	 * @param text The plain text.
	 * @return The div.
	 */
	public static XhtmlNode toXhtml(final String text) {
		final XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
		div.setAttribute("xmlns", XHTML_NAMESPACE);

		final String[] lines = text.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				div.addTag("br");
			}
			div.addText(lines[i]);
		}
		return div;
	}

	private static void appendText(final XhtmlNode node, final StringBuilder text) {
		if (node.getNodeType() == NodeType.Text) {
			if (node.getContent() != null) {
				text.append(node.getContent());
			}
			return;
		}

		if (node.getNodeType() != NodeType.Element && node.getNodeType() != NodeType.Document) {
			// Comments, processing instructions, ...
			return;
		}

		final String name = node.getName() != null ? node.getName().toLowerCase() : "";
		if (name.equals("br")) {
			text.append('\n');
			return;
		}

		final String separator = BLOCK_ELEMENTS.contains(name) ? "\n" : CELL_ELEMENTS.contains(name) ? " " : "";
		text.append(separator);
		for (final XhtmlNode child : node.getChildNodes()) {
			appendText(child, text);
		}
		text.append(separator);
	}
}
