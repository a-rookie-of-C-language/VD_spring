package site.arookieofc.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String UPDATE_EXCHANGE = "activity.update.exchange";
    public static final String UPDATE_RETRY_EXCHANGE = "activity.update.retry.exchange";
    public static final String UPDATE_DLX_EXCHANGE = "activity.update.dlx.exchange";
    public static final String UPDATE_QUEUE = "activity.update.v2.queue";
    public static final String UPDATE_RETRY_QUEUE = "activity.update.v2.retry.queue";
    public static final String UPDATE_DLX_QUEUE = "activity.update.v2.dlq";
    public static final String DELAY_ROUTING_KEY = "activity.delay";
    public static final String UPDATE_ROUTING_KEY = "activity.update";
    public static final String UPDATE_RETRY_ROUTING_KEY = "activity.update.retry";
    public static final String UPDATE_DLX_ROUTING_KEY = "activity.update.dlq";

    public static final String MONITORING_EXCHANGE = "monitoring.exchange";
    public static final String MONITORING_CLEANUP_QUEUE = "monitoring.cleanup.queue";
    public static final String MONITORING_CLEANUP_ROUTING_KEY = "monitoring.cleanup";

    @Bean
    public DirectExchange updateExchange() {
        return new DirectExchange(UPDATE_EXCHANGE);
    }

    @Bean
    public DirectExchange updateRetryExchange() {
        return new DirectExchange(UPDATE_RETRY_EXCHANGE);
    }

    @Bean
    public DirectExchange updateDlxExchange() {
        return new DirectExchange(UPDATE_DLX_EXCHANGE);
    }

    @Bean
    public DirectExchange monitoringExchange() {
        return new DirectExchange(MONITORING_EXCHANGE);
    }


    @Bean
    public Queue updateQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", UPDATE_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", UPDATE_DLX_ROUTING_KEY);
        return new Queue(UPDATE_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue updateRetryQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", UPDATE_EXCHANGE);
        args.put("x-dead-letter-routing-key", UPDATE_ROUTING_KEY);
        return new Queue(UPDATE_RETRY_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue updateDlxQueue() {
        return new Queue(UPDATE_DLX_QUEUE, true);
    }

    @Bean
    public Queue monitoringCleanupQueue() {
        return new Queue(MONITORING_CLEANUP_QUEUE, true);
    }

    @Bean
    public Binding bindUpdate() {
        return BindingBuilder.bind(updateQueue()).to(updateExchange()).with(UPDATE_ROUTING_KEY);
    }

    @Bean
    public Binding bindUpdateRetry() {
        return BindingBuilder.bind(updateRetryQueue()).to(updateRetryExchange()).with(UPDATE_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding bindUpdateDlx() {
        return BindingBuilder.bind(updateDlxQueue()).to(updateDlxExchange()).with(UPDATE_DLX_ROUTING_KEY);
    }

    @Bean
    public Binding bindMonitoringCleanup() {
        return BindingBuilder.bind(monitoringCleanupQueue()).to(monitoringExchange()).with(MONITORING_CLEANUP_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
