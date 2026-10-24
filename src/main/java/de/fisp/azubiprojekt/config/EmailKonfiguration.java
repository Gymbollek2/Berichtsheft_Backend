package de.fisp.azubiprojekt.config;

import de.fisp.azubiprojekt.service.EmailVersand;
import de.fisp.azubiprojekt.service.GraphEmailVersand;
import de.fisp.azubiprojekt.service.SmtpEmailVersand;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Properties;

/** Waehlt den Versandweg ueber {@code app.mail.versandart}: smtp (Standard) oder graph. */
@Configuration
@EnableConfigurationProperties(EmailEigenschaften.class)
public class EmailKonfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.mail.versandart", havingValue = "smtp", matchIfMissing = true)
    EmailVersand smtpEmailVersand(JavaMailSender mailSender, EmailEigenschaften eigenschaften) {
        return new SmtpEmailVersand(mailSender, eigenschaften);
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.versandart", havingValue = "graph")
    EmailVersand graphEmailVersand(EmailEigenschaften eigenschaften) {
        Duration zeitlimit = Duration.ofMillis(eigenschaften.graph().timeoutMs());
        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(zeitlimit);
        http.setReadTimeout(zeitlimit);
        return new GraphEmailVersand(eigenschaften, RestClient.builder().requestFactory(http));
    }

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
