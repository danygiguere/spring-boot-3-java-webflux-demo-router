package com.example.routerdemo.router;

import com.example.routerdemo.dto.ImageResponse;
import com.example.routerdemo.dto.PostSummary;
import com.example.routerdemo.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** OpenAPI descriptions of the paged list responses; router functions carry no generic return types. */
public final class PageDocs {

    public static final String CURSOR =
            "`nextCursor` of the previous page; omit for the first page. Null `nextCursor` means the last page.";
    public static final String LIMIT = "Page size, 1 to 100";
    private static final String NEXT_CURSOR = "Send as `cursor` to get the next page; null on the last page";
    private static final String PAGE = "A page of a keyset-paginated list";

    private PageDocs() {}

    @Schema(name = "CursorPageUserResponse", description = PAGE)
    public record Users(List<UserResponse> data, @Schema(description = NEXT_CURSOR) Long nextCursor) {}

    @Schema(name = "CursorPagePostSummary", description = PAGE)
    public record Posts(List<PostSummary> data, @Schema(description = NEXT_CURSOR) Long nextCursor) {}

    @Schema(name = "CursorPageImageResponse", description = PAGE)
    public record Images(List<ImageResponse> data, @Schema(description = NEXT_CURSOR) Long nextCursor) {}
}
