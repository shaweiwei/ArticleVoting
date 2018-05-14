package com.av.entity;

import java.util.Date;
import java.util.LinkedList;

public class UserToken {
	private String userId;
	private long lastTime;
	private LinkedList<String> elementids;
	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public long getLastTime() {
		return lastTime;
	}
	public void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}
	public LinkedList<String> getElementids() {
		return elementids;
	}
	public void setElementids(LinkedList<String> elementids) {
		this.elementids = elementids;
	}
	
}
