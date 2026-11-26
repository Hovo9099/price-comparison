package am.pricecompare.web.dto;

public record CategoryDto(Long id, String name, String nameEn, String slug, String icon, long productCount) {
}
