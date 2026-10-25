package com.noobfly.containersearcher;

import java.util.ArrayList;
import java.util.List;

public final class ContainerItemRecord {
	public String itemId;
	// name of the shulker box this item was packed in, null when it sits loose
	public String inside;
	public String stackData;
	public int count;
	public String name;
	public String searchText;
	public boolean enchanted;
	public boolean damageable;
	public int damage;
	public int maxDamage;
	public List<String> tooltipLines = new ArrayList<>();
	public List<String> loreLines = new ArrayList<>();
	public List<String> enchantments = new ArrayList<>();
	public List<String> enchantmentNames = new ArrayList<>();
}
