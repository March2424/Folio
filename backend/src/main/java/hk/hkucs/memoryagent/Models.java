package hk.hkucs.memoryagent;

import jakarta.validation.constraints.*;
import java.util.List;

public final class Models {
    private Models() {}
    public record MemoryInput(
        @Pattern(regexp="[A-Za-z0-9_-]{1,80}") String id,
        @NotBlank @Size(max=160) String title,
        @NotBlank @Pattern(regexp="MATERIAL|BOOK|MUSIC|MOVIE|TEXT|VOICE|FOOTPRINT") String kind,
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String occurredOn,
        @NotBlank @Size(max=24000) String content) {}
    public record Memory(String id, String title, String kind, String occurredOn, String content,
                         String summary, boolean pinned, String status, long revision,
                         long createdAt, long updatedAt, long recalledAt) {}
    public record Trait(String id, String memoryId, String description, String basis, String evidence, String status) {}
    public record ChatInput(@Pattern(regexp="[A-Za-z0-9_-]{1,80}") String sessionId,
                            @NotBlank @Size(max=2000) String message) {}
    public record Source(String id, String title, String occurredOn, String excerpt) {}
    public record ChatAnswer(String sessionId, String answer, List<Source> sources) {}
    public record TraitState(@NotBlank @Pattern(regexp="CONFIRMED|REJECTED") String status) {}
    public record Pin(boolean pinned) {}
}
