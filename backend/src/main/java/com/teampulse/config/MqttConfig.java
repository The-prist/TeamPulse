package com.teampulse.config;

import com.teampulse.module.attendance.config.MqttProperties;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.integration.core.MessageProducer;
import org.springframework.util.StringUtils;

/**
 * MQTT 接入配置（spring-integration-mqtt + Eclipse Paho）。
 *
 * <p>为什么用 Spring Integration 而不是直接 new 一个 Paho client：
 * <ul>
 *   <li>入站适配器自带断线重连、QoS 会话保持、消息转 Channel，业务代码只处理 payload</li>
 *   <li>出站 Gateway 让「发回执」变成一次 {@code channel.send(msg)}，与业务事务解耦</li>
 *   <li>QoS=1 + cleanSession=false：设备离线期间的消息在重连后补投，打卡不漏记</li>
 * </ul>
 *
 * <p>注意入站适配器 {@code autoStartup=false}：由 {@code MqttLifecycleManager} 手动启动。
 * 这样 broker 暂时不可用时应用仍能起来（Web 接口照常可用），只是实时打卡链路降级。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "teampulse.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    private final MqttProperties properties;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{properties.getBrokerUrl()});
        if (StringUtils.hasText(properties.getUsername())) {
            options.setUserName(properties.getUsername());
            options.setPassword(properties.getPassword().toCharArray());
        }
        options.setCleanSession(properties.isCleanSession());
        options.setKeepAliveInterval(properties.getKeepAliveSeconds());
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        options.setAutomaticReconnect(true);
        options.setMaxInflight(50);
        return options;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(MqttConnectOptions options) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    /** 入站通道：适配器 → 业务处理器 */
    @Bean(name = "mqttInboundChannel")
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    /** 出站通道：业务 → 下行回执 */
    @Bean(name = "mqttOutboundChannel")
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean(name = "mqttInboundAdapter")
    public MessageProducer mqttInboundAdapter(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                properties.getClientId(), factory,
                properties.getTopicCheckin(), properties.getTopicHeartbeat());
        adapter.setCompletionTimeout(properties.getCompletionTimeoutMs());
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(properties.getQos());
        adapter.setOutputChannel(mqttInboundChannel());
        // 断线重连由 MqttConnectOptions#setAutomaticReconnect(true) 负责
        // （Spring Integration 6.x 起移除了适配器上的 setRecoveryInterval）
        // 交给 MqttLifecycleManager 启动，避免 broker 不可用时应用启动失败
        adapter.setAutoStartup(false);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundHandler(MqttPahoClientFactory factory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(properties.getClientId() + "-pub", factory);
        handler.setAsync(true);
        handler.setAsyncEvents(true);
        handler.setDefaultQos(properties.getQos());
        handler.setDefaultTopic(properties.getTopicAck());
        return handler;
    }
}
