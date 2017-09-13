/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc>
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License version 3
as published by the Free Software Foundation. You may not use, modify
or distribute this program under any other version of the
GNU Affero General Public License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package tools.packet;

import client.*;
import client.MapleTrait.MapleTraitType;
import client.inventory.*;
import client.status.IBuffStat;
import constants.GameConstants;
import handling.world.MapleCharacterLook;
import server.MapleItemInformationProvider;
import server.MapleShop;
import server.MapleShopItem;
import server.movement.ILifeMovementFragment;
import server.quest.MapleQuest;
import server.shops.AbstractPlayerStore;
import server.shops.IMaplePlayerShop;
import tools.BitTools;
import tools.KoreanDateUtil;
import tools.StringUtil;
import tools.data.MaplePacketLittleEndianWriter;
import tools.types.Pair;
import tools.types.Triple;

import java.util.*;
import java.util.Map.Entry;

public class PacketHelper {

    public final static long FT_UT_OFFSET = 116444592000000000L; // EDT
    public final static long MAX_TIME = 150842304000000000L; //00 80 05 BB 46 E6 17 02
    public final static long ZERO_TIME = 94354848000000000L; //00 40 E0 FD 3B 37 4F 01
    public final static long PERMANENT = 150841440000000000L; // 00 C0 9B 90 7D E5 17 02

    public static long getKoreanTimestamp(final long realTimestamp) {
        return getTime(realTimestamp);
    }

    public static long getTime(long realTimestamp) {
        if (realTimestamp == -1) {
            return MAX_TIME;
        } else if (realTimestamp == -2) {
            return ZERO_TIME;
        } else if (realTimestamp == -3) {
            return PERMANENT;
        }
        return ((realTimestamp * 10000) + FT_UT_OFFSET);
    }

    public static long getFileTimestamp(long timeStampinMillis, boolean roundToMinutes) {
        if (SimpleTimeZone.getDefault().inDaylightTime(new Date())) {
            timeStampinMillis -= 3600000L;
        }
        long time;
        if (roundToMinutes) {
            time = (timeStampinMillis / 1000 / 60) * 600000000;
        } else {
            time = timeStampinMillis * 10000;
        }
        return time + FT_UT_OFFSET;
    }

    public static void addQuestInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        final boolean idk = true;

        // 0x2000
        final List<MapleQuestStatus> started = chr.getStartedQuests();
        mplew.write(idk ? 1 : 0); // boolean
        if (idk) {
            mplew.writeShort(started.size());
            for (final MapleQuestStatus q : started) {
                mplew.writeShort(q.getQuest().getId());
                if (q.hasMobKills()) {
                    final StringBuilder sb = new StringBuilder();
                    for (final int kills : q.getMobKills().values()) {
                        sb.append(StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3));
                    }
                    mplew.writeMapleAsciiString(sb.toString());
                } else {
                    mplew.writeMapleAsciiString(q.getCustomData() == null ? "" : q.getCustomData());
                }
            }

        } else {
            mplew.writeShort(0); // size, one short per size
        }
        mplew.writeShort(0); // size, two strings per size

        // 0x4000
        mplew.write(idk ? 1 : 0); //dunno
        if (idk) {
            final List<MapleQuestStatus> completed = chr.getCompletedQuests();
            mplew.writeShort(completed.size());
            for (final MapleQuestStatus q : completed) {
                mplew.writeShort(q.getQuest().getId());
                mplew.writeLong(getTime(q.getCompletionTime()));
            }
        } else {
            mplew.writeShort(0); // size, one short per size
        }
    }

    public static void addSkillInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) { // 0x100
        final Map<Skill, SkillEntry> skills = chr.getSkills();
        boolean useOld = skills.size() < 500;
        mplew.write(useOld ? 1 : 0); // To handle the old skill system or something?
        if (useOld) {
            mplew.writeShort(skills.size());
            for (final Entry<Skill, SkillEntry> skill : skills.entrySet()) {
                mplew.writeInt(skill.getKey().getId());
                mplew.writeInt(skill.getValue().skillevel);
                addExpirationTime(mplew, skill.getValue().expiration);

                if (skill.getKey().isFourthJob()) {
                    mplew.writeInt(skill.getValue().masterlevel);
                }
            }
        } else {
            final Map<Integer, Integer> skillsWithoutMax = new LinkedHashMap<>();
            final Map<Integer, Long> skillsWithExpiration = new LinkedHashMap<>();
            final Map<Integer, Integer> skillsWithMax = new LinkedHashMap<>();

            // Fill in these maps
            for (final Entry<Skill, SkillEntry> skill : skills.entrySet()) {
                skillsWithoutMax.put(skill.getKey().getId(), skill.getValue().skillevel);
                if (skill.getValue().expiration > 0) {
                    skillsWithExpiration.put(skill.getKey().getId(), skill.getValue().expiration);
                }
                if (skill.getKey().isFourthJob()) {
                    skillsWithMax.put(skill.getKey().getId(), skill.getValue().masterlevel);
                }
            }

            int amount = skillsWithoutMax.size();
            mplew.writeShort(amount);
            for (final Entry<Integer, Integer> x : skillsWithoutMax.entrySet()) {
                mplew.writeInt(x.getKey());
                mplew.writeInt(x.getValue()); // 80000000, 80000001, 80001040 show cid if linked.
            }
            mplew.writeShort(0); // For each, int

            amount = skillsWithExpiration.size();
            mplew.writeShort(amount);
            for (final Entry<Integer, Long> x : skillsWithExpiration.entrySet()) {
                mplew.writeInt(x.getKey());
                mplew.writeLong(x.getValue()); // Probably expiring skills here
            }
            mplew.writeShort(0); // For each, int

            amount = skillsWithMax.size();
            mplew.writeShort(amount);
            for (final Entry<Integer, Integer> x : skillsWithMax.entrySet()) {
                mplew.writeInt(x.getKey());
                mplew.writeInt(x.getValue());
            }
            mplew.writeShort(0); // For each, int (Master level = 0? O.O)
        }
    }

    public static void addCoolDownInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        final List<MapleCoolDownValueHolder> cd = chr.getCooldowns();
        mplew.writeShort(cd.size());
        for (final MapleCoolDownValueHolder cooling : cd) {
            mplew.writeInt(cooling.skillId);
            mplew.writeShort((int) (cooling.length + cooling.startTime - System.currentTimeMillis()) / 1000);
        }
    }

    public static void addRocksInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        final int[] mapz = chr.getRegRocks();
        for (int i = 0; i < 5; i++) { // VIP teleport map
            mplew.writeInt(mapz[i]);
        }

        final int[] map = chr.getRocks();
        for (int i = 0; i < 10; i++) { // VIP teleport map
            mplew.writeInt(map[i]);
        }

        final int[] maps = chr.getHyperRocks();
        for (int i = 0; i < 13; i++) { // VIP teleport map
            mplew.writeInt(maps[i]);
        }
    }

    public static void addRingInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        Triple<List<MapleRing>, List<MapleRing>, List<MapleRing>> aRing = chr.getRings(true);
        List<MapleRing> cRing = aRing.getLeft();
        mplew.writeShort(cRing.size());
        for (MapleRing ring : cRing) { // 33
            mplew.writeInt(ring.getPartnerChrId());
            mplew.writeAsciiString(ring.getPartnerName(), 15);
            mplew.writeLong(ring.getRingId());
            mplew.writeLong(ring.getPartnerRingId());
        }
        List<MapleRing> fRing = aRing.getMid();
        mplew.writeShort(fRing.size());
        for (MapleRing ring : fRing) { // 37
            mplew.writeInt(ring.getPartnerChrId());
            mplew.writeAsciiString(ring.getPartnerName(), 15);
            mplew.writeLong(ring.getRingId());
            mplew.writeLong(ring.getPartnerRingId());
            mplew.writeInt(ring.getItemId());
        }
        List<MapleRing> mRing = aRing.getRight();
        mplew.writeShort(mRing.size());
        int marriageId = 30000;
        for (MapleRing ring : mRing) { // 48
            mplew.writeInt(marriageId);
            mplew.writeInt(chr.getId());
            mplew.writeInt(ring.getPartnerChrId());
            mplew.writeShort(3); //1 = engaged 3 = married
            mplew.writeInt(ring.getRingId());
            mplew.writeInt(ring.getPartnerRingId());
            mplew.writeAsciiString(chr.getGender() == 0 ? chr.getName() : ring.getPartnerName(), 15);
            mplew.writeAsciiString(chr.getGender() == 0 ? ring.getPartnerName() : chr.getName(), 15);
        }
    }

    public static void addCharStats(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        mplew.writeInt(chr.getId());
        mplew.writeAsciiString(chr.getName(), 15);
        mplew.write(chr.getGender());
        mplew.write(chr.getSkinColor());
        mplew.writeInt(chr.getFace());
        mplew.writeInt(chr.getHair());
        mplew.write(chr.getLevel());
        mplew.writeShort(chr.getJob());
        chr.getStat().connectData(mplew);
        mplew.writeShort(chr.getRemainingAp());
        if (GameConstants.isSeparatedSp(chr.getJob())) {
            final int size = chr.getRemainingSpSize();
            mplew.write(size);
            for (int i = 0; i < chr.getRemainingSps().length; i++) {
                if (chr.getRemainingSp(i) > 0) {
                    mplew.write(i + 1);
                    mplew.write(chr.getRemainingSp(i));
                }
            }
        } else {
            mplew.writeShort(chr.getRemainingSp());
        }
        mplew.writeInt(chr.getExp());
        mplew.writeInt(chr.getFame());
        mplew.writeInt(chr.getGachExp());
        mplew.writeLong(KoreanDateUtil.getFileTimestamp(System.currentTimeMillis(), false));
        mplew.writeInt(chr.getMapId());
        mplew.write(chr.getInitialSpawnpoint());
        mplew.writeShort(chr.getSubcategory());
        if (GameConstants.isDemon(chr.getJob())) {
            mplew.writeInt(chr.getDemonMarking());
        }
        mplew.write(chr.getFatigue());
        mplew.writeInt(GameConstants.getCurrentDate());
        for (MapleTraitType t : MapleTraitType.values()) {
            mplew.writeInt(chr.getTrait(t).getTotalExp());
        }
        for (MapleTraitType t : MapleTraitType.values()) {
            mplew.writeShort(0);
        }
        mplew.writeInt(chr.getStat().pvpExp);
        mplew.write(chr.getStat().pvpRank);
        mplew.writeInt(chr.getBattlePoints());
        mplew.write(6);
        mplew.writeInt(0);
        mplew.writeReversedLong(getTime(System.currentTimeMillis()));
        mplew.writeZeroBytes(25); // 台版以前到現在都有
        mplew.write(1);
        mplew.write(1);
        mplew.write(1);
        mplew.write(1);
        mplew.write(1);
    }

    public static void addCharLook(final MaplePacketLittleEndianWriter mplew, final MapleCharacterLook chr, final boolean mega, MapleClient client) {
        mplew.write(chr.getGender());
        mplew.write(chr.getSkinColor());
        mplew.writeInt(chr.getFace());
        mplew.writeInt(chr.getJob());
        mplew.write(mega ? 0 : 1);
        mplew.writeInt(chr.getHair());

        final Map<Byte, Integer> myEquip = new LinkedHashMap<>();
        final Map<Byte, Integer> maskedEquip = new LinkedHashMap<>();
        final Map<Byte, Integer> equip = chr.getEquips();
        final Map<Byte, Integer> totem = new LinkedHashMap<>();
        for (final Entry<Byte, Integer> item : equip.entrySet()) {
            if (item.getKey() < -127) { //not visible
                continue;
            }
            byte pos = (byte) (item.getKey() * -1);

            if (pos <= -118 && pos >= -120) {
                pos = (byte) (pos + 118);
                totem.put(pos, item.getValue());
            } else if (pos < 100 && myEquip.get(pos) == null) {
                myEquip.put(pos, item.getValue());
            } else if (pos > 100 && pos != 111) {
                pos = (byte) (pos - 100);
                if (myEquip.get(pos) != null) {
                    maskedEquip.put(pos, myEquip.get(pos));
                }
                myEquip.put(pos, item.getValue());
            } else if (myEquip.get(pos) != null) {
                maskedEquip.put(pos, item.getValue());
            }
        }
        for (final Entry<Byte, Integer> entry : myEquip.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }
        mplew.write(0xFF); // end of visible itens
        for (final Entry<Byte, Integer> entry : totem.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }
        mplew.write(0xFF); // end of totem
        // masked itens

        mplew.write(0xFF);
        mplew.writeInt(0);
        //TODO: 解包

        mplew.writeBool(chr.isElf(client.getPlayer()));
        mplew.writeZeroBytes(12); // pets
        if ((GameConstants.isDemon(chr.getJob()))) {
            mplew.writeInt(chr.getDemonMarking());
        }
    }

    public static void addExpirationTime(final MaplePacketLittleEndianWriter mplew, final long time) {
        mplew.writeLong(getTime(time));
    }

    public static void addItemPosition(final MaplePacketLittleEndianWriter mplew, final Item item, final boolean trade, final boolean bagSlot) {
        if (item == null) {
            mplew.write(0);
            return;
        }
        short pos = item.getPosition();
        if (pos <= -1) {
            pos *= -1;
            if (pos > 100 && pos < 1000) {
                pos -= 100;
            }
        }
        if (bagSlot) {
            mplew.writeInt((pos % 100) - 1);
        } else if (!trade && item.getType() == 1) {
            mplew.writeShort(pos);
        } else {
            mplew.write(pos);
        }
    }

    public static void serializeMovementList(final MaplePacketLittleEndianWriter lew, final List<ILifeMovementFragment> moves) {
        lew.write(moves.size());
        for (ILifeMovementFragment move : moves) {
            move.serialize(lew);
        }
    }

    public static void addAnnounceBox(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        if (chr.getPlayerShop() != null && chr.getPlayerShop().isOwner(chr) && (chr.getPlayerShop().getShopType() == 3 || chr.getPlayerShop().getShopType() == 4) && chr.getPlayerShop().isAvailable()) {
            addOmok(mplew, chr.getPlayerShop(), chr);
        } else if (chr.getPlayerShop() != null && chr.getPlayerShop().isOwner(chr) && chr.getPlayerShop().getShopType() != 1 && chr.getPlayerShop().isAvailable()) {
            addInteraction(mplew, chr.getPlayerShop());
        } else {
            mplew.write(0);
        }
    }

    public static void addInteraction(final MaplePacketLittleEndianWriter mplew, IMaplePlayerShop shop) {
        mplew.write(shop.getGameType());
        mplew.writeInt(((AbstractPlayerStore) shop).getObjectId());
        mplew.writeMapleAsciiString(shop.getDescription());
        if (shop.getShopType() != 1) {
            mplew.write(shop.getPassword().length() > 0 ? 1 : 0); //password = false
        }
        mplew.write(shop.getItemId() % 10);
        mplew.write(shop.getSize()); //current size
        mplew.write(shop.getMaxSize()); //full slots... 4 = 4-1=3 = has slots, 1-1=0 = no slots
        if (shop.getShopType() != 1) {
            mplew.write(shop.isOpen() ? 0 : 1);
        }
    }

    public static void addOmok(final MaplePacketLittleEndianWriter mplew, IMaplePlayerShop shop, MapleCharacter owner) {
        mplew.write(shop.getGameType()); // 1 = omok, 2 = matchcard
        mplew.writeInt(((AbstractPlayerStore) shop).getObjectId());
        mplew.writeMapleAsciiString(shop.getDescription());
        mplew.write(shop.getPassword().length() > 0 ? 1 : 0); //password = false
        mplew.write(shop.getGameType() == 1 ? 0 : owner.getMatchCardVal());
        mplew.write(shop.getSize());
        mplew.write(2);
        mplew.write(shop.isOpen() ? 0 : 1);
    }

    public static void addCharacterInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        long mask = 0xFF_FF_FF_FF_FF_FF_FF_FFL;
        addCharacterInfo(mplew, chr, mask);
    }

    public static void addCharacterInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr, long mask) {

        mplew.writeLong(mask);
        mplew.write(0);

        int v5 = 0;
        mplew.write(v5); // 應該是拼圖
        for (int i = 1; i < v5; i++) {
            mplew.writeInt(0);
        }


        int v10 = 0;
        mplew.writeInt(v10);
        for (int i = 0; i < v10; i++) {
            mplew.writeInt(0);
            mplew.writeLong(0); // Time
        }

        boolean v11 = false;
        mplew.writeBool(v11);
        if (v11) {
            mplew.write(0);
            int v12 = 0;
            mplew.writeInt(v12);
            for (int i = 0; i < v12; i++) {
                mplew.writeLong(0);
            }

            int v15 = 0;
            mplew.writeInt(v15);
            for (int i = 0; i < v15; i++) {
                mplew.writeLong(0);
            }
        }

        if ((mask & 1) != 0) {
            addCharStats(mplew, chr); // 角色狀態訊息
            mplew.write(chr.getBuddylist().getCapacity()); // 好友上限
            mplew.writeBool(chr.getBlessOfFairyOrigin() != null); // 精靈的祝福
            if (chr.getBlessOfFairyOrigin() != null) {
                mplew.writeMapleAsciiString(chr.getBlessOfFairyOrigin());
            }
            mplew.writeBool(chr.getBlessOfEmpressOrigin() != null); // 女皇的祝福
            if (chr.getBlessOfEmpressOrigin() != null) {
                mplew.writeMapleAsciiString(chr.getBlessOfEmpressOrigin());
            }
            // 終極冒險家訊息
            MapleQuestStatus ultExplorer = chr.getQuestNoAdd(MapleQuest.getInstance(GameConstants.ULT_EXPLORER));
            mplew.writeBool((ultExplorer != null) && (ultExplorer.getCustomData() != null));
            if ((ultExplorer != null) && (ultExplorer.getCustomData() != null)) {
                mplew.writeMapleAsciiString(ultExplorer.getCustomData());
            }
            mplew.writeLong(getTime(System.currentTimeMillis()));
        }

        if ((mask & 2) != 0) {
            mplew.writeInt(chr.getMeso());
            mplew.writeInt(chr.getId());
            mplew.writeInt(763); // 小鋼珠
            mplew.writeInt(chr.getCSPoints(2)); // 楓葉點數
        }

        if ((mask & 8) != 0 || (mask & 8 | 0x2000000 & mask) != 0) {
            int v20 = 0;
            mplew.writeInt(v20);
            for (int i = 0; i > v20; i++) {
                mplew.writeInt(0);
                mplew.writeInt(0);
                mplew.writeInt(0);
                mplew.writeInt(0);
            }
        }

        if ((mask & 0x80) != 0) {
            mplew.write(chr.getInventory(MapleInventoryType.EQUIP).getSlotLimit()); // equip slots
            mplew.write(chr.getInventory(MapleInventoryType.USE).getSlotLimit()); // use slots
            mplew.write(chr.getInventory(MapleInventoryType.SETUP).getSlotLimit()); // set-up slots
            mplew.write(chr.getInventory(MapleInventoryType.ETC).getSlotLimit()); // etc slots
            mplew.write(chr.getInventory(MapleInventoryType.CASH).getSlotLimit()); // cash slots
        }

        if ((mask & 0x100000) != 0) {
            mplew.writeLong(getTime(-2));
        }

        MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
        final List<Item> equipped = iv.newList();
        Collections.sort(equipped);
        List<Item> items;
        Iterator<Item> Iitem;

        if ((mask & 4) != 0) {

            boolean v54 = false;
            mplew.writeBool(v54);

            Iitem = getInventoryInfo(equipped, 1, 99).iterator(); // 普通裝備
            while (true) {
                Item v52 = Iitem.hasNext() ? Iitem.next() : null;
                mplew.writeShort(getItemPosition(v52, false, false));
                if (v52 != null) {
                    GW_ItemSlotBase_Decode(mplew, v52, chr);
                } else {
                    break;
                }
            }

            Iitem = chr.getInventory(MapleInventoryType.EQUIP).newList().iterator();// 裝備
            while (true) {
                Item v61 = Iitem.hasNext() ? Iitem.next() : null;
                mplew.writeShort(getItemPosition(v61, false, false));
                if (v61 != null) {
                    GW_ItemSlotBase_Decode(mplew, v61, chr);
                } else {
                    break;
                }
            }

            Iitem = getInventoryInfo(equipped, -1000, 100).iterator(); // 龍魔導
            while (true) {
                Item v52 = Iitem.hasNext() ? Iitem.next() : null;
                mplew.writeShort(getItemPosition(v52, false, false));
                if (v52 != null) {
                    GW_ItemSlotBase_Decode(mplew, v52, chr);
                } else {
                    break;
                }
            }

        }

        // 其他欄位
        int other = 2;
        do {
            MapleInventoryType[] mit = {MapleInventoryType.UNDEFINED, MapleInventoryType.EQUIP, MapleInventoryType.USE, MapleInventoryType.SETUP, MapleInventoryType.ETC, MapleInventoryType.CASH};
            Iterator<Item> it = chr.getInventory(mit[other]).newList().iterator();
            while (true) {
                Item item = it.hasNext() ? it.next() : null;
                mplew.write(getItemPosition(item, false, false));
                if (item != null) {
                    GW_ItemSlotBase_Decode(mplew, item, chr);
                } else {
                    break;
                }
            }
            ++other;
        } while (other <= 5);

        for (int i = 0; i < chr.getExtendedSlots().size(); i++) {
            mplew.writeInt(i);
            mplew.writeInt(chr.getExtendedSlot(i));
            for (Item item : chr.getInventory(MapleInventoryType.ETC).list()) {
                if (item.getPosition() > (i * 100 + 100) && item.getPosition() < (i * 100 + 200)) {
                    addItemPosition(mplew, item, false, true);
                    GW_ItemSlotBase_Decode(mplew, item, chr);
                }
            }
        }
        mplew.writeInt(-1);


        if ((mask & 0x1000000) != 0) {
            int unkSize = 0;
            mplew.writeInt(unkSize);
            for (int i = unkSize; i > 0; i--) {
                mplew.writeInt(0);
                mplew.writeLong(0);
            }
        }

        if ((mask & 0x40000000) != 0) {
            int unkSize = 0;
            mplew.writeInt(unkSize);
            for (int i = unkSize; i > 0; i--) {
                mplew.writeLong(0);
                mplew.writeLong(0);
            }
        }

        if ((mask & 0x800000) != 0) {
            while (true) {
                int pos = 0;
                mplew.write(pos);
                if (pos == 0) {
                    break;
                }
                mplew.writeInt(0);
                mplew.write(0);
                mplew.write(0);
                mplew.writeInt(0);
                mplew.writeInt(0);
                mplew.writeInt(0);
                mplew.writeInt(0);
                mplew.write(0);
                mplew.writeInt(0);
                mplew.writeLong(0);
                mplew.writeLong(0);
                mplew.writeLong(0);
                mplew.writeLong(0);
            }
        }

        if ((mask & 0x100) != 0) {
            addSkillInfo(mplew, chr);//技能訊息
        }

        if ((mask & 0x8000) != 0) {
            addCoolDownInfo(mplew, chr);//冷卻技能訊息
        }

        if ((mask & 0x200) != 0) {
            addStartedQuestInfo(mplew, chr);//已開始任務訊息
        }
        if ((mask & 0x4000) != 0) {
            addCompletedQuestInfo(mplew, chr);//已完成任務訊息
        }

        if ((mask & 0x400) != 0) {
            addMiniGameInfo(mplew, chr);//小遊戲訊息
        }

        if ((mask & 0x800) != 0) {
            addRingInfo(mplew, chr);//戒指訊息
        }
        if ((mask & 0x1000) != 0) {
            addRocksInfo(mplew, chr);
        }

        if ((mask & 0x40000) != 0) {
            chr.QuestInfoPacket(mplew);//任務數據
        }

        if ((mask & 0x200000) != 0 && MapleJob.is狂豹獵人(chr.getJob())) { // 狂豹的豹訊息
            addJaguarInfo(mplew, chr);
        }

        if ((mask & 0x400000) != 0) {
            int v183 = 0;
            mplew.writeShort(v183);
            for (int i = 0; i < v183; i++) {
                mplew.writeShort(0);
                mplew.writeLong(0);
            }
        }


        if ((mask & 0x4000000) != 0) {
            int v143 = 0;
            mplew.writeShort(v143); // 應該是商店限購的已購買數量(如培羅德)
            for (int i = 0; i < v143; i++) {
                int v179 = 0;
                int v180 = 0;
                mplew.writeShort(v179); // 數量
                mplew.writeInt(v180); // NPCID
                if (v179 != 0 && v180 != 0) {
                    for (int j = 0; j < v179; j++) {
                        mplew.writeInt(v180); // NPCID 應該是跟v180一致?
                        mplew.write(0); // 商品位置嗎
                        mplew.writeInt(0); // 道具ID
                        mplew.writeShort(0); // 已經購買次數
                    }
                }
            }
        }

        if ((mask & 0x20000000) != 0) { // [52] Byte幻影複製技能訊息
            for (int i = 1; i <= 4; i++) {
                addStolenSkills(mplew, chr, i, false); // 52
            }
        }

        if ((mask & 0x10000000) != 0) {
            mplew.write(1);
            int v216 = 0;
            for(int i = 0; i < v216; i++)
                mplew.writeInt(0);
        }


        if ((mask & 0x80000000) != 0) {
            addOXSystemInfo(mplew, chr);//角色內在能力訊息
        }

        if ((mask & 1) != 0) {
            addHonorInfo(mplew, chr);//內在能力聲望訊息
        }
    }

    public static void addMiniGameInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        short size = 0;
        mplew.writeShort(size);
        for (int i = 0; i < size; i++) {
            mplew.writeInt(0);
            mplew.writeInt(0);
            mplew.writeInt(0);
            mplew.writeInt(0);
            mplew.writeInt(0);
            mplew.write(0);
        }
    }

    public static void addCompletedQuestInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        boolean newPacket = true;
        mplew.writeBool(newPacket);
        if (newPacket) {
            final List<MapleQuestStatus> completed = chr.getCompletedQuests();
            mplew.writeShort(completed.size());
            for (MapleQuestStatus q : completed) {
                mplew.writeShort(q.getQuest().getId());
                mplew.writeLong(KoreanDateUtil.getQuestTimestamp(q.getCompletionTime()));
                //v139 changed from long to int
            }
        } else {
            final List<MapleQuestStatus> completed = chr.getCompletedQuests();
            mplew.writeShort(completed.size());
            for (MapleQuestStatus q : completed) {
                mplew.writeShort(q.getQuest().getId());
            }
        }
    }

    public static void addStartedQuestInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        boolean newPacket = true;
        mplew.writeBool(newPacket);
        if (newPacket) {
            final List<MapleQuestStatus> started = chr.getStartedQuests();
            mplew.writeShort(started.size());
            for (MapleQuestStatus q : started) {
                mplew.writeShort(q.getQuest().getId());
                if (q.hasMobKills()) {
                    StringBuilder sb = new StringBuilder();
                    for (Iterator i$ = q.getMobKills().values().iterator(); i$.hasNext(); ) {
                        int kills = ((Integer) i$.next());
                        sb.append(StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3));
                    }
                    mplew.writeMapleAsciiString(sb.toString());
                } else {
                    mplew.writeMapleAsciiString(q.getCustomData() == null ? "" : q.getCustomData());
                }
            }
        } else {
            final List<MapleQuestStatus> started = chr.getStartedQuests();
            mplew.writeShort(started.size());
            for (MapleQuestStatus q : started) {
                mplew.writeShort(q.getQuest().getId());
            }
        }
    }

    public static void addHonorInfo(final MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeInt(chr.getHonourLevel()); //之前是聲望等級honor lvl
        mplew.writeInt(chr.getHonourExp()); //之前是聲望經驗值,現在是聲望honor exp
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
    }

    public static void addOXSystemInfo(final MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        int OX_System = 0;
        mplew.writeShort(OX_System); //for <short> length write 2 shorts
        for (int i = 0; i < OX_System; i++) {
            mplew.writeInt(0);
            mplew.writeInt(0);
            mplew.writeMapleAsciiString("");
            mplew.write(0);
            mplew.writeLong(0);
            mplew.writeInt(0);
            mplew.writeMapleAsciiString("");
            mplew.write(0);
            mplew.write(0);
            mplew.writeLong(0);
            mplew.writeMapleAsciiString("");
        }
    }


    public static void addStolenSkills(MaplePacketLittleEndianWriter mplew, MapleCharacter chr, int jobNum, boolean writeJob) {
        if (writeJob) {
            mplew.writeInt(jobNum);
        }
        int count = 0;
        if (chr.getStolenSkills() != null) {
            for (Pair<Integer, Boolean> sk : chr.getStolenSkills()) {
                if (MapleJob.getJobGrade(sk.left / 10000) == jobNum) {
                    mplew.writeInt(sk.left);
                    count++;
                    if (count >= GameConstants.getNumSteal(jobNum)) {
                        break;
                    }
                }
            }
        }
        while (count < GameConstants.getNumSteal(jobNum)) { //for now?
            mplew.writeInt(0);
            count++;
        }
    }

    public static void addChosenSkills(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        for (int i = 1; i <= 4; i++) {
            boolean found = false;
            if (chr.getStolenSkills() != null) {
                for (Pair<Integer, Boolean> sk : chr.getStolenSkills()) {
                    if (MapleJob.getJobGrade(sk.left / 10000) == i && sk.right) {
                        mplew.writeInt(sk.left);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                mplew.writeInt(0);
            }
        }
    }


    public static List<Item> getInventoryInfo(List<Item> equipped, int position) {
        return getInventoryInfo(equipped, position, 100);
    }

    public static List<Item> getInventoryInfo(List<Item> equipped, int position, int size) {
        List<Item> items = new LinkedList();
        for (Item item : equipped) {
            int pos = -item.getPosition();
            if (pos >= position && pos < position + size) {
                items.add(item);
            }
        }
        Collections.sort(items);
        return items;
    }

    public static int getItemPosition(Item item, boolean trade, boolean bagSlot) {
        if (item == null) {
            return 0;
        }
        short pos = item.getPosition();
        if (pos <= -1) {
            pos = (short) (pos * -1);
            if ((pos > 100) && (pos < 1000)) {
                pos = (short) (pos - 100);
            }
        }
        if (bagSlot) {
            return (pos % 100 - 1);
        } else if ((!trade) && (item.getType() == 1)) {
            return (pos);
        } else {
            return (pos);
        }
    }


    public static final void addInnerStats(final MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        final List<InnerSkillValueHolder> skills = chr.getInnerSkills();
        mplew.writeShort(skills.size());
        for (int i = 0; i < skills.size(); ++i) {
            mplew.write(i + 1); // key
            mplew.writeInt(skills.get(i).getSkillId()); //d 7000000 id ++, 71 = char cards
            mplew.write(skills.get(i).getSkillLevel()); // level
            mplew.write(skills.get(i).getRank()); //rank, C, B, A, and S
        }

        mplew.writeInt(chr.getHonourLevel()); //honor lvl
        mplew.writeInt(chr.getHonourExp()); //honor exp
    }

    public static final void addCoreAura(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeInt(0);
        mplew.writeLong(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeLong(getTime(System.currentTimeMillis() + 86400000));
        mplew.writeInt(0);
        mplew.write((GameConstants.isJett(chr.getJob())) ? 1 : 0);
    }
/*     */

    public static void addMonsterBookInfo(final MaplePacketLittleEndianWriter mplew, final MapleCharacter chr) {
        mplew.writeInt(0); // 0x20000
        if (chr.getMonsterBook().getSetScore() > 0) { // 0x10000
            chr.getMonsterBook().writeFinished(mplew);
        } else {
            chr.getMonsterBook().writeUnfinished(mplew);
        }

        mplew.writeInt(chr.getMonsterBook().getSet()); // 0x80000000
    }

    public static void addPetItemInfo(final MaplePacketLittleEndianWriter mplew, final Item item, final MaplePet pet, final boolean active) {
        if (item == null) {
            mplew.writeLong(PacketHelper.getKoreanTimestamp((long) (System.currentTimeMillis() * 1.5)));
        } else {
            PacketHelper.addExpirationTime(mplew, item.getExpiration() <= System.currentTimeMillis() ? -1 : item.getExpiration());
        }
        mplew.writeInt(-1);
        mplew.writeAsciiString(pet.getName(), 13);
        mplew.write(pet.getLevel());
        mplew.writeShort(pet.getCloseness());
        mplew.write(pet.getFullness());
        if (item == null) {
            mplew.writeLong(PacketHelper.getKoreanTimestamp((long) (System.currentTimeMillis() * 1.5)));
        } else {
            PacketHelper.addExpirationTime(mplew, item.getExpiration() <= System.currentTimeMillis() ? -1 : item.getExpiration());
        }
        mplew.writeShort(0);
        mplew.writeShort(pet.getFlags());
        mplew.writeInt(pet.getPetItemId() == 5000054 && pet.getSecondsLeft() > 0 ? pet.getSecondsLeft() : 0); //in seconds, 3600 = 1 hr.
        mplew.writeShort(0);
        mplew.write(active ? (pet.getSummoned() ? pet.getSummonedValue() : 0) : 0); // 1C 5C 98 C6 01
        for (int i = 0; i < 4; i++) {
            mplew.write(0); //0x40 before, changed to 0?
        }
    }


    /*      */
    public static void addShopInfo(MaplePacketLittleEndianWriter mplew, MapleShop shop, MapleClient c)
/*      */ {
/* 1002 */
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
/* 1003 */
        mplew.write(shop.getRanks().size() > 0 ? 1 : 0);
/*      */
/* 1006 */
        if (shop.getRanks().size() > 0) {
/* 1007 */
            mplew.write(shop.getRanks().size());
/* 1008 */
            for (Pair s : shop.getRanks()) {
/* 1009 */
                mplew.writeInt(((Integer) s.left).intValue());
/* 1010 */
                mplew.writeMapleAsciiString((String) s.right);
/*      */
            }
/*      */
        }
/* 1013 */
        mplew.writeShort(shop.getItems().size() + c.getPlayer().getRebuy().size());
/* 1014 */
        for (MapleShopItem item : shop.getItems()) {
/* 1015 */
            addShopItemInfo(mplew, item, shop, ii, null);
/*      */
        }
        for (Iterator<Item> it = c.getPlayer().getRebuy().iterator(); it.hasNext(); ) {
            Item i = it.next();
            addShopItemInfo(mplew, new MapleShopItem(i.getItemId(), (int) ii.getPrice(i.getItemId())), shop, ii, i);
        }
    }

    /*      */
/*      */
    public static void addShopItemInfo(MaplePacketLittleEndianWriter mplew, MapleShopItem item, MapleShop shop, MapleItemInformationProvider ii, Item i) {

        mplew.writeInt(item.getItemId());
        mplew.writeInt(item.getPrice());
        mplew.write(0);
        mplew.writeInt(item.getReqItem());
        mplew.writeInt(item.getReqItemQ());
        mplew.writeInt(item.getExpiration());
        mplew.writeInt(item.getMinLevel());
        mplew.writeInt(item.getCategory());
        mplew.write(0);
        mplew.writeInt(0);
        mplew.writeInt(0);

        if ((!GameConstants.isThrowingStar(item.getItemId())) && (!GameConstants.isBullet(item.getItemId()))) {
            mplew.writeShort(1);
            mplew.writeShort(1000);
        } else {
            mplew.writeZeroBytes(6);
            mplew.writeShort(BitTools.doubleToShortBits(ii.getPrice(item.getItemId())));
            mplew.writeShort(ii.getSlotMax(item.getItemId()));
        }
        mplew.write(i == null ? 0 : 1);
        if (i != null) {
            PacketHelper.GW_ItemSlotBase_Decode(mplew, i);
        }
        if (shop.getRanks().size() > 0) {
            mplew.write(item.getRank() >= 0 ? 1 : 0);
            if (item.getRank() >= 0) {
                mplew.write(item.getRank());
            }
        }
        mplew.writeZeroBytes(16);
        for (int j = 0; j < 4; j++) {
            mplew.writeReversedLong(System.currentTimeMillis());
        }
    }

    public static void addJaguarInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.write(chr.getIntNoRecord(GameConstants.JAGUAR));
        for (int i = 0; i < 5; i++) {
            mplew.writeInt(0);
        }
    }

    public static <E extends IBuffStat> void writeSingleMask(MaplePacketLittleEndianWriter mplew, E statup) {
        for (int i = GameConstants.MAX_BUFFSTAT; i >= 1; i--) {
            mplew.writeInt(i == statup.getPosition() ? statup.getValue() : 0);
        }
    }

    public static <E extends IBuffStat> void writeMask(MaplePacketLittleEndianWriter mplew, Collection<E> statups) {
        int[] mask = new int[GameConstants.MAX_BUFFSTAT];
        for (E statup : statups) {
            mask[statup.getPosition() - 1] |= statup.getValue();
        }
        for (int i = mask.length; i >= 1; i--) {
            mplew.writeInt(mask[i - 1]);
        }
    }

    public static <E extends IBuffStat> void writeBuffMask(MaplePacketLittleEndianWriter mplew, Collection<Pair<E, Integer>> statups) {
        int[] mask = new int[GameConstants.MAX_BUFFSTAT];
        for (Pair<E, Integer> statup : statups) {
            mask[statup.left.getPosition() - 1] |= statup.left.getValue();
        }
        for (int i = mask.length; i >= 1; i--) {
            mplew.writeInt(mask[i - 1]);
        }
    }

    public static <E extends IBuffStat> void writeBuffMask(MaplePacketLittleEndianWriter mplew, Map<E, Integer> statups) {
        int[] mask = new int[GameConstants.MAX_BUFFSTAT];
        for (E statup : statups.keySet()) {
            mask[statup.getPosition() - 1] |= statup.getValue();
        }
        for (int i = mask.length; i >= 1; i--) {
            mplew.writeInt(mask[i - 1]);
        }
    }


    public static void GW_ItemSlotBase_Decode(MaplePacketLittleEndianWriter mplew, Item item) {
        GW_ItemSlotBase_Decode(mplew, item, null);
    }

    public static void GW_ItemSlotBase_Decode(final MaplePacketLittleEndianWriter mplew, final Item item, final MapleCharacter chr) {
        mplew.write(item.getPet() != null ? 3 : item.getType());
        GW_ItemSlotBase_RawDecode(mplew, item, chr);
        if (item.getPet() != null) { // Pet
            GW_ItemSlotPet_RawDecode(mplew, item, item.getPet(), true);
        } else if (item.getType() == 1) {
            GW_ItemSlotEquip_RawDecode(mplew, item, chr);
        } else {
            GW_ItemSlotBundle_RawDecode(mplew, item, chr);
        }
    }

    public static void GW_ItemSlotBase_RawDecode(final MaplePacketLittleEndianWriter mplew, final Item item, final MapleCharacter chr) {
        mplew.writeInt(item.getItemId());
        boolean hasUniqueId = item.getUniqueId() > 0 && !GameConstants.isMarriageRing(item.getItemId()) && item.getItemId() / 10000 != 166;
        boolean isPet = item.getPet() != null && item.getPet().getUniqueId() > -1;
        boolean isRing = false;
        mplew.write(hasUniqueId ? 1 : 0);
        if (hasUniqueId) {
            mplew.writeLong(item.getUniqueId());
        }
        addExpirationTime(mplew, item.getExpiration());
        mplew.writeInt(chr == null ? -1 : chr.getExtendedSlots().indexOf(item.getItemId()));
    }

    public static void GW_ItemSlotEquip_RawDecode(final MaplePacketLittleEndianWriter mplew, final Item item, final MapleCharacter chr) {
        boolean hasUniqueId = item.getUniqueId() > 0 && !GameConstants.isMarriageRing(item.getItemId()) && item.getItemId() / 10000 != 166;
        Equip equip = (Equip) item;
        mplew.write(equip.getUpgradeSlots());
        mplew.write(equip.getLevel());
        mplew.writeShort(equip.getStr());
        mplew.writeShort(equip.getDex());
        mplew.writeShort(equip.getInt());
        mplew.writeShort(equip.getLuk());
        mplew.writeShort(equip.getHp());
        mplew.writeShort(equip.getMp());
        mplew.writeShort(equip.getWatk());
        mplew.writeShort(equip.getMatk());
        mplew.writeShort(equip.getWdef());
        mplew.writeShort(equip.getMdef());
        mplew.writeShort(equip.getAcc());
        mplew.writeShort(equip.getAvoid());
        mplew.writeShort(equip.getHands());
        mplew.writeShort(equip.getSpeed());
        mplew.writeShort(equip.getJump());
        mplew.writeMapleAsciiString(equip.getOwner());

        mplew.writeInt(equip.getFlag());
        mplew.write(0); //getIncSkill
        mplew.write(Math.max(equip.getBaseLevel(), equip.getEquipLevel())); // Item level
        //------------------計算方法?? TODO
        mplew.writeInt(equip.getExpPercentage() * 100000); // Item Exp... 98% = 25%
        mplew.writeInt(equip.getDurability());
        mplew.writeInt(equip.getViciousHammer());

        mplew.writeShort(equip.getPVPDamage());
        mplew.write(equip.getState());
        mplew.write(equip.getEnhance());

        mplew.writeShort(equip.getPotential1());
        mplew.writeShort(equip.getPotential2());
        mplew.writeShort(equip.getPotential3());


        // 不知道是啥
        mplew.writeShort(equip.getSocket1() % 10000);
        mplew.writeShort(equip.getSocket2() % 10000);
        mplew.writeShort(equip.getSocket3() % 10000);

        if (!hasUniqueId) {
            mplew.writeLong(item.getUniqueId()); //some tracking ID
        }
        mplew.writeLong(getTime(-2));
        mplew.writeInt(-1);
    }

    public static void GW_ItemSlotBundle_RawDecode(final MaplePacketLittleEndianWriter mplew, final Item item, final MapleCharacter chr) {
        mplew.writeShort(item.getQuantity());
        mplew.writeMapleAsciiString(item.getOwner());
        mplew.writeShort(item.getFlag());
        if (GameConstants.isRechargable(item.getItemId()) || GameConstants.isExpChair(item.getItemId())) {
            mplew.writeLong(/*(int)*/(item.getInventoryId() <= 0 ? -1 : item.getInventoryId()));
        }
    }

    public static void GW_ItemSlotPet_RawDecode(MaplePacketLittleEndianWriter mplew, Item item, MaplePet pet, boolean active) {
        mplew.writeAsciiString(pet.getName(), 13);
        mplew.write(pet.getLevel());
        mplew.writeShort(pet.getCloseness());
        mplew.write(pet.getFullness());
        if (item == null) {
            mplew.writeLong(PacketHelper.getKoreanTimestamp((long) (System.currentTimeMillis() * 1.5)));
        } else {
            addExpirationTime(mplew, item.getExpiration() <= System.currentTimeMillis() ? -1L : item.getExpiration());
        }
        mplew.writeShort(0);
        mplew.writeShort(pet.getFlags());
        mplew.writeInt((pet.getPetItemId() == 5000054) && (pet.getSecondsLeft() > 0) ? pet.getSecondsLeft() : 0);
        mplew.writeShort(0);//pet.isCanPickup() ? 0 : 2);
        mplew.write(pet.getSummoned() ? pet.getSummonedValue() : 0); // 位置
        mplew.writeInt(/*pet.getBuffSkill()*/0); // 裝備的BUFF技能?????
    }


}
