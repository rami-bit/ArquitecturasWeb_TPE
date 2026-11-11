# Spec Delta

## ADDED Requirements

### Requirement: Preserve sector identifiers across purchase selection
The public published event detail SHALL identify each sector with its stable source sector ID, matching the sector identifier used by Inventory availability and reservation contracts.

#### Scenario: Select a sector for purchase
- **GIVEN** a published event whose sector is visible in both Catalog detail and Inventory availability
- **WHEN** the client submits the sector ID from the published event detail to Purchase
- **THEN** Purchase and Inventory refer to the same sector without name-based translation

#### Scenario: Preserve sector identity across catalog changes
- **GIVEN** a published event snapshot and a later edit to its venue template
- **WHEN** a client reads the published event detail and Inventory availability
- **THEN** both continue to expose the original source sector ID for that event
