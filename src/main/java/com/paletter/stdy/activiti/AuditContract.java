package com.paletter.stdy.activiti;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.util.json.JSONObject;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;

import com.fasterxml.jackson.databind.util.JSONPObject;

public class AuditContract {

	public static void main(String[] args) {
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
		rs.createDeployment().addClasspathResource("AuditContract.bpmn20.xml").deploy();
		System.out.println("Number of process definitions: " + rs.createProcessDefinitionQuery().count());
		
		Map<String, Object> variables = new HashMap<String, Object>();
		RuntimeService runtimeService = processEngine.getRuntimeService();
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("activitiReviewPooled", variables);

		// Verify that we started a new process instance
		System.out.println("Number of process instances: " + runtimeService.createProcessInstanceQuery().count());
		
		// Fetch all tasks for the management group
		TaskService taskService = processEngine.getTaskService();
		while (processInstance != null && !processInstance.isEnded()) {
			List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("management").list();
			for (Task task : tasks) {
				System.out.println("Task available: " + task.getName() + "," + new JSONObject(task).toString());
				Map<String, Object> taskVariables = new HashMap<String, Object>();
				if (task.getTaskDefinitionKey().equals("auditContract")) {
					taskVariables.put("auditState", "1");
				}
				
				taskService.complete(task.getId(), taskVariables);
			}
			
			// History
			HistoryService historyService = processEngine.getHistoryService();
			List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstance.getId())
					.finished().orderByHistoricActivityInstanceEndTime().asc().list();
			for (HistoricActivityInstance h : activities) {
				System.out.println("## " + h.getActivityName() + "|" + h.getActivityId());
			}
			
			processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstance.getId()).singleResult();
		}
	}
}
