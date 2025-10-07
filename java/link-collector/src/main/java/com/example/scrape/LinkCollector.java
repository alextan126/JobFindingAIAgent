package com.example.scrape;

import java.net.URI;
import java.util.List;

public interface LinkCollector {
    List<URI> collect(String sourceUrl) throws Exception;
}