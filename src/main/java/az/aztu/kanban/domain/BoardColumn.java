package az.aztu.kanban.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "board_column")
@Getter
@Setter
public class BoardColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int position = 0;

    /** Null or 0 means "no work in progress limit". */
    @Column(name = "wip_limit")
    private Integer wipLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ColumnCategory category = ColumnCategory.TODO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @OneToMany(mappedBy = "boardColumn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<Task> tasks = new ArrayList<>();
}
