package com.example.model;
import java.time.Instant;
public record JobLink(String url, String hostType, String source, Instant discoveredAt) {}