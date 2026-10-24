package de.fisp.azubiprojekt.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(EmailEigenschaften.class)
public class EmailKonfiguration {

    /**
     * In application.properties stehen alle SMTP-Optionen mit leerem Standardwert (z. B. {@code ssl.trust}), damit sie
     * per Umgebungsvariable gesetzt werden koennen. Jakarta Mail wertet aber auch leere Werte aus – ein leeres
     * {@code ssl.trust} wuerde z. B. jedem Server misstrauen. Deshalb werden leere Optionen hier entfernt.
     */
    @Bean
    static BeanPostProcessor leereMailOptionenEntfernen() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof JavaMailSenderImpl sender) {
                    Properties optionen = sender.getJavaMailProperties();
                    optionen.stringPropertyNames().stream()
                            .filter(name -> optionen.getProperty(name).isBlank())
                            .forEach(optionen::remove);
                }
                return bean;
            }
        };
    }
}
