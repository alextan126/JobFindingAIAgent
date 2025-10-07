package com.example.model;
import java.time.Instant;
public record JobPost(String url, String platform, String title, String company,
                      String location, String applyUrl, String descriptionText,
                      Instant scrapedAt, Integer httpStatus) {}