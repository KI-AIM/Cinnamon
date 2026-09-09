package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextExtractionServiceTest {

	@Test
	void wideFormatUsesMaximumOccurrencesPerSourceInsteadOfTheirSum() {
		final var fields = JsonNodeFactory.instance.arrayNode();
		final ObjectNode procedures = fields.addObject();
		procedures.put("name", "procedures");
		procedures.put("type", "object");
		procedures.putArray("fields").addObject()
		          .put("name", "name").put("type", "string").put("description", "Procedure name");

		final ObjectNode longResult = JsonNodeFactory.instance.objectNode();
		final var rows = longResult.putArray("rows");
		addProcedureRows(rows, 0, 2);
		addProcedureRows(rows, 1, 3);

		final TextExtractionService service = new TextExtractionService(null, null, null);
		final TextExtractionService.WideExtraction wide = service.toWideFormat(longResult, fields);

		assertEquals(2, wide.result().path("rows").size());
		assertTrue(wide.result().path("columns").toString().contains("procedures_3_name"));
		assertFalse(wide.result().path("columns").toString().contains("procedures_4_name"));
	}

	private void addProcedureRows(final com.fasterxml.jackson.databind.node.ArrayNode rows,
	                              final int sourceRowIndex, final int count) {
		for (int occurrence = 1; occurrence <= count; occurrence++) {
			final ObjectNode row = rows.addObject();
			row.put("row_index", rows.size() - 1);
			row.put("source_row_index", sourceRowIndex);
			row.put("text_extraction_id", sourceRowIndex + 1);
			row.put("object_type", "procedures");
			row.put("name", "Procedure " + occurrence);
		}
	}
}
