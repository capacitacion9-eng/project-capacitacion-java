package com.example.infra.config;

import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;

/**
 * Configuración inmutable por ambiente para infraestructura Ticketero.
 */
public record EnvironmentConfig(
    String envName,
    String vpcCidr,
    int natGateways,
    int desiredCount,
    int minCapacity,
    int maxCapacity,
    int taskCpu,
    int taskMemory,
    InstanceClass dbInstanceClass,
    InstanceSize dbInstanceSize,
    int dbAllocatedStorage,
    boolean multiAz,
    String mqInstanceType,
    boolean enableAlarms,
    int logRetentionDays
) {
    /**
     * Configuración para ambiente de desarrollo.
     * Costo estimado: ~$110/mes
     */
    public static EnvironmentConfig dev() {
        return new EnvironmentConfig(
            "dev",
            "10.0.0.0/16",
            1,              // 1 NAT Gateway
            1,              // desired tasks
            1,              // min capacity
            2,              // max capacity
            512,            // CPU units
            1024,           // Memory MB
            InstanceClass.T3,
            InstanceSize.MICRO,
            20,             // GB storage
            false,          // no Multi-AZ
            "mq.t3.micro",
            false,          // no alarms
            7               // log retention days
        );
    }
    
    /**
     * Configuración para ambiente de producción.
     * Costo estimado: ~$210/mes
     */
    public static EnvironmentConfig prod() {
        return new EnvironmentConfig(
            "prod",
            "10.0.0.0/16",
            2,              // 2 NAT Gateways (HA)
            2,              // desired tasks
            2,              // min capacity
            4,              // max capacity
            512,            // CPU units
            1024,           // Memory MB
            InstanceClass.T3,
            InstanceSize.SMALL,
            50,             // GB storage
            true,           // Multi-AZ
            "mq.t3.micro",
            true,           // alarms enabled
            14              // log retention days
        );
    }
    
    public String resourceName(String baseName) {
        return "ticketero-" + envName + "-" + baseName;
    }
    
    public boolean isProd() {
        return "prod".equals(envName);
    }
}