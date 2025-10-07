package com.example.model;

public record JobLead(
        String company,
        String role,
        String location,
        String applyUrl,
        String sourceUrl
) {}