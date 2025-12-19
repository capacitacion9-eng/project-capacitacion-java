package com.example.infra.constructs;

import com.example.infra.config.EnvironmentConfig;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;
import java.util.List;

/**
 * VPC, Subnets y Security Groups.
 */
public class NetworkingConstruct extends Construct {
    
    private final Vpc vpc;
    private final SecurityGroup albSg;
    private final SecurityGroup ecsSg;
    private final SecurityGroup rdsSg;
    private final SecurityGroup mqSg;
    
    public NetworkingConstruct(final Construct scope, final String id, 
                                final EnvironmentConfig config) {
        super(scope, id);
        
        // VPC
        this.vpc = Vpc.Builder.create(this, "Vpc")
            .vpcName(config.resourceName("vpc"))
            .ipAddresses(IpAddresses.cidr(config.vpcCidr()))
            .maxAzs(2)
            .natGateways(config.natGateways())
            .subnetConfiguration(List.of(
                SubnetConfiguration.builder()
                    .name("Public")
                    .subnetType(SubnetType.PUBLIC)
                    .cidrMask(24)
                    .build(),
                SubnetConfiguration.builder()
                    .name("Private")
                    .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                    .cidrMask(24)
                    .build()
            ))
            .build();
        
        // Security Group - ALB
        this.albSg = SecurityGroup.Builder.create(this, "AlbSg")
            .vpc(vpc)
            .securityGroupName(config.resourceName("alb-sg"))
            .description("ALB Security Group")
            .allowAllOutbound(true)
            .build();
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "HTTP");
        albSg.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "HTTPS");
        
        // Security Group - ECS
        this.ecsSg = SecurityGroup.Builder.create(this, "EcsSg")
            .vpc(vpc)
            .securityGroupName(config.resourceName("ecs-sg"))
            .description("ECS Tasks Security Group")
            .allowAllOutbound(true)
            .build();
        ecsSg.addIngressRule(albSg, Port.tcp(8080), "From ALB");
        
        // Security Group - RDS
        this.rdsSg = SecurityGroup.Builder.create(this, "RdsSg")
            .vpc(vpc)
            .securityGroupName(config.resourceName("rds-sg"))
            .description("RDS Security Group")
            .allowAllOutbound(false)
            .build();
        rdsSg.addIngressRule(ecsSg, Port.tcp(5432), "PostgreSQL from ECS");
        
        // Security Group - Amazon MQ
        this.mqSg = SecurityGroup.Builder.create(this, "MqSg")
            .vpc(vpc)
            .securityGroupName(config.resourceName("mq-sg"))
            .description("Amazon MQ Security Group")
            .allowAllOutbound(false)
            .build();
        mqSg.addIngressRule(ecsSg, Port.tcp(5671), "AMQPS from ECS");
        mqSg.addIngressRule(ecsSg, Port.tcp(443), "HTTPS Console");
    }
    
    public Vpc getVpc() { return vpc; }
    public SecurityGroup getAlbSg() { return albSg; }
    public SecurityGroup getEcsSg() { return ecsSg; }
    public SecurityGroup getRdsSg() { return rdsSg; }
    public SecurityGroup getMqSg() { return mqSg; }
}