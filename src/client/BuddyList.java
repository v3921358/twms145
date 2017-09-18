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
package client;

import database.DatabaseConnection;
import tools.packet.CWvsContext.BuddylistPacket;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class BuddyList implements Serializable {

    private static final long serialVersionUID = 1413738569L;
    private Map<Integer, BuddyListEntry> buddies = new LinkedHashMap<>();
    private int capacity;
    private boolean changed = false;

    public BuddyList(int capacity) {
        this.capacity = capacity;
    }

    public boolean contains(int characterId) {
        return buddies.containsKey(characterId);
    }

    public boolean containsVisible(int characterId) {
        BuddyListEntry ble = buddies.get(characterId);
        if (ble == null) {
            return false;
        }
        return ble.isVisible();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public BuddyListEntry get(int characterId) {
        return buddies.get(characterId);
    }

    public BuddyListEntry get(String characterName) {
        String lowerCaseName = characterName.toLowerCase();
        for (BuddyListEntry ble : buddies.values()) {
            if (ble.getName().toLowerCase().equals(lowerCaseName)) {
                return ble;
            }
        }
        return null;
    }

    public void put(BuddyListEntry entry) {
        buddies.put(entry.getCharacterId(), entry);
        changed = true;
    }

    public void remove(int characterId) {
        buddies.remove(characterId);
        changed = true;
    }

    public Collection<BuddyListEntry> getBuddies() {
        return buddies.values();
    }

    public boolean isFull() {
        return buddies.size() >= capacity;
    }

    public int[] getBuddyIds() {
        int buddyIds[] = new int[buddies.size()];
        int i = 0;
        for (BuddyListEntry ble : buddies.values()) {
            if (ble.isVisible()) {
                buddyIds[i++] = ble.getCharacterId();
            }
        }
        return buddyIds;
    }

    public void loadFromTransfer(final Map<CharacterNameAndId, Boolean> data) {
        CharacterNameAndId buddyid;
        for (final Map.Entry<CharacterNameAndId, Boolean> qs : data.entrySet()) {
            buddyid = qs.getKey();
            put(new BuddyListEntry(buddyid.getName(), buddyid.getId(), buddyid.getGroup(), -1, qs.getValue()));
        }
    }

    public void loadFromDb(int characterId) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        try (PreparedStatement ps = con.prepareStatement("SELECT b.buddyid, b.pending, c.name as buddyname, b.groupname FROM buddies as b, characters as c WHERE c.id = b.buddyid AND b.characterid = ?")) {
            ps.setInt(1, characterId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                put(new BuddyListEntry(rs.getString("buddyname"), rs.getInt("buddyid"), rs.getString("groupname"), -1, rs.getInt("pending") != 1));
            }
            rs.close();
        }
    }

    public void addBuddyRequest(MapleClient c, int cidFrom, String nameFrom, int channelFrom, int levelFrom, int jobFrom) {
        put(new BuddyListEntry(nameFrom, cidFrom, "ETC", channelFrom, false));
        c.sendPacket(BuddylistPacket.requestBuddylistAdd(cidFrom, nameFrom, levelFrom, jobFrom));
    }

    public void setChanged(boolean v) {
        this.changed = v;
    }

    public boolean changed() {
        return changed;
    }

    public enum BuddyOperation {
        ADDED, DELETED
    }

    public enum BuddyAddResult {

        BUDDYLIST_FULL, ALREADY_ON_LIST, OK
    }
}
