package com.health_insurance.kie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.health_insurance.phm_model.Reminder;
import com.health_insurance.phm_model.Response;
import com.health_insurance.phm_model.Result;
import com.health_insurance.phm_model.Task;
import com.health_insurance.phm_model.TaskActorAssignment;
import com.health_insurance.phm_model.Trigger;

import org.kie.api.KieServices;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ExecutionResults;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieContainerResourceList;
import org.kie.server.api.model.KieServerInfo;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.definition.ProcessDefinition;
import org.kie.server.api.model.KieServiceResponse; 
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.RuleServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("businessAutomationServiceClient")
public class BusinessAutomationClient {
  private static final Logger LOG = LoggerFactory.getLogger(BusinessAutomationClient.class);

  @Value("${kie.server.url:http://localhost:8080/kie-server/services/rest/server}")
  String kieServerUrl;
  @Value("${kie.server.user:kieAdmin}")
  String kieServerUser;
  @Value("${kie.server.password}")
  String kieServerPassword;

  private static final MarshallingFormat FORMAT = MarshallingFormat.JSON;
  private static KieServicesConfiguration conf;
  private static KieServicesClient kieServicesClient;

  public BusinessAutomationClient() {
  }

  @PostConstruct
  public void initialize() {
    LOG.info("\n=== Initializing Kie Client ===\n");
    LOG.info("\t connecting to {}", kieServerUrl);
    conf = KieServicesFactory.newRestConfiguration(kieServerUrl, kieServerUser, kieServerPassword);

    // If you use custom classes, such as Obj.class, add them to the configuration.
    Set<Class<?>> extraClassList = new HashSet<Class<?>>();
    extraClassList.add(Task.class);
    extraClassList.add(Reminder.class);
    extraClassList.add(Result.class);
    extraClassList.add(TaskActorAssignment.class);
    extraClassList.add(Trigger.class);
    extraClassList.add(Response.class);
    conf.addExtraClasses(extraClassList);

    conf.setMarshallingFormat(FORMAT);
    kieServicesClient = KieServicesFactory.newKieServicesClient(conf);

    listCapabilities();

    LOG.info("=== Kie Client initialization done ===\n");
  }

  public List<String> listCapabilities() {
    KieServerInfo serverInfo = kieServicesClient.getServerInfo().getResult();
    LOG.info("Kie Server capabilities:");
    serverInfo.getCapabilities().forEach(c -> LOG.info("\t" + c));
    return serverInfo.getCapabilities();
  }

  public List<KieContainerResource> listContainers() {
    KieContainerResourceList containersList = kieServicesClient.listContainers().getResult();
    List<KieContainerResource> kieContainers = containersList.getContainers();
    LOG.info("Available containers: ");
    kieContainers.forEach(container ->
      LOG.info("\t" + container.getContainerId() + " (" + container.getReleaseId() + ")")
    );

    return kieContainers;
  }

  public List<ProcessDefinition> listProcesses() {
    LOG.info("== Listing Business Processes ==");
    QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
    List<ProcessDefinition> processDefinitions = queryClient.findProcessesByContainerId("rewards", 0, 1000);
    LOG.info("Available process: ");
    processDefinitions.forEach(def -> 
      LOG.info(def.getName() + " - " + def.getId() + " v" + def.getVersion())
    );

    return processDefinitions;
  }  

  /**
   * Execute a batch commands on a remote kie-server
   * 
   * @param containerId
   * @param sessionName
   * @param facts map facts and its key identifiers (used as part of the fact output names)
   * @return resultFacts map of returned facts and its respective ids
   */
  public Map<String ,Object> executeCommands(String containerId, String sessionName, HashMap<String, Object> facts) {
    Map<String, Object> resultFacts = new HashMap<String, Object>();

    LOG.info("== Sending commands to the kie-server [" + containerId + "] ==");
    LOG.info("\t feeding session [" + sessionName + "] up with the following input facts: ");
    LOG.info("\t\t" + facts);
    RuleServicesClient rulesClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
    KieCommands commandsFactory = KieServices.Factory.get().getCommands();
  
    // Get PriorApplications from the DB
    List<Command> kieCommands = new ArrayList<Command>();
    facts.forEach(
      (k, o) -> kieCommands.add(commandsFactory.newInsert(o, "isertedFactObject_" + k))
    );

    BatchExecutionCommand batchCommand = commandsFactory.newBatchExecution(kieCommands, sessionName);

    Command<?> fireAllRules = commandsFactory.newFireAllRules("firedRules");
    kieCommands.add(fireAllRules);
    Command<?> getObjects = commandsFactory.newGetObjects("resultFactObjects");
    kieCommands.add(getObjects);

    ServiceResponse<ExecutionResults> executeResponse = 
      rulesClient.executeCommandsWithResults(containerId, batchCommand);
  
    if(executeResponse.getType() == KieServiceResponse.ResponseType.SUCCESS) {
      LOG.info("Commands executed with success! Response: ");

      Collection<String> resultFactsIds = executeResponse.getResult().getIdentifiers();
      LOG.info("\n\tFacts Returned: " + resultFactsIds);
      resultFactsIds.forEach(
        id -> resultFacts.put(id, executeResponse.getResult().getValue(id))
      );

      LOG.info("\t" + resultFacts);
    } else {
      LOG.info("Error executing rules. Message: ");
      LOG.info(executeResponse.getMsg());
    }

    return resultFacts;
  }

  /**
   * Start a new process instance given a process definition and a map of process' variables
   * 
   * @param containerId
   * @param processDefinitionId
   * @param variables
   * @return {@link Long} processInstanceId
   */
  public Long startProcess(String containerId, String processDefinitionId, Map<String, Object> variables) {
    LOG.info("== Sending commands to the kie-server [" + containerId + "] ==");
    ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);

    LOG.info("\t Starting process [" + processDefinitionId + "] with the following input variables: ");
    LOG.info("\t\t" + variables);
    
    Long processInstanceID = processClient.startProcess(containerId, processDefinitionId, variables);
    return processInstanceID;
  }

  @PreDestroy
  public void closeResources(){
    LOG.info("=== Kie Client finalization ===\n");
    kieServicesClient.close();
  }
}