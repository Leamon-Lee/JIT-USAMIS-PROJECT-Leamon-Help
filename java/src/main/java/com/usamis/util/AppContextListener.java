package com.usamis.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WHY a ServletContextListener for DB init:
 * Initialising the connection pool in a servlet's init() means the pool
 * isn't ready until the first HTTP request hits that servlet.
 * A context listener runs at application startup — pool is warm before
 * any request arrives, preventing the cold-start slowness.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("═══════════════════════════════════════");
        log.info(" USAMIS v1.0 — Jinling Institute of Technology");
        log.info(" Starting up...");
        log.info("═══════════════════════════════════════");

        try {
            DatabaseConnection.init();
            log.info("✔ Database pool initialized");
        } catch (Exception e) {
            log.error("✘ Database initialization failed — application may not work correctly", e);
            // Don't throw — let the app start so health endpoint reports the issue
        }

        log.info("✔ USAMIS application started successfully");
        log.info("  Context path: {}", sce.getServletContext().getContextPath());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("USAMIS shutting down...");
        DatabaseConnection.close();
        log.info("✔ Database pool closed. Goodbye.");
    }
}
