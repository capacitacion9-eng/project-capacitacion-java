package com.example.infra;

import com.example.infra.config.EnvironmentConfig;
import com.example.infra.constructs.*;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

/**
 * Stack principal que orquesta todos los constructs.
 */
public class TicketeroStack extends Stack {
    
    public TicketeroStack(final Construct scope, final String id, 
                          final StackProps props, final EnvironmentConfig config) {
        super(scope, id, props);
        
        // 1. Networking
        NetworkingConstruct networking = new NetworkingConstruct(this, "Networking", config);
        
        // 2. Database
        DatabaseConstruct database = new DatabaseConstruct(this, "Database", config, networking);
        
        // 3. Messaging
        MessagingConstruct messaging = new MessagingConstruct(this, "Messaging", config, networking);
        
        // 4. Container
        ContainerConstruct container = new ContainerConstruct(this, "Container", 
            config, networking, database, messaging);
        
        // 5. Monitoring
        new MonitoringConstruct(this, "Monitoring", config, container, database);
        
        // Outputs
        CfnOutput.Builder.create(this, "LoadBalancerDNS")
            .value(container.getLoadBalancerDNS())
            .description("ALB DNS Name")
            .exportName(config.resourceName("alb-dns"))
            .build();
            
        CfnOutput.Builder.create(this, "EcrRepositoryUri")
            .value(container.getEcrRepositoryUri())
            .description("ECR Repository URI")
            .exportName(config.resourceName("ecr-uri"))
            .build();
            
        CfnOutput.Builder.create(this, "DatabaseEndpoint")
            .value(database.getEndpoint())
            .description("RDS Endpoint")
            .exportName(config.resourceName("db-endpoint"))
            .build();
            
        CfnOutput.Builder.create(this, "MQEndpoint")
            .value(messaging.getMqEndpoint())
            .description("Amazon MQ Endpoint")
            .exportName(config.resourceName("mq-endpoint"))
            .build();
    }
}