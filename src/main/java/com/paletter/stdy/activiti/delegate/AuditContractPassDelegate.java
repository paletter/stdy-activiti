package com.paletter.stdy.activiti.delegate;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;

public class AuditContractPassDelegate implements JavaDelegate {

	@Override
	public void execute(DelegateExecution arg0) {
		System.out.println("## AuditContractPassDelegate");
	}

}
