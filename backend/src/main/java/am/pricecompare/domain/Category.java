package am.pricecompare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String nameEn;
    private String slug;
    private String icon;
    private int sortOrder;
    @ManyToOne(fetch = FetchType.LAZY)
    private Category parent;
    private boolean active = true;
}
