package sg.edu.nus.cats.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Shared infrastructure beans. The clock is injectable so that every
 * business rule that depends on "now" stays testable.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AppConfig {

    @Bean
    public Clock clock(@Value("${cats.time.zone:Asia/Singapore}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }

    @Bean
    public ZoneId businessZone(@Value("${cats.time.zone:Asia/Singapore}") String timeZone) {
        return ZoneId.of(timeZone);
    }
}
