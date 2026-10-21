package de.fisp.azubiprojekt.entity;

import de.fisp.azubiprojekt.util.KindEnum;
import jakarta.persistence.*;

/**
 * Ein Lernziel aus dem Ausbildungsrahmenplan. Lernziele bilden eine Hierarchie
 * (Richtlernziel → Groblernziel → Feinlernziel) ueber {@link #parentId}.
 */
@Entity
@Table(name = "lernziel")
public class Lernziel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KindEnum kind;

    /** Id des uebergeordneten Lernziels oder {@code null} fuer oberste Ebene. */
    private Long parentId;

    public Lernziel() {
    }

    public Lernziel(String title, String description, KindEnum kind, Long parentId) {
        this.title = title;
        this.description = description;
        this.kind = kind;
        this.parentId = parentId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public KindEnum getKind() {
        return kind;
    }

    public void setKind(KindEnum kind) {
        this.kind = kind;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
