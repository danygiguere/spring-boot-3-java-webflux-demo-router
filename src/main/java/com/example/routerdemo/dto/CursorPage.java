package com.example.routerdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

@Schema(description = "A page of a keyset-paginated list")
public record CursorPage<T>(
        List<T> data,
        @Schema(description = "Send as `cursor` to get the next page; null on the last page", nullable = true)
                Long nextCursor) {

    /** {@code rows} was fetched with {@code limit + 1}: the extra row only tells that another page exists. */
    public static <T> CursorPage<T> of(List<T> rows, int limit, Function<T, Long> id) {
        if (rows.size() <= limit) {
            return new CursorPage<>(rows, null);
        }
        List<T> data = List.copyOf(rows.subList(0, limit));
        return new CursorPage<>(data, id.apply(data.get(limit - 1)));
    }
}
