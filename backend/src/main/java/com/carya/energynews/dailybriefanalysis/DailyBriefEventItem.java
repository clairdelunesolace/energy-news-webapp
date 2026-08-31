package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBriefItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
        name = "daily_brief_event_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_brief_event_items_event_item",
                        columnNames = {"event_id", "daily_brief_item_id"}
                ),
                @UniqueConstraint(
                        name = "uk_daily_brief_event_items_event_rank",
                        columnNames = {"event_id", "support_rank"}
                )
        }
)
public class DailyBriefEventItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_brief_event_items_event")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DailyBriefEvent event;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "daily_brief_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_brief_event_items_item")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DailyBriefItem dailyBriefItem;

    @Column(name = "support_rank", nullable = false)
    private int supportRank;

    protected DailyBriefEventItem() {
    }

    public DailyBriefEventItem(
            DailyBriefEvent event,
            DailyBriefItem dailyBriefItem,
            int supportRank
    ) {
        this.event = event;
        this.dailyBriefItem = dailyBriefItem;
        this.supportRank = supportRank;
    }

    public Long getId() {
        return id;
    }

    public DailyBriefEvent getEvent() {
        return event;
    }

    public DailyBriefItem getDailyBriefItem() {
        return dailyBriefItem;
    }

    public int getSupportRank() {
        return supportRank;
    }
}
