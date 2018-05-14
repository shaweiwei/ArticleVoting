package com.av.entity;

import java.util.Date;

public class DeviceInfo {

	private String id;
	private String deviceNumber;
	private String deviceType;
	private long inspectTime;
	private InspecterInfo inspecter;
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getDeviceNumber() {
		return deviceNumber;
	}
	public void setDeviceNumber(String deviceNumber) {
		this.deviceNumber = deviceNumber;
	}
	public String getDeviceType() {
		return deviceType;
	}
	public void setDeviceType(String deviceType) {
		this.deviceType = deviceType;
	}
	public InspecterInfo getInspecter() {
		return inspecter;
	}
	public void setInspecter(InspecterInfo inspecter) {
		this.inspecter = inspecter;
	}
	public long getInspectTime() {
		return inspectTime;
	}
	public void setInspectTime(long inspectTime) {
		this.inspectTime = inspectTime;
	}
	
	
	
}
