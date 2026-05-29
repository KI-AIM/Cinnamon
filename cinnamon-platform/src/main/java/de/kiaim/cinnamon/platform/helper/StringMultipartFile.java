package de.kiaim.cinnamon.platform.helper;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class StringMultipartFile implements MultipartFile {

	/**
	 * The content of the file.
	 */
	@Getter
	private final String content;

	/**
	 * The name of the file.
	 */
	private final String fileName;

	/**
	 * The content type of the file.
	 */
	private final MediaType contentType;

	public StringMultipartFile(final String content, final String fileName, final MediaType contentType) {
		this.content = content;
		this.fileName = fileName;
		this.contentType = contentType;
	}

	@Override
	public String getName() {
		return fileName;
	}

	@Override
	@Nullable
	public String getOriginalFilename() {
		return fileName;
	}

	@Override
	@Nullable
	public String getContentType() {
		return contentType.toString();
	}

	@Override
	public boolean isEmpty() {
		return content.isEmpty();
	}

	@Override
	public long getSize() {
		return content.length();
	}

	@Override
	public byte[] getBytes() throws IOException {
		return content.getBytes();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return new ByteArrayInputStream(content.getBytes());
	}

	@Override
	public void transferTo(final File dest) throws IOException, IllegalStateException {
		try (FileOutputStream outputStream = new FileOutputStream(dest)) {
			outputStream.write(content.getBytes());
		}
	}
}
