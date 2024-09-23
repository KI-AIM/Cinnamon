package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.platform.exception.BadColumnNameException;
import de.kiaim.platform.exception.BadDataSetIdException;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.exception.InternalIOException;
import de.kiaim.platform.model.entity.ExecutionStepEntity;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service class for managing projects.
 */
@Service
public class ProjectService {

	private final ObjectMapper yamlMapper;
	private final UserRepository userRepository;
	private final DatabaseService databaseService;

	public ProjectService(final ObjectMapper yamlMapper, final UserRepository userRepository,
	                      final DatabaseService databaseService) {
		this.yamlMapper = yamlMapper;
		this.userRepository = userRepository;
		this.databaseService = databaseService;
	}

	/**
	 * Checks if the given user has a project.
	 * @param user The user to check.
	 * @return If the user ha a project.
	 */
	public boolean hasProject(final UserEntity user) {
		final UserEntity user2 = userRepository.findById(user.getEmail()).get();
		return user2.getProject() != null;
	}

	/**
	 * Creates and returns a new project for the given user if they do not have one.
	 * Otherwise, returns the existing project.
	 * @param user The user.
	 * @return The projects of the user.
	 */
	public ProjectEntity createProject(final UserEntity user) {
		if (hasProject(user)) {
			return user.getProject();
		}

		final ProjectEntity project = new ProjectEntity();
		user.setProject(project);

		// Create entities for external processes
		for (final Step step : Step.values()) {
			if (!step.getProcesses().isEmpty()) {
				var exec = new ExecutionStepEntity();

				for (final var processStep : step.getProcesses()) {
					exec.putExternalProcess(processStep, new ExternalProcessEntity());
				}

				project.putExecutionStep(step, exec);
			}
		}

		userRepository.save(user);

		return project;
	}

	/**
	 * Returns the project of the user.
	 * Creates a new project, if the user does not have one.
	 * TODO: Add projectId parameter if multiple projects are supported
	 *
	 * @param user The user of the project.
	 * @return The project.
	 */
	@Transactional
	public ProjectEntity getProject(final UserEntity user) {
		if (!hasProject(user)) {
			throw new RuntimeException("No project");
		}

		final UserEntity user2 = userRepository.findById(user.getEmail()).get();
		return user2.getProject();
	}

	@Transactional
	public void setMode(final ProjectEntity project, final Mode mode) {
		project.getStatus().setMode(mode);
		project.getStatus().setCurrentStep(Step.UPLOAD);
		userRepository.save(project.getUser());
	}

	/**
	 * Writes a ZIP to the given OutputStream containing the data set and data configuration of the given project and the given configuration.
	 * @param project The project of the data set.
	 * @param outputStream The OutputStream to write to.
	 * @throws BadColumnNameException If the data set does not contain a column with the given names.
	 * @throws BadDataSetIdException If no DataConfiguration is associated with the given project.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException If the request body could not be created.
	 */
	@Transactional
	public void createZipFile(final ProjectEntity project, final OutputStream outputStream)
			throws BadColumnNameException, BadDataSetIdException, InternalDataSetPersistenceException, InternalIOException {
		try (final ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
			final DataSet dataSet = databaseService.exportDataSet(project, new ArrayList<>(), Step.VALIDATION);

			// Add data configuration
			final ZipEntry attributeConfigZipEntry = new ZipEntry("attribute_config.yaml");
			zipOut.putNextEntry(attributeConfigZipEntry);
			yamlMapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
			          .writeValue(zipOut, dataSet.getDataConfiguration());
			zipOut.closeEntry();

			// Add configuration
			for (final var configurationEntry : project.getConfigurations().entrySet()) {
				final ZipEntry configZipEntry = new ZipEntry(configurationEntry.getKey() + ".yaml");
				zipOut.putNextEntry(configZipEntry);
				zipOut.write(configurationEntry.getValue().getBytes());
				zipOut.closeEntry();
			}

			// Add data set
			addCsvToZip(zipOut, dataSet, "original");

			// Add results
			for (final ExecutionStepEntity executionStep : project.getExecutions().values()) {
				for (final ExternalProcessEntity externalProcess : executionStep.getProcesses().values()) {
					if (externalProcess.getResultDataSet() != null) {
						final ZipEntry resultZipEntry = new ZipEntry(externalProcess.getStep().name() + "-result.csv");
						zipOut.putNextEntry(resultZipEntry);
						zipOut.write(externalProcess.getResultDataSet());
						zipOut.closeEntry();
					}

					for (final var entry : externalProcess.getAdditionalResultFiles().entrySet()) {
						final ZipEntry additionalFileEntry = new ZipEntry(entry.getKey());
						zipOut.putNextEntry(additionalFileEntry);
						zipOut.write(entry.getValue());
						zipOut.closeEntry();
					}
				}
			}

			zipOut.finish();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION,
			                              "Failed to create the ZIP file for starting an external process!", e);
		}
	}

	private void addCsvToZip(final ZipOutputStream zipOut, final DataSet dataSet, final String name) throws IOException {
		final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(zipOut, StandardCharsets.UTF_8);
		final CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(
				dataSet.getDataConfiguration().getColumnNames().toArray(new String[0])).build();

		final ZipEntry dataZipEntry = new ZipEntry(name + ".csv");
		zipOut.putNextEntry(dataZipEntry);

		final CSVPrinter csvPrinter = new CSVPrinter(outputStreamWriter, csvFormat);
		for (final DataRow dataRow : dataSet.getDataRows()) {
			csvPrinter.printRecord(dataRow.getRow());
		}
		csvPrinter.flush();

		zipOut.closeEntry();
	}
}
