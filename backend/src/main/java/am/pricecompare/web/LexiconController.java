package am.pricecompare.web;

import am.pricecompare.lexicon.GroceryLexicon;
import am.pricecompare.service.SmartListService;
import am.pricecompare.web.dto.SmartListDtos.ConceptDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lexicon")
@Validated
@Tag(name = "Lexicon", description = "Grocery words in Armenian, Latin-letter Armenian, Russian and English")
public class LexiconController {

    private final SmartListService smart;
    private final GroceryLexicon lexicon;

    public LexiconController(SmartListService smart, GroceryLexicon lexicon) {
        this.smart = smart;
        this.lexicon = lexicon;
    }

    @GetMapping("/suggest")
    @Operation(summary = "Concept suggestions while typing (local dictionary, no external services)")
    public List<ConceptDto> suggest(@RequestParam @Size(max = 100) String q, @RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {
        return smart.suggest(q, limit);
    }

    @GetMapping("/concepts")
    @Operation(summary = "All known grocery concepts")
    public List<ConceptDto> concepts() {
        return lexicon.all().stream().map(smart::conceptDto).toList();
    }

    @GetMapping("/concepts/{code}")
    @Operation(summary = "One concept with its choices and sizes")
    public ConceptDto concept(@PathVariable String code) {
        return lexicon.get(code).map(smart::conceptDto).orElseThrow(() -> new NotFoundException("Concept " + code + " not found"));
    }
}
