package com.ticketya.catalog.domain.event;

@io.swagger.v3.oas.annotations.media.Schema(description="Lifecycle state. SOLD_OUT, CANCELLED and FINISHED are reserved for future workflows.", allowableValues={"DRAFT","PUBLISHED","SUSPENDED","SOLD_OUT","CANCELLED","FINISHED"})
public enum EventStatus { DRAFT, PUBLISHED, SUSPENDED, SOLD_OUT, CANCELLED, FINISHED }
