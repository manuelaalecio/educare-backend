package com.manuelaalecio.educare_backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API contract versioned in {@code openapi/educare-api.json} (spec api-contract): normalizes the generated
 * document, compares it with the committed file and rewrites the file in the update mode
 * ({@code ./gradlew updateApiContract}).
 */
final class ApiContract {

	static final Path CONTRACT_FILE = Path.of("openapi", "educare-api.json");

	static final String UPDATE_PROPERTY = "educare.apiContract.update";

	static final String OUTDATED_MESSAGE = "Contrato da API desatualizado: rode ./gradlew updateApiContract e commite "
			+ "openapi/educare-api.json";

	private static final DefaultIndenter INDENTER = new DefaultIndenter("  ", "\n");

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, SerializationFeature.INDENT_OUTPUT)
		.defaultPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(INDENTER).withArrayIndenter(INDENTER))
		.build();

	private ApiContract() {
	}

	static boolean isUpdateMode() {
		return Boolean.getBoolean(UPDATE_PROPERTY);
	}

	/** Sorted keys, 2-space indentation and a final line break, whatever the order springdoc writes. */
	static String normalize(String json) {
		Object document = MAPPER.readValue(json, Object.class);
		return MAPPER.writeValueAsString(document) + "\n";
	}

	/** Fails, pointing to the update command, when the file is missing or differs from the generated contract. */
	static void verify(Path file, String generated) {
		if (!Files.exists(file)) {
			fail(OUTDATED_MESSAGE + " (arquivo ausente: " + file + ")");
		}
		assertThat(read(file)).as(OUTDATED_MESSAGE).isEqualTo(generated);
	}

	static void write(Path file, String generated) {
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(file, generated, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
