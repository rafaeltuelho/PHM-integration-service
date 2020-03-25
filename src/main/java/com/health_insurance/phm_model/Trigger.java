package com.health_insurance.phm_model;

/**
 * Trigger
 */
public class Trigger {

    private String triggerId;
    private String memberId;

	public String getTriggerId() {
		return triggerId;
	}
	public void setTriggerId(String triggerId) {
		this.triggerId = triggerId;
	}
	public String getMemberId() {
		return memberId;
	}
	public void setMemberId(String memberId) {
		this.memberId = memberId;
    }
    
	@Override
	public String toString() {
		return "Trigger [memberId=" + memberId + ", triggerId=" + triggerId + "]";
	}
    
    
}