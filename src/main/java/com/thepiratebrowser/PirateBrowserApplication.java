package com.thepiratebrowser;

import com.thepiratebrowser.config.ApplicationConfiguration;
import javafx.application.Application;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class PirateBrowserApplication {
    private static AnnotationConfigApplicationContext context;

    private PirateBrowserApplication() {
    }

    public static void main(String[] args) {
        context = new AnnotationConfigApplicationContext(ApplicationConfiguration.class);
        Runtime.getRuntime().addShutdownHook(new Thread(PirateBrowserApplication::closeContext));
        Application.launch(JavaFxApplication.class, args);
    }

    static AnnotationConfigApplicationContext context() {
        return context;
    }

    static synchronized void closeContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }
}
