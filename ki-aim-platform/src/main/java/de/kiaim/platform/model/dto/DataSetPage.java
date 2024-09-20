package de.kiaim.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter @AllArgsConstructor
public class DataSetPage {

	private final List<List<Object>> data;

	private final int page;

	private final int perPage;

	private final int total;

	private final int totalPages;
}
