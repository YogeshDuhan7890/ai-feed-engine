package com.yogesh.dto;

import lombok.*;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeedPageResponse {

    private List<FeedResponseDTO> feed;
    private Double nextCursor;
}