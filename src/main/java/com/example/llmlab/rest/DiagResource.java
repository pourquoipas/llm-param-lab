package com.example.llmlab.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TEMP diagnostic — remove before Step 15. */
@Path("/api")
@ApplicationScoped
public class DiagResource {

    @Inject
    DataSource dataSource;

    @GET
    @Path("/diag")
    public Map<String, Object> diag() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jdbc.url", orNull(ConfigProvider.getConfig().getOptionalValue("quarkus.datasource.jdbc.url", String.class)));
        m.put("db-kind", orNull(ConfigProvider.getConfig().getOptionalValue("quarkus.datasource.db-kind", String.class)));
        m.put("liquibase.change-log", orNull(ConfigProvider.getConfig().getOptionalValue("quarkus.liquibase.change-log", String.class)));
        m.put("liquibase.migrate-at-start", orNull(ConfigProvider.getConfig().getOptionalValue("quarkus.liquibase.migrate-at-start", String.class)));

        // Try to open a real connection and list tables
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            m.put("connection", "OK");
            m.put("tables", tables);
        } catch (Exception e) {
            m.put("connection", "ERROR: " + e.getMessage());
        }
        return m;
    }

    private static String orNull(java.util.Optional<String> s) {
        return s.orElse("<null>");
    }
}
