package hr.algebra.sitelog.notification.domain;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores the arguments of a notification body in one {@code varchar} column
 * ({@code notification.body_args}, V7), separated by the ASCII unit separator U+001F - a
 * control character that does not occur in names, codes or dates. {@link #clean} removes it from
 * a value before it is stored, so a value can never be split in two. No arguments = null.
 *
 * <p>Chosen over a JSON column (needs a JSON mapper and a {@code jsonb} type for one list of
 * strings) and over a child table (one more join for the bell on every page).
 */
@Converter
public class MessageArgsConverter implements AttributeConverter<List<String>, String> {

	static final String SEPARATOR = "\u001F";

	@Override
	public String convertToDatabaseColumn(List<String> args) {
		if (args == null || args.isEmpty()) {
			return null;
		}
		return String.join(SEPARATOR, args.stream().map(MessageArgsConverter::clean).toList());
	}

	@Override
	public List<String> convertToEntityAttribute(String column) {
		if (column == null) {
			return List.of();
		}
		return Arrays.asList(column.split(SEPARATOR, -1));
	}

	/** The value as it can be stored: null becomes empty, the separator is removed. */
	public static String clean(Object value) {
		return value == null ? "" : String.valueOf(value).replace(SEPARATOR, "");
	}

}
