package com.example.infra.constructs;

import com.example.infra.config.EnvironmentConfig;
import software.amazon.awscdk.services.amazonmq.*;
import software.amazon.awscdk.services.secretsmanager.*;
import software.constructs.Construct;
import java.util.List;

/**
 * Amazon MQ RabbitMQ y secrets para Telegram.
 */
public class MessagingConstruct extends Construct {
    
    private final CfnBroker mqBroker;
    private final Secret mqCredentials;
    private final Secret telegramSecret;
    
    public MessagingConstruct(final Construct scope, final String id,
                               final EnvironmentConfig config,
                               final NetworkingConstruct networking) {
        super(scope, id);
        
        // Credenciales MQ auto-generadas
        this.mqCredentials = Secret.Builder.create(this, "MqCredentials")
            .secretName(config.resourceName("mq-credentials"))
            .description("Amazon MQ Credentials")
            .generateSecretString(SecretStringGenerator.builder()
                .secretStringTemplate("{\"username\": \"ticketero\"}")
                .generateStringKey("password")
                .excludePunctuation(true)
                .excludeCharacters("/@\"\\")
                .passwordLength(32)
                .build())
            .build();
        
        // Amazon MQ Broker
        this.mqBroker = CfnBroker.Builder.create(this, "MqBroker")
            .brokerName(config.resourceName("mq"))
            .engineType("RABBITMQ")
            .engineVersion("3.11.20")
            .hostInstanceType(config.mqInstanceType())
            .deploymentMode("SINGLE_INSTANCE")
            .publiclyAccessible(false)
            .autoMinorVersionUpgrade(true)
            .subnetIds(List.of(
                networking.getVpc().getPrivateSubnets().get(0).getSubnetId()
            ))
            .securityGroups(List.of(
                networking.getMqSg().getSecurityGroupId()
            ))
            .users(List.of(
                CfnBroker.UserProperty.builder()
                    .username("ticketero")
                    .password(mqCredentials.secretValueFromJson("password").unsafeUnwrap())
                    .build()
            ))
            .logs(CfnBroker.LogListProperty.builder()
                .general(true)
                .build())
            .build();
        
        // Secret Telegram (placeholder)
        this.telegramSecret = Secret.Builder.create(this, "TelegramSecret")
            .secretName(config.resourceName("telegram"))
            .description("Telegram Bot Token - UPDATE AFTER DEPLOY")
            .generateSecretString(SecretStringGenerator.builder()
                .secretStringTemplate("{\"token\": \"PLACEHOLDER\"}")
                .generateStringKey("dummy")
                .build())
            .build();
    }
    
    public String getMqEndpoint() {
        return mqBroker.getAttrAmqpEndpoints().toString();
    }
    
    public Secret getMqCredentials() { return mqCredentials; }
    public Secret getTelegramSecret() { return telegramSecret; }
    public CfnBroker getMqBroker() { return mqBroker; }
}