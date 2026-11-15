package org.example.districtpulse.source;

/** Request body for create/update. On update, null fields are left unchanged. username/password are optional. */
public record SourceInput(
        String name,
        String url,
        String description,
        String category,
        String type,
        String coverage,
        String accessMethod,
        Boolean authRequired,
        String frequency,
        String username,
        String password) {
}
