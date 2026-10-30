package hk.hkucs.memoryagent;

import static hk.hkucs.memoryagent.Models.*;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

@RestController
public class AgentController {
    private final MemoryStore store;
    private final MemoryAnalysis analysis;
    private final MemoryAgent agent;
    private final Validator validator;
    public AgentController(MemoryStore store, MemoryAnalysis analysis, MemoryAgent agent, Validator validator) {
        this.store=store; this.analysis=analysis; this.agent=agent; this.validator=validator;
    }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","ok","version","0.1.0"); }
    @PostMapping("/api/memories") public Memory save(@Valid @RequestBody MemoryInput input) { return store.save(input); }
    @PostMapping("/api/materials") public Memory upload(@RequestParam("file") MultipartFile file) throws Exception {
        String filename = Objects.toString(file.getOriginalFilename(), "材料.txt");
        if (!filename.toLowerCase(Locale.ROOT).matches(".*\\.(txt|md)$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"第一版支持 UTF-8 编码的 .txt 和 .md 文件");
        String content;
        try { content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(file.getBytes())).toString().replaceFirst("^\\uFEFF", ""); }
        catch (CharacterCodingException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请将文件转换为 UTF-8 编码"); }
        if (content.indexOf('\0') >= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件不是纯文本");
        String title = filename.replace('\\','/'); title = title.substring(title.lastIndexOf('/') + 1);
        MemoryInput input = new MemoryInput(null, title, "MATERIAL", null, content);
        if (!validator.validate(input).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"材料不能为空，文件名最多160字，正文最多24000字");
        return store.save(input);
    }
    @GetMapping("/api/memories") public List<Map<String,Object>> list() {
        return store.all().stream().limit(100).map(m -> {
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("id",m.id()); out.put("title",m.title()); out.put("kind",m.kind()); out.put("occurredOn",m.occurredOn());
            out.put("summary",m.summary()); out.put("status",m.status()); out.put("pinned",m.pinned()); return out;
        }).toList();
    }
    @GetMapping("/api/memories/{id}") public Memory get(@PathVariable String id) { return store.get(id); }
    @PostMapping("/api/memories/{id}/analyze") public Memory analyze(@PathVariable String id) { return analysis.analyze(id); }
    @PatchMapping("/api/memories/{id}/pin") public Map<String,String> pin(@PathVariable String id,@RequestBody Pin input) {
        store.pin(id,input.pinned()); return Map.of("status","ok");
    }
    @DeleteMapping("/api/memories/{id}") public Map<String,String> delete(@PathVariable String id) {
        store.delete(id); return Map.of("status","deleted");
    }
    @GetMapping("/api/profile") public List<Trait> profile() { return store.traits(); }
    @PatchMapping("/api/profile/{id}") public Map<String,String> state(@PathVariable String id,@Valid @RequestBody TraitState input) {
        store.traitState(id,input.status()); return Map.of("status",input.status());
    }
    @PostMapping("/api/chat") public ChatAnswer chat(@Valid @RequestBody ChatInput input) { return agent.chat(input); }
}
