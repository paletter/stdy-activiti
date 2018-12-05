package com.paletter.stdy.activiti;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.ProcessEngines;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.ProcessEngineImpl;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.util.json.JSONArray;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AuditContract2 {

	public static RuntimeService runtimeService;
	public static TaskService taskService;
	public static HistoryService historyService;
	public static IdentityService identityService;
	public static RepositoryService repositoryService;
	
	public static void main(String[] args) throws Throwable {
		ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
		  .setJdbcUrl("jdbc:h2:mem:activiti;DB_CLOSE_DELAY=1000")
		  .setJdbcUsername("sa")
		  .setJdbcPassword("")
		  .setJdbcDriver("org.h2.Driver")
		  .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
		ProcessEngine processEngine = cfg.buildProcessEngine();
		String pName = processEngine.getName();
		String ver = ProcessEngine.VERSION;
		System.out.println("ProcessEngine [" + pName + "] Version: [" + ver + "]");
		
		RepositoryService rs = processEngine.getRepositoryService();
		rs.createDeployment().addClasspathResource("AuditContract2.bpmn20.xml").deploy();
		System.out.println("Number of process definitions: " + rs.createProcessDefinitionQuery().count());
		
		String key = "CN20181205";
		
		runtimeService = processEngine.getRuntimeService();
		taskService = processEngine.getTaskService();
		historyService = processEngine.getHistoryService();
		identityService = processEngine.getIdentityService();
		repositoryService = processEngine.getRepositoryService();
		
		// 1 Start Workflow
		ProcessInstance processInstance = startWorkflow(processEngine, key);
		System.out.println("-- BusinessKey " + processInstance.getBusinessKey());
		
		auditLegal(key);
		auditFinance(key);
		auditLegal(key);
		auditFinance(key);
	}
	
	public static ProcessInstance startWorkflow(ProcessEngine processEngine, String key) {
		Map<String, Object> variables = new HashMap<String, Object>();
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("contractAuditProcess", key, variables);
		return processInstance;
	}
	
	public static void auditFinance(String key) {
		Task task = taskService.createTaskQuery().processInstanceBusinessKeyLike(key).taskCandidateGroup("finance").singleResult();
		if (task != null) {
			taskService.complete(task.getId());
			System.out.println("------- AuditFinance: " + task.getName() + " Complete");
		} else {
			System.out.println("------- Null Task Need FinanceAudit");
		}
	}
	
	public static void auditLegal(String key) {
		Task task = taskService.createTaskQuery().processInstanceBusinessKeyLike(key).taskCandidateGroup("legal").singleResult();
		if (task != null) {
			taskService.complete(task.getId());
			System.out.println("------- AuditLegal: " + task.getName() + " Complete");
		} else {
			System.out.println("------- Null Task Need AuditLegal");
		}
	}
	
	public static void createPng(ProcessEngine processEngine, ProcessInstance processInstance, ProcessEngineConfiguration cfg) throws Throwable {

		BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
		List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstance.getId());
		
	    ProcessEngineImpl defaultProcessEngine = (ProcessEngineImpl) ProcessEngines.getDefaultProcessEngine();
	    Context.setProcessEngineConfiguration(defaultProcessEngine.getProcessEngineConfiguration());

        ProcessDiagramGenerator diagramGenerator = processEngine.getProcessEngineConfiguration().getProcessDiagramGenerator();
        InputStream imageStream = diagramGenerator.generateDiagram(bpmnModel, "png", activeActivityIds);
        File f = new File("E:\\tmp.png");
        byte[] b = new byte[1024];
        int len;
        FileOutputStream os = new FileOutputStream(f);
        while ((len = imageStream.read(b, 0, 1024)) != -1) {
            os.write(b, 0, len);
        }
	}
	
	public static void convert(ProcessInstance processInstance) throws XMLStreamException, Throwable {
		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processInstance.getProcessDefinitionId()).singleResult();
        InputStream bpmnStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(),
                processDefinition.getResourceName());
        XMLInputFactory xif = XMLInputFactory.newInstance();
        InputStreamReader in = new InputStreamReader(bpmnStream, "UTF-8");
        XMLStreamReader xtr = xif.createXMLStreamReader(in);
        BpmnModel bpmnModel = new BpmnXMLConverter().convertToBpmnModel(xtr);

        BpmnJsonConverter converter = new BpmnJsonConverter();
        com.fasterxml.jackson.databind.node.ObjectNode modelNode = converter.convertToJson(bpmnModel);
        Model modelData = repositoryService.newModel();
        modelData.setKey(processDefinition.getKey());
        modelData.setName(processDefinition.getResourceName());
        modelData.setCategory(processDefinition.getDeploymentId());

        ObjectNode modelObjectNode = new ObjectMapper().createObjectNode();
        modelObjectNode.put(ModelDataJsonConstants.MODEL_NAME, processDefinition.getName());
        modelObjectNode.put(ModelDataJsonConstants.MODEL_REVISION, 1);
        modelObjectNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, processDefinition.getDescription());
        modelData.setMetaInfo(modelObjectNode.toString());

        repositoryService.saveModel(modelData);

        repositoryService.addModelEditorSource(modelData.getId(), modelNode.toString().getBytes("utf-8"));
	}
	
	public static void showModel() throws JsonProcessingException, IOException {
		List<Model> models = repositoryService.createModelQuery().list();
		System.out.println(new JSONArray(models).toString());
		
		Model modelData = models.get(0);
        ObjectNode modelNode = (ObjectNode) new ObjectMapper().readTree(repositoryService.getModelEditorSource(modelData.getId()));
        byte[] bpmnBytes = null;

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        String processName = "test.bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment().name(modelData.getName()).addString(processName, new String(bpmnBytes)).deploy();
	}
}
