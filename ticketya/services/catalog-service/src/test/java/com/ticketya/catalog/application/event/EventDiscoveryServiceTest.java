package com.ticketya.catalog.application.event;

import com.ticketya.catalog.api.error.ApiProblemException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventDiscoveryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EventDiscoveryService service = new EventDiscoveryService(jdbc);

    @Test
    void defaultsToDateAscendingAndCalculatesPaginationMetadata() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        var page = service.search(criteria(null, null, 1, 2, null, null));

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("ORDER BY  e.starts_at ASC, e.id ASC"),
                any(RowMapper.class), any(Object[].class));
    }

    @Test
    void appliesRequestedPriceDirectionAndStableIdTieBreak() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.search(criteria(null, null, 0, 10, " price ", " DESC "));

        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("ORDER BY  minimum_price DESC NULLS LAST, e.id ASC"),
                any(RowMapper.class), any(Object[].class));
    }

    @Test
    void relevanceDefaultsToDescendingAndRequiresNonblankQuery() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.search(criteria(null, " festival ", 0, 10, "relevance", null));

        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("ORDER BY  relevance_score DESC, e.id ASC"),
                any(RowMapper.class), any(Object[].class));
        assertThatThrownBy(() -> service.search(criteria(null, "  ", 0, 10, "relevance", null)))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("q is required");
    }

    @Test
    void rejectsInvalidPageSizeRangeAndSortOptionsBeforeQuerying() {
        assertBad(criteria(null, null, -1, 10, null, null), "page must be zero or greater");
        assertBad(criteria(null, null, 0, 0, null, null), "size must be between 1 and 50");
        assertBad(criteria(null, null, 0, 51, null, null), "size must be between 1 and 50");
        assertBad(criteria(null, null, 0, 10, "rating", null), "sort must be date, price, or relevance");
        assertBad(criteria(null, null, 0, 10, "date", "sideways"), "direction must be asc or desc");
        assertThatThrownBy(() -> service.search(new EventDiscoveryService.SearchCriteria(null, null, null, null,
                java.time.OffsetDateTime.parse("2030-06-12T00:00:00Z"),
                java.time.OffsetDateTime.parse("2030-06-11T00:00:00Z"), null, 0, 10, null, null)))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("from must be before or equal to to");
    }

    private void assertBad(EventDiscoveryService.SearchCriteria criteria, String detail) {
        assertThatThrownBy(() -> service.search(criteria))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining(detail);
    }

    private EventDiscoveryService.SearchCriteria criteria(UUID organizer, String query, Integer page, Integer size,
                                                           String sort, String direction) {
        return new EventDiscoveryService.SearchCriteria(null, organizer, null, null, null, null, query,
                page, size, sort, direction);
    }
}
