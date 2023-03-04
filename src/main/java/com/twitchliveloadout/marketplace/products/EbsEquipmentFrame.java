package com.twitchliveloadout.marketplace.products;

public class EbsEquipmentFrame extends EbsEffectFrame {
	public Integer head = -1;
	public Integer cape = -1;
	public Integer amulet = -1;
	public Integer weapon = -1;
	public Integer torso = -1;
	public Integer shield = -1;
	public Integer arms = -1;
	public Integer legs = -1;
	public Integer hair = -1;
	public Integer hands = -1;
	public Integer boots = -1;
	public Integer jaw = -1;

	public Integer getEquipmentIdBySlotId(int slotId)
	{
		switch (slotId) {
			case 0: return head;
			case 1: return cape;
			case 2: return amulet;
			case 3: return weapon;
			case 4: return torso;
			case 5: return shield;
			case 6: return arms;
			case 7: return legs;
			case 8: return hair;
			case 9: return hands;
			case 10: return boots;
			case 11: return jaw;
		}

		return -1;
	}
}
