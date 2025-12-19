package com.example.infra;

import com.example.infra.config.EnvironmentConfig;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;

/**
 * Entry point de la aplicación CDK.
 */
public class TicketeroApp {
    
    public static void main(final String[] args) {
        App app = new App();
        
        String account = System.getenv("CDK_DEFAULT_ACCOUNT");
        String region = System.getenv("CDK_DEFAULT_REGION");
        
        if (account == null || region == null) {
            account = (String) app.getNode().tryGetContext("accountId");
            region = (String) app.getNode().tryGetContext("region");
        }
        
        Environment awsEnv = Environment.builder()
            .account(account)
            .region(region)
            .build();
        
        // Stack Dev
        EnvironmentConfig devConfig = EnvironmentConfig.dev();
        TicketeroStack devStack = new TicketeroStack(app, "ticketero-dev", 
            StackProps.builder()
                .env(awsEnv)
                .description("Ticketero - Development")
                .build(), 
            devConfig);
        applyTags(devStack, devConfig);
        
        // Stack Prod
        EnvironmentConfig prodConfig = EnvironmentConfig.prod();
        TicketeroStack prodStack = new TicketeroStack(app, "ticketero-prod", 
            StackProps.builder()
                .env(awsEnv)
                .description("Ticketero - Production")
                .build(), 
            prodConfig);
        applyTags(prodStack, prodConfig);
        
        app.synth();
    }
    
    private static void applyTags(TicketeroStack stack, EnvironmentConfig config) {
        Tags.of(stack).add("Environment", config.envName());
        Tags.of(stack).add("Project", "Ticketero");
        Tags.of(stack).add("ManagedBy", "CDK");
        Tags.of(stack).add("CostCenter", "ticketero-" + config.envName());
    }
}