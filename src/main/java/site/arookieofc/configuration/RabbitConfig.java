package site.arookieofc.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String DELAY_EXCHANGE = "activity.delayed.exchange";
    public static final String UPDATE_EXCHANGE = "activity.update.exchange";
    public static final String UPDATE_QUEUE = "activity.update.queue";
    public static final String DELAY_ROUTING_KEY = "activity.delay";
    public static final String UPDATE_ROUTING_KEY = "activity.update";

    public static final String MONITORING_EXCHANGE = "monitoring.exchange";
    public static final String MONITORING_CLEANUP_QUEUE = "monitoring.cleanup.queue";
    public static final String MONITORING_CLEANUP_ROUTING_KEY = "monitoring.cleanup";

    @Bean
    public CustomExchange delayExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(DELAY_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    public DirectExchange updateExchange() {
        return new DirectExchange(UPDATE_EXCHANGE);
    }

    @Bean
    public DirectExchange monitoringExchange() {
        return new DirectExchange(MONITORING_EXCHANGE);
    }


    @Bean
    public Queue updateQueue() {
        return new Queue(UPDATE_QUEUE, true);
    }

    @Bean
    public Queue monitoringCleanupQueue() {
        return new Queue(MONITORING_CLEANUP_QUEUE, true);
    }

    @Bean
    public Binding bindDelay() {
        return BindingBuilder.bind(updateQueue()).to(delayExchange()).with(DELAY_ROUTING_KEY).noargs();
    }

    @Bean
    public Binding bindUpdate() {
        return BindingBuilder.bind(updateQueue()).to(updateExchange()).with(UPDATE_ROUTING_KEY);
    }

    @Bean
    public Binding bindMonitoringCleanup() {
        return BindingBuilder.bind(monitoringCleanupQueue()).to(monitoringExchange()).with(MONITORING_CLEANUP_ROUTING_KEY);
    }
}
