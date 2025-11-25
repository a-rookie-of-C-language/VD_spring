package site.arookieofc.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String DELAY_EXCHANGE = "activity.delay.exchange";
    public static final String UPDATE_EXCHANGE = "activity.update.exchange";
    public static final String DELAY_QUEUE = "activity.delay.queue";
    public static final String UPDATE_QUEUE = "activity.update.queue";
    public static final String DELAY_ROUTING_KEY = "activity.delay";
    public static final String UPDATE_ROUTING_KEY = "activity.update";

    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE);
    }

    @Bean
    public DirectExchange updateExchange() {
        return new DirectExchange(UPDATE_EXCHANGE);
    }

    @Bean
    public Queue delayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", UPDATE_EXCHANGE);
        args.put("x-dead-letter-routing-key", UPDATE_ROUTING_KEY);
        return new Queue(DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue updateQueue() {
        return new Queue(UPDATE_QUEUE, true);
    }

    @Bean
    public Binding bindDelay() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding bindUpdate() {
        return BindingBuilder.bind(updateQueue()).to(updateExchange()).with(UPDATE_ROUTING_KEY);
    }
}
