package com.health_insurance.integration;

import java.util.HashMap;
import java.util.Map;

import com.health_insurance.phm_model.Trigger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;



/**
 * A simple Camel REST DSL route that implements the greetings service.
 * 
 */
@Component
public class CamelRouter extends RouteBuilder {

    @Value("${kie.process.container.id}") 
    String processContainerId;
    @Value("${kie.process.definition.id}") 
	String processDefinitionId;
    
    @Value("${kafka.topic:test}") 
	String kafkaTopic;
    @Value("${kafka.host:localhost}") 
	String kafkaHost;
    @Value("${kafka.port:9092}") 
	String kafkaPort;

    @Override
    public void configure() throws Exception {

        // @formatter:off
        restConfiguration()
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Integration Service REST API")
                .apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
                .apiProperty("base.path", "camel/")
                .apiProperty("api.path", "/")
                .apiProperty("host", "")
                .apiContextRouteId("doc-api")
            .component("servlet")
            .bindingMode(RestBindingMode.json);
        
        rest("/greetings").description("Greeting to {name}")
            .get("/{name}").outType(Greetings.class)
                .route().routeId("greeting-api")
                .to("direct:greetingsImpl");

        rest("/kieserver").description("Call Kie Server")
            .get("/listContainers")
                .route().routeId("kie-server-api")
                .to("direct:runKieCommand");

    // Direct routes
        from("direct:greetingsImpl").description("Greetings REST service implementation route")
            .streamCaching()
            .to("bean:greetingsService?method=getGreetings"); 
            
        from("direct:runKieCommand")
            .log("calling kie-server")
            .to("bean:businessAutomationServiceClient?method=listContainers")
            .log("${body}");
            //.to("direct:publishToKafka");
        
        from("direct:publishToKafka")
            .log("publishing ${body} to kafka topic}")
            .setHeader(KafkaConstants.KEY, constant("Camel")) // Key of the message
            .toF("kafka:%s?brokers=%s:%s", kafkaTopic, kafkaHost, kafkaPort);
            
        fromF("kafka:%s?brokers=%s:%s", kafkaTopic, kafkaHost, kafkaPort)
            .log("Message received from Kafka : ${body}")
            .log("    on the topic ${headers[kafka.TOPIC]}")
            .log("    on the partition ${headers[kafka.PARTITION]}")
            .log("    with the offset ${headers[kafka.OFFSET]}")
            .log("    with the key ${headers[kafka.KEY]}");  
        
        from("direct:startProcess")
            .process(e -> {
                Trigger trigger = e.getIn().getBody(Trigger.class);

                Map<String, Object> processVariables = new HashMap<>();
                processVariables.put("pTriggerId", trigger.getOriginalTriggerId());
                processVariables.put("pMemberId", trigger.getMemberId());

                e.getIn().setBody(processVariables);
            }) // start a new Process instance
            .toF("bean:businessAutomationServiceClient?method=startProcess(%s, %s, ${body})", processContainerId, processDefinitionId)
            .log("a process instance has been created with Id ${body}");
        
        // @formatter:on
    }

}