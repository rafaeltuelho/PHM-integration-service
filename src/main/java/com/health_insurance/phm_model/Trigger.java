package com.health_insurance.phm_model;

/**
 * Trigger
 */
public class Trigger {

    private String originalTriggerId;
    private String memberId;

	public String getOriginalTriggerId() {
		return originalTriggerId;
	}
	public void setOriginalTriggerId(String originalTriggerId) {
		this.originalTriggerId = originalTriggerId;
	}
	public String getMemberId() {
		return memberId;
	}
	public void setMemberId(String memberId) {
		this.memberId = memberId;
    }
    
	@Override
	public String toString() {
		return "Trigger [memberId=" + memberId + ", originalTriggerId=" + originalTriggerId + "]";
	}
    
    
}