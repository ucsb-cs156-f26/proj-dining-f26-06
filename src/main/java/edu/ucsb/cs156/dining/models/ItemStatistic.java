package edu.ucsb.cs156.dining.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated rating statistics for a single menu item, used by the statistics endpoints. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemStatistic {
  private Long itemId;
  private String itemName;
  private String diningCommonsCode;
  private String mealCode;
  private String station;
  private Double averageStars;
  private Long reviewCount;

  /**
   * A handful of the most recent non-blank reviewer comments for this item, most recent first. See
   * {@link edu.ucsb.cs156.dining.controllers.StatisticsController#MAX_COMMENTS_PER_ITEM} for the
   * cap.
   */
  @Builder.Default private List<String> comments = List.of();
}
