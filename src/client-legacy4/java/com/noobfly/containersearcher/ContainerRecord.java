package com.noobfly.containersearcher;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContainerRecord {
	public String server;
	public String dimension;
	public int x;
	public int y;
	public int z;
	public Integer secondaryX;
	public Integer secondaryY;
	public Integer secondaryZ;
	public String containerType;
	public String entityUuid;
	public String villagerProfession;
	public long updatedAt;
	public Map<String, Integer> items = new LinkedHashMap<>();
	public java.util.List<ContainerItemRecord> entries = new java.util.ArrayList<>();
}
