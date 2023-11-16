package de.kiaim.platform.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DataRow {

	private final List<Data> data;
}
